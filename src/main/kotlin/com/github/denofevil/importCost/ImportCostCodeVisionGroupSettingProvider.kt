package com.github.denofevil.importCost

import com.intellij.codeInsight.codeVision.settings.CodeVisionGroupSettingProvider

class ImportCostCodeVisionGroupSettingProvider : CodeVisionGroupSettingProvider {
    override val groupId: String get() = "Import Cost"
    override val description: String get() = "Import Cost"
    override val groupName: String get() = "Import Cost"
}