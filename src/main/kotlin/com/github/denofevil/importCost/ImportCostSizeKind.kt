package com.github.denofevil.importCost

import com.intellij.openapi.editor.DefaultLanguageHighlighterColors
import com.intellij.openapi.editor.colors.CodeInsightColors
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.markup.EffectType
import com.intellij.openapi.editor.markup.TextAttributes
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.text.StringUtil
import java.awt.Color
import java.awt.Font

enum class ImportCostSizeKind {
    INFO, WARNING, ERROR
}

fun calculateSizeInfo(project: Project, size: Long): ImportCostSizeKind {
    val settings = project.getService(ImportCostSettings::class.java)
    return when {
        size > settings.getErrorLimit() * 1024 -> ImportCostSizeKind.ERROR
        size > settings.getWarningLimit() * 1024 -> ImportCostSizeKind.WARNING
        else -> ImportCostSizeKind.INFO
    }
}

fun convertToText(project: Project, size: ImportCostLanguageService.Sizes): String {
    val settings = project.getService(ImportCostSettings::class.java)
    val textTemplate = settings.getTextTemplate()

    return textTemplate
        .replace("\$size", StringUtil.formatFileSize(size.size))
        .replace("\$gsize", StringUtil.formatFileSize(size.gzip))
}

fun getHandlerAttributes(info: ImportCostSizeKind, isItalic: Boolean = true) =
    TextAttributes(getTextColor(info), null, null, EffectType.BOXED, if (isItalic) Font.ITALIC else Font.PLAIN)

private fun getTextColor(info: ImportCostSizeKind): Color? {
    val globalScheme = EditorColorsManager.getInstance().globalScheme

    return when (info) {
        ImportCostSizeKind.ERROR -> globalScheme.getAttributes(CodeInsightColors.ERRORS_ATTRIBUTES)?.effectColor
        ImportCostSizeKind.WARNING -> globalScheme.getAttributes(CodeInsightColors.WARNINGS_ATTRIBUTES)?.errorStripeColor
        else -> globalScheme.getAttributes(DefaultLanguageHighlighterColors.LINE_COMMENT)?.foregroundColor
    }
}
