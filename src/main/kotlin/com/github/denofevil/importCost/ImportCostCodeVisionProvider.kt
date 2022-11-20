package com.github.denofevil.importCost

import com.intellij.codeInsight.codeVision.CodeVisionEntry
import com.intellij.codeInsight.codeVision.CodeVisionRelativeOrdering
import com.intellij.codeInsight.codeVision.ui.model.ClickableRichTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.ClickableTextCodeVisionEntry
import com.intellij.codeInsight.codeVision.ui.model.richText.RichText
import com.intellij.codeInsight.hints.codeVision.CodeVisionProviderBase
import com.intellij.lang.ecmascript6.psi.ES6ImportCall
import com.intellij.lang.ecmascript6.psi.ES6ImportDeclaration
import com.intellij.lang.html.HtmlCompatibleFile
import com.intellij.lang.javascript.psi.JSCallExpression
import com.intellij.lang.javascript.psi.JSFile
import com.intellij.lang.javascript.psi.JSVarStatement
import com.intellij.openapi.editor.Editor
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.openapi.util.TextRange
import com.intellij.psi.PsiDocumentManager
import com.intellij.psi.PsiElement
import com.intellij.psi.PsiFile
import com.intellij.ui.SimpleTextAttributes
import java.awt.event.MouseEvent

class ImportCostCodeVisionProvider : CodeVisionProviderBase() {
    override val id: String get() = "Import Cost"
    override val name: String get() = "Import Cost"
    override val relativeOrderings: List<CodeVisionRelativeOrdering> get() = emptyList()

    override fun acceptsElement(element: PsiElement): Boolean {
        return element is ES6ImportDeclaration ||
                isRequireCall(element) ||
                isRequireVarStatement(element) ||
                element is ES6ImportCall
    }

    private fun isRequireCall(element: PsiElement?) = element is JSCallExpression && element.isRequireCall

    private fun isRequireVarStatement(element: PsiElement): Boolean {
        return element is JSVarStatement &&
                element.variables.size == 1 && isRequireCall(element.variables[0].initializer)
    }

    override fun acceptsFile(file: PsiFile): Boolean {
        val project = file.project
        val settings = project.getService(ImportCostSettings::class.java)
        if (!settings.isCodeVision()) return false

        @Suppress("UnstableApiUsage") //we update plugins for every version, so it isn't big deal
        return file is JSFile || file is HtmlCompatibleFile
    }

    override fun getHint(element: PsiElement, file: PsiFile): String? {
        val project = element.project
        val vFile = file.virtualFile ?: return null
        val service = project.getService(ImportCostLanguageService::class.java)
        val document = PsiDocumentManager.getInstance(project).getDocument(file) ?: return null
        val lineNumber = document.getLineNumber(element.textRange.endOffset)
        val importSize = service.getImportSize(vFile, lineNumber)
        if (importSize.size <= 0) return null
        val info = calculateSizeInfo(project, importSize.size)
        return info.name + "#" + convertToText(project, importSize)
    }

    override fun computeForEditor(editor: Editor, file: PsiFile): List<Pair<TextRange, CodeVisionEntry>> {
        val fromParent = super.computeForEditor(editor, file)
        if (fromParent.isEmpty()) return fromParent

        return fromParent.map {
            val range = it.first
            val entry = it.second
            if (entry is ClickableTextCodeVisionEntry) {
                val text = entry.text
                val parts = text.split("#")
                if (parts.size != 2) return@map it

                val realText = parts[1]
                val info = ImportCostSizeKind.valueOf(parts[0])
                val richText = RichText()
                val textAttributes = getHandlerAttributes(info, false)
                richText.append(realText, SimpleTextAttributes.fromTextAttributes(textAttributes))

                val newEntry = ClickableRichTextCodeVisionEntry(
                    entry.providerId,
                    richText,
                    entry.onClick,
                    entry.icon,
                    entry.longPresentation,
                    entry.tooltip,
                    entry.extraActions
                )

                return@map range to newEntry
            }

            return@map it
        }
    }

    override fun handleClick(editor: Editor, element: PsiElement, event: MouseEvent?) {
        ShowSettingsUtil.getInstance().showSettingsDialog(element.project, ImportCostConfigurable::class.java)
    }
}