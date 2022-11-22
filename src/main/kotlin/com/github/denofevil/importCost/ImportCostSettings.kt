package com.github.denofevil.importCost

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage

@State(name = "ImportCostSettings", storages = [Storage("import-cost.xml")])
class ImportCostSettings : PersistentStateComponent<InnerState> {
    private var state: InnerState = InnerState()

    override fun getState(): InnerState = state

    override fun loadState(state: InnerState) {
        this.state = state
    }

    fun isCodeVision(): Boolean = state.isCodeVision

    fun getTextTemplate(): String = state.textTemplate

    fun getErrorLimit(): Int = state.errorLimit
    fun getWarningLimit(): Int = state.warningLimit

    fun setErrorLimit(limit: Int) {
        state = state.copy().also { it.errorLimit = limit }
    }

    fun setWarningLimit(limit: Int) {
        state = state.copy().also { it.warningLimit = limit }
    }

    fun setTextTemplate(text: String) {
        state = state.copy().also {
            it.textTemplate = text
        }
    }

    fun setCodeVision(newValue: Boolean) {
        state = state.copy().also {
            it.isCodeVision = newValue
        }
    }
}

class InnerState {
    var isCodeVision = true
    var textTemplate = "\$size (gzip \$gsize)"
    var errorLimit = 100
    var warningLimit = 50

    fun copy(): InnerState {
        val copy = InnerState()
        copy.isCodeVision = isCodeVision
        copy.textTemplate = textTemplate
        copy.warningLimit = warningLimit
        copy.errorLimit = errorLimit
        return copy
    }
}