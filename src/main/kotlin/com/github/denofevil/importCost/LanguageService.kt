package com.github.denofevil.importCost

import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.ecmascript6.psi.impl.ES6ImportPsiUtil
import com.intellij.lang.javascript.frameworks.commonjs.CommonJSUtil
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSRecursiveWalkingElementVisitor
import com.intellij.lang.javascript.service.JSLanguageServiceBase
import com.intellij.lang.javascript.service.JSLanguageServiceDefaultCacheData
import com.intellij.lang.javascript.service.JSLanguageServiceQueue
import com.intellij.lang.javascript.service.JSLanguageServiceQueueImpl
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceObject
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceSimpleCommand
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.editor.Document
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.event.DocumentEvent
import com.intellij.openapi.editor.event.DocumentListener
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiManager
import com.intellij.util.EmptyConsumer
import com.intellij.util.SingleAlarm
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.xml.util.HtmlUtil
import java.util.*
import java.util.concurrent.ConcurrentHashMap

class LanguageService(project: Project) : JSLanguageServiceBase(project) {
    companion object {
        private val KEY = Key<DocumentListener>("import-cost-listener")
    }

    private val psiManager: PsiManager = PsiManager.getInstance(project)
    private val psiDocumentManager: PsiDocumentManager = PsiDocumentManager.getInstance(project)

    private val failedSize = Sizes(0L, 0L)
    private val evalQueue =
        MergingUpdateQueue("import-cost-eval", 300, true, null, this, null, false).setRestartTimerOnAdd(true)
    private val alarm = SingleAlarm({
        val editor = FileEditorManager.getInstance(myProject).selectedTextEditor
        if (editor != null) {
            editor.contentComponent.revalidate()
            editor.contentComponent.repaint()
        }
    }, 100, this)

    data class Sizes(val size: Long, val gzip: Long)

    private val cache = ConcurrentHashMap<String, MutableMap<Int, Sizes>>()

    override fun needInitToolWindow() = false

    override fun createLanguageServiceQueue(): JSLanguageServiceQueue {
        val protocol = ServiceProtocol(myProject, EmptyConsumer.getInstance<Any>())

        return JSLanguageServiceQueueImpl(
            myProject, protocol, myProcessConnector, myDefaultReporter,
            JSLanguageServiceDefaultCacheData()
        )
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
        evalQueue.queue(object : Update(document) {
            override fun run() {
                processImports(document, file, map)
            }

            override fun canEat(update: Update?): Boolean {
                return Arrays.equals(update?.equalityObjects, equalityObjects)
            }
        })
    }

    private fun processImports(document: Document, file: VirtualFile, map: MutableMap<Int, Sizes>) {
        ApplicationManager.getApplication().runReadAction {
            val psiFile = PsiDocumentManager.getInstance(myProject).getPsiFile(document)
            psiFile?.accept(object : JSRecursiveWalkingElementVisitor() {
                override fun visitJSCallExpression(node: JSCallExpression) {
                    if (node.isRequireCall) {
                        val path = CommonJSUtil.getModulePathIfRequireCall(node)
                        if (path != null) {
                            val endOffset = node.textRange.endOffset
                            //handle uncommitted doc
                            if (document.textLength > endOffset) {
                                val line = document.getLineNumber(endOffset)
                                runRequest(file, path, line, "require('$path')", map)
                            }
                        }
                    }
                }

                override fun visitES6ImportDeclaration(node: ES6ImportDeclaration) {
                    val path = ES6ImportPsiUtil.getUnquotedFromClauseOrModuleText(node)
                    if (path != null) {
                        val endOffset = node.textRange.endOffset
                        //handle uncommitted doc
                        if (document.textLength > endOffset) {
                            val line = document.getLineNumber(endOffset)
                            runRequest(file, path, line, compileImportString(node, path), map)
                        }
                    }
                }
            })
        }
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

    private fun runRequest(file: VirtualFile, path: String, line: Int, string: String, map: MutableMap<Int, Sizes>) {
        val request = EvaluateImportRequest(file.path, path, line, string)
        sendCommand(request) { _, answer ->
            val response = Sizes(
                answer.element["package"].asJsonObject["size"].asLong,
                answer.element["package"].asJsonObject["gzip"].asLong
            )
            map[line] = response
            alarm.cancelAndRequest()
        }
    }

    class EvaluateImportRequest(
        @Suppress("unused") val fileName: String,
        val name: String,
        val line: Int,
        val string: String
    ) : JSLanguageServiceSimpleCommand, JSLanguageServiceObject {
        override fun toSerializableObject() = this

        override fun getCommand() = "import-cost"
    }
}

