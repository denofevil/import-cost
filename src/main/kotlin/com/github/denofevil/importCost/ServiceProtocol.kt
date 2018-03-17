package com.github.denofevil.importCost

import com.intellij.javascript.nodejs.interpreter.NodeJsInterpreterManager
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreter
import com.intellij.javascript.nodejs.interpreter.local.NodeJsLocalInterpreterManager
import com.intellij.lang.javascript.service.JSLanguageServiceUtil
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceInitialState
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceNodeStdProtocolBase
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer

class ServiceProtocol(private val project: Project, readyConsumer: Consumer<*>) : JSLanguageServiceNodeStdProtocolBase(project,
        readyConsumer) {
    override fun getNodeInterpreter(): String? {
        val node = NodeJsInterpreterManager.getInstance(project).interpreter ?: return null
        var localInterpreter = NodeJsLocalInterpreter.tryCast(node)
        if (localInterpreter == null || !localInterpreter.isValid) {
            localInterpreter = NodeJsLocalInterpreterManager.getInstance().detectMostRelevant()
        }
        return localInterpreter?.interpreterSystemDependentPath
    }

    override fun createState(): JSLanguageServiceInitialState {
        val result = JSLanguageServiceInitialState()
        result.pluginName = "import-cost"
        val file = JSLanguageServiceUtil.getPluginDirectory(this.javaClass, "lib/index.js")
        result.pluginPath = file.absolutePath
        return result
    }

    override fun dispose() {}
}