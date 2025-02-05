package com.github.denofevil.importCost

import com.intellij.lang.ecmascript6.psi.ES6ImportCall
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.ecmascript6.psi.impl.ES6ImportPsiUtil
import com.intellij.lang.ecmascript6.resolve.JSFileReferencesUtil
import com.intellij.lang.javascript.JSStringUtil
import com.intellij.lang.javascript.frameworks.commonjs.CommonJSUtil
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSRecursiveWalkingElementVisitor
import com.intellij.lang.javascript.service.JSLanguageServiceBase
import com.intellij.lang.javascript.service.JSLanguageServiceDefaultCacheData
import com.intellij.lang.javascript.service.JSLanguageServiceQueue
import com.intellij.lang.javascript.service.JSLanguageServiceQueueImpl
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceObject
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceSimpleCommand
import com.intellij.openapi.application.EDT
import com.intellij.openapi.application.readAction
import com.intellij.openapi.components.Service
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.util.Key
import com.intellij.openapi.util.registry.Registry
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.EmptyConsumer
import com.intellij.util.concurrency.annotations.RequiresReadLock
import com.intellij.xml.util.HtmlUtil
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import java.util.concurrent.ConcurrentHashMap
import kotlin.time.Duration.Companion.seconds

@Service(Service.Level.PROJECT)
@OptIn(FlowPreview::class)
class ImportCostLanguageService(project: Project, cs: CoroutineScope) : JSLanguageServiceBase(project) {
    companion object {
        private val KEY = Key<DocumentListener>("import-cost-listener")
    }

    private val psiManager: PsiManager = PsiManager.getInstance(project)
    private val psiDocumentManager: PsiDocumentManager = PsiDocumentManager.getInstance(project)

    private val failedSize = Sizes(0L, 0L)
    private val evalRequests = MutableSharedFlow<EvalDocumentRequest>(extraBufferCapacity = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)
    private val updateEditorRequests = MutableSharedFlow<Unit>(replay = 1, onBufferOverflow = BufferOverflow.DROP_OLDEST)

    data class Sizes(val size: Long, val gzip: Long)

    private val cache = ConcurrentHashMap<String, MutableMap<Int, Sizes>>()

    init {
        cs.launch {
            evalRequests
                .distinctUntilChanged { prev, current ->
                    prev.document == current.document && prev.modificationStamp == current.modificationStamp
                }
                .collect {
                    delay(1.seconds)
                    val requests = readAction {
                        buildRequests(it.document, it.file)
                    }
                    for (request in requests) {
                        // TODO replace with suspend function usage
                        sendCommand(request) { _, answer ->
                            val response = Sizes(
                                answer.element["package"].asJsonObject["size"].asLong,
                                answer.element["package"].asJsonObject["gzip"].asLong
                            )
                            it.map[request.line] = response
                            updateEditorRequests.tryEmit(Unit)
                        }
                    }
                }
        }
        cs.launch {
            updateEditorRequests.debounce(1.seconds).collectLatest {
                withContext(Dispatchers.EDT) {
                    val editor = FileEditorManager.getInstance(myProject).selectedTextEditor
                    if (editor != null) {
                        editor.contentComponent.revalidate()
                        editor.contentComponent.repaint()
                    }
                }
            }
        }
    }

    override fun needInitToolWindow() = false

    override fun createLanguageServiceQueue(): JSLanguageServiceQueue {
        val protocol = ServiceProtocol(myProject, EmptyConsumer.getInstance<Any>())

        val service = JSLanguageServiceQueueImpl(
            myProject, protocol, myProcessConnector, myDefaultReporter,
            JSLanguageServiceDefaultCacheData()
        )

        if (Registry.`is`("js.language.service.log.messages")) {
            protocol.startMessageStreamLogging("import-cost")
            Disposer.register(service) {
                protocol.stopMessageStreamLogging()
            }
        }

        return service
    }

    fun getImportSize(file: VirtualFile, line: Int): Sizes {
        if (!file.isInLocalFileSystem || !file.isValid) return failedSize

        val psiFile = psiManager.findFile(file)
        if (psiFile == null || !(HtmlUtil.hasHtml(psiFile) || HtmlUtil.supportsXmlTypedHandlers(psiFile))) {
            return failedSize
        }
        val document = psiDocumentManager.getDocument(psiFile) ?: return failedSize
        EditorFactory.getInstance().getEditors(document, myProject).forEach { editor ->
            if (KEY.get(editor) == null) {
                val listener = createListener(file)
                document.addDocumentListener(listener, (editor as EditorImpl).disposable)
                KEY.set(editor, listener)
            }
        }

        var map: MutableMap<Int, Sizes>? = cache[file.path]
        if (map == null) {
            map = createNewMap()
            cache.putIfAbsent(file.path, map)
            updateImports(document, file, map)
        }
        return map.getOrDefault(line, failedSize)
    }

