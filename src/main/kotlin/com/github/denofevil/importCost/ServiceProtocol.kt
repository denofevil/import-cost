package com.github.denofevil.importCost

import com.intellij.lang.javascript.service.JSLanguageServiceUtil
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceInitialState
import com.intellij.lang.javascript.service.protocol.JSLanguageServiceNodeStdProtocolBase
import com.intellij.lang.javascript.service.protocol.LocalFilePath
import com.intellij.openapi.project.Project
import com.intellij.util.Consumer

class ServiceProtocol(project: Project, readyConsumer: Consumer<*>) :
    JSLanguageServiceNodeStdProtocolBase("importcost", project, readyConsumer) {

    override fun needReadActionToCreateState(): Boolean = false

    override fun createState(): JSLanguageServiceInitialState {
        val result = JSLanguageServiceInitialState()
        result.pluginName = "import-cost"
        val file = JSLanguageServiceUtil.getPluginDirectory(this.javaClass, "lib/index.js")
            ?: throw IllegalStateException("import-cost plugin not found")
        result.pluginPath = LocalFilePath.create(file.absolutePath)
        return result
    }

    override fun dispose() {}
}