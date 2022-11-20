package com.github.denofevil.importCost

import com.intellij.openapi.editor.EditorLinePainter
import com.intellij.openapi.editor.LineExtensionInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile

class ImportCostLinePainter : EditorLinePainter() {
    override fun getLineExtensions(project: Project, file: VirtualFile, line: Int): Collection<LineExtensionInfo> {
        val settings = project.getService(ImportCostSettings::class.java)
        if (settings.isCodeVision()) return emptyList()

        val service = project.getService(ImportCostLanguageService::class.java)
        val importSize: ImportCostLanguageService.Sizes = service.getImportSize(file, line)
        if (importSize.size <= 0) return emptyList()

        val info = calculateSizeInfo(project, importSize.size)
        val textAttributes = getHandlerAttributes(info)
        return arrayListOf(LineExtensionInfo("  " + convertToText(project, importSize), textAttributes))
    }
}