    private fun createNewMap() = ConcurrentHashMap<Int, Sizes>()

    private fun createListener(file: VirtualFile): DocumentListener {
        return object : DocumentListener {
            override fun documentChanged(event: DocumentEvent) {
                val document = event.document

                val map: MutableMap<Int, Sizes>?
                if (event.oldFragment.contains('\n') || event.newFragment.contains('\n')) {
                    map = createNewMap()
                    cache[file.path] = map
                } else {
                    map = cache[file.path] ?: return
                    val line = document.getLineNumber(event.offset)
                    map.remove(line)
                }

                updateImports(document, file, map)
            }
        }
    }

    private fun updateImports(document: Document, file: VirtualFile, map: MutableMap<Int, Sizes>) {
        evalRequests.tryEmit(EvalDocumentRequest(document, file, map))
    }

    @RequiresReadLock
    private fun buildRequests(document: Document, file: VirtualFile): List<EvaluateImportRequest> {
        val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document)
        val result = mutableListOf<EvaluateImportRequest>()
        psiFile?.accept(object : JSRecursiveWalkingElementVisitor() {
            override fun visitJSCallExpression(node: JSCallExpression) {
                if (node.isRequireCall) {
                    val path = CommonJSUtil.getModulePathIfRequireCall(node)
                    if (!path.isNullOrEmpty() && !JSFileReferencesUtil.isRelative(path)) {
                        val endOffset = node.textRange.endOffset
                        // handle uncommitted doc
                        if (document.textLength > endOffset) {
                            val line = document.getLineNumber(endOffset)
                            result.add(EvaluateImportRequest(file.path, path, line, "require('$path')"))
                        }
                    }
                }
                super.visitJSCallExpression(node)
            }

            override fun visitES6ImportCall(node: ES6ImportCall) {
                val path = node.referenceText?.let { JSStringUtil.unquoteAndUnescapeString(it) }
                if (!path.isNullOrEmpty() && !JSFileReferencesUtil.isRelative(path)) {
                    val endOffset = node.textRange.endOffset
                    // handle uncommitted doc
                    if (document.textLength > endOffset) {
                        val line = document.getLineNumber(endOffset)
                        result.add(EvaluateImportRequest(file.path, path, line, "import('$path')"))
                    }
                }

                super.visitES6ImportCall(node)
            }

            override fun visitES6ImportDeclaration(node: ES6ImportDeclaration) {
                val path = ES6ImportPsiUtil.getUnquotedFromClauseOrModuleText(node)
                if (!path.isNullOrEmpty() && !JSFileReferencesUtil.isRelative(path)) {
                    val endOffset = node.textRange.endOffset
                    // handle uncommitted doc
                    if (document.textLength > endOffset) {
                        val line = document.getLineNumber(endOffset)
                        result.add(EvaluateImportRequest(file.path, path, line, compileImportString(node, path)))
                    }
                }
            }
        })
        return result.toList()
    }

    private fun compileImportString(node: ES6ImportDeclaration, path: String): String {
        val importString = if (node.importSpecifiers.isNotEmpty() || node.importedBindings.isNotEmpty()) {
            val importSpecifiers = node.importSpecifiers.mapNotNull { it.declaredName }.joinToString(",")
            val importedBindings =
                node.importedBindings.joinToString(",") { if (it.isNamespaceImport) "* as ${it.lastChild.text}" else it.lastChild.text }
            when {
                importSpecifiers.isEmpty() -> importedBindings
                importedBindings.isEmpty() -> "{$importSpecifiers}"
                else -> "$importedBindings,{$importSpecifiers}"
            }
        } else {
            "* as tmp"
        }
        return "import $importString from '$path'; console.log(${importString.replace("* as ", "")});"
    }

    class EvaluateImportRequest(
        @Suppress("unused") val fileName: String,
        val name: String,
        val line: Int,
        val string: String
    ) : JSLanguageServiceSimpleCommand, JSLanguageServiceObject {
        override fun toSerializableObject() = this

        override val command: String = "import-cost"
    }

    class EvalDocumentRequest(val document: Document, val file: VirtualFile, val map: MutableMap<Int, Sizes>) {
        val modificationStamp = document.modificationStamp
    }
}

