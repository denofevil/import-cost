package com.github.denofevil.importCost

import com.intellij.openapi.options.ConfigurableBuilder
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout

class ImportCostConfigurable(project: Project) : ConfigurableBuilder(), SearchableConfigurable {
    init {
        val settings = project.getService(ImportCostSettings::class.java)
        checkBox("Use code vision", settings::isCodeVision) { settings.setCodeVision(it) }

        val jbTextField = JBTextField("", 30)
        val labeledComponent = LabeledComponent.create(jbTextField, "Presentation:", BorderLayout.WEST)

        component(labeledComponent, {
            settings.getTextTemplate()
        }, {
            settings.setTextTemplate(it)
        }, {
            jbTextField.text
        }, {
            jbTextField.text = it
        })
    }

    override fun getDisplayName(): String = "Import Cost"

    override fun getId(): String = "ImportCost"
}