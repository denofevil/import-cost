package com.github.denofevil.importCost

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
import com.intellij.openapi.editor.impl.EditorImpl
import com.intellij.openapi.fileEditor.FileEditorManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Key
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiFile
import com.intellij.psi.PsiManager
import com.intellij.util.Consumer
import com.intellij.util.SingleAlarm
import com.intellij.util.containers.ContainerUtil
import com.intellij.util.ui.update.MergingUpdateQueue
import com.intellij.util.ui.update.Update
import com.intellij.xml.util.HtmlUtil
import java.util.*
import java.util.concurrent.CompletableFuture
import javax.swing.event.DocumentListener

class LanguageService(project: Project, private val psiManager: PsiManager, private val psiDocumentManager: PsiDocumentManager) : JSLanguageServiceBase(project) {
    companion object {
        private val KEY = Key<DocumentListener>("import-cost-listener")
    }
    private val failedSize = Pair(0L, 0L)
    private val evalQueue = MergingUpdateQueue("import-cost-eval", 300, true, null, this, null, false).setRestartTimerOnAdd(true)
    private val alarm = SingleAlarm(Runnable {
        val editor = FileEditorManager.getInstance(myProject).selectedTextEditor
        if (editor != null) {
            editor.contentComponent.revalidate()
            editor.contentComponent.repaint()
        }
    }, 100, this)
    private val cache = ContainerUtil.newConcurrentMap<String, MutableMap<Int, Pair<Long, Long>>>()

    override fun needInitToolWindow() = false

    override fun createLanguageServiceQueue(): JSLanguageServiceQueue {
        val protocol = ServiceProtocol(myProject, Consumer.EMPTY_CONSUMER)

        return JSLanguageServiceQueueImpl(myProject, protocol, myProcessConnector, myDefaultReporter,
                JSLanguageServiceDefaultCacheData())
    }

    fun getImportSize(file: VirtualFile, line: Int): Pair<Long, Long> {
        val psiFile = psiManager.findFile(file)
        if (psiFile == null || !(HtmlUtil.hasHtml(psiFile) || HtmlUtil.supportsXmlTypedHandlers(psiFile))) return failedSize
        val document = psiDocumentManager.getDocument(psiFile) ?: return failedSize
        EditorFactory.getInstance().getEditors(document, myProject).forEach { editor ->
            if (KEY.get(editor) == null) {
                val listener = object : com.intellij.openapi.editor.event.DocumentListener {
                    override fun documentChanged(event: DocumentEvent) {
                        val map = cache[file.path]!!
                        val start = document.getLineNumber(event.offset)
                        val end = document.getLineNumber(event.offset + Math.max(event.oldLength, event.newLength))
                        for (i in start..end) {
                            map.remove(i)
                        }
                        updateImports(document, file, psiFile, map)
                    }
                }
                document.addDocumentListener(listener, (editor as EditorImpl).disposable)
            }
        }

        if (!cache.containsKey(file.path)) {
            val map = ContainerUtil.newConcurrentMap<Int, Pair<Long, Long>>()
            cache.putIfAbsent(file.path, map)
            updateImports(document, file, psiFile, map)
        }
        return cache[file.path]!!.getOrDefault(line, failedSize)
    }

    private fun updateImports(document: Document, file: VirtualFile, psiFile: PsiFile, map: MutableMap<Int, Pair<Long, Long>>) {
        evalQueue.queue(object : Update(document) {
            override fun run() {
                processImports(document, file, psiFile, map)
            }

            override fun canEat(update: Update?): Boolean {
                return Arrays.equals(update?.equalityObjects, equalityObjects)
            }
        })
    }

    private fun processImports(document: Document, file: VirtualFile, psiFile: PsiFile, map: MutableMap<Int, Pair<Long, Long>>) {
        ApplicationManager.getApplication().runReadAction {
            psiFile.accept(object : JSRecursiveWalkingElementVisitor() {
                override fun visitJSCallExpression(node: JSCallExpression) {
                    if (node.isRequireCall) {
                        val line = document.getLineNumber(node.textRange.endOffset)
                        val path = CommonJSUtil.getRequireCallModulePath(node)
                        if (path != null) {
                            runRequest(file, path, line, node, map)
                        }
                    }
                }
            })
        }
    }

    private fun runRequest(file: VirtualFile, path: String, line: Int, node: JSCallExpression, map: MutableMap<Int, Pair<Long, Long>>) {
        val request = EvaluateImportRequest(file.path, path, line, node.text)
        CompletableFuture.supplyAsync({
            sendCommand(request) { _, answer ->
                Pair(answer.element["package"].asJsonObject["size"].asLong,
                        answer.element["package"].asJsonObject["gzip"].asLong)
            }!!.get()
        }).thenApply { response ->
            map.put(line, response)
            alarm.cancelAndRequest()
        }
    }

    @Suppress("unused")
    class EvaluateImportRequest(val fileName: String,
                                val name: String,
                                val line: Int,
                                val string: String) : JSLanguageServiceSimpleCommand, JSLanguageServiceObject {
        override fun toSerializableObject() = this

        override fun getCommand() = "import-cost"
    }
}

