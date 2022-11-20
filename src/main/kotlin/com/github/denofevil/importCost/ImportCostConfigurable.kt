package com.github.denofevil.importCost

import com.intellij.openapi.options.ConfigurableBuilder
import com.intellij.openapi.options.SearchableConfigurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.LabeledComponent
import com.intellij.openapi.util.text.StringUtil
import com.intellij.ui.components.JBTextField
import java.awt.BorderLayout

class ImportCostConfigurable(project: Project) : ConfigurableBuilder(), SearchableConfigurable {
    init {
        val settings = project.getService(ImportCostSettings::class.java)
        checkBox("Use code vision", settings::isCodeVision) { settings.setCodeVision(it) }

        val jbTextField = JBTextField("", 30)
        val labeledComponent = LabeledComponent.create(jbTextField, "Presentation", BorderLayout.WEST)
        component(labeledComponent,
            { settings.getTextTemplate() },
            { settings.setTextTemplate(it) },
            { jbTextField.text },
            { jbTextField.text = it }
        )


        val jbWarningField = JBTextField("", 30)
        val warningComponent = LabeledComponent.create(jbWarningField, "Warning limit (kb)", BorderLayout.WEST)
        component(warningComponent,
            { settings.getWarningLimit().toString() },
            { settings.setWarningLimit(StringUtil.parseInt(it, 50)) },
            { jbWarningField.text },
            { jbWarningField.text = it }
        )

        labeledComponent.anchor = warningComponent.label

        val jbErrorField = JBTextField("", 30)
        val errorComponent = LabeledComponent.create(jbErrorField, "Error limit (kb)", BorderLayout.WEST)
        component(errorComponent,
            { settings.getErrorLimit().toString() },
            { settings.setErrorLimit(StringUtil.parseInt(it, 100)) },
            { jbErrorField.text },
            { jbErrorField.text = it }
        )

        errorComponent.anchor = warningComponent.label
    }

    override fun getDisplayName(): String = "Import Cost"

    override fun getId(): String = "ImportCost"
}