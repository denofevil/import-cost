package com.github.denofevil.importCost

import com.intellij.openapi.options.BoundSearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.DialogPanel
import com.intellij.ui.dsl.builder.bindIntText
import com.intellij.ui.dsl.builder.bindSelected
import com.intellij.ui.dsl.builder.bindText
import com.intellij.ui.dsl.builder.panel

class ImportCostConfigurable(private val project: Project) : BoundSearchableConfigurable("Import Cost", "ImportCost") {
    override fun createPanel(): DialogPanel {
        val settings = project.getService(ImportCostSettings::class.java)
        return panel {
            row {
                checkBox("Use code vision")
                    .bindSelected(settings::isCodeVision, settings::setCodeVision)
            }

            row("Presentation") {
                textField()
                    .bindText(settings::getTextTemplate, settings::setTextTemplate)
            }

            row("Warning limit (kB)") {
                intTextField()
                    .bindIntText(settings::getWarningLimit, settings::setWarningLimit)
            }

            row("Error limit (kB)") {
                intTextField()
                    .bindIntText(settings::getErrorLimit, settings::setErrorLimit)
            }
        }
    }
}