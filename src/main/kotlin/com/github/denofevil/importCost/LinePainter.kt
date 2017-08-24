package com.github.denofevil.importCost

import com.intellij.openapi.components.ServiceManager
import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.EditorLinePainter
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import com.intellij.openapi.vfs.VirtualFile
import java.awt.Color
import java.awt.Font

class LinePainter : EditorLinePainter() {
    override fun getLineExtensions(project: Project, file: VirtualFile, line: Int): MutableCollection<LineExtensionInfo> {
        val service = ServiceManager.getService(project, LanguageService::class.java)
        val importSize = service.getImportSize(file, line)
        if (importSize.first == 0L) return arrayListOf()
        val textAttributes = TextAttributes(getTextColor(importSize.first), null, null, EffectType.BOXED, Font.ITALIC)
        return arrayListOf(LineExtensionInfo("  ${StringUtil.formatFileSize(importSize.first)}", textAttributes),
                           LineExtensionInfo(" (gzip: ${StringUtil.formatFileSize(importSize.second)})", textAttributes))
    }

    private fun getTextColor(size: Long): Color? {
        return when {
            size > 100 * 1024 -> EditorColorsManager.getInstance().globalScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)?.effectColor
            size > 50 * 1024 -> EditorColorsManager.getInstance().globalScheme.getAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES)?.errorStripeColor
            else -> EditorColorsManager.getInstance().globalScheme.getAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT)?.foregroundColor
        }
    }
}
