@file:Suppress("JUnitMixedFramework")

package com.github.denofevil.importCost

import com.intellij.ide.plugins.PluginManagerCore
import com.intellij.lang.javascript.service.JSLanguageServiceUtil
import com.intellij.openapi.extensions.PluginId
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.testFramework.IndexingTestUtil
import com.intellij.testFramework.fixtures.BasePlatformTestCase
import com.intellij.testFramework.fixtures.TempDirTestFixture
import com.intellij.testFramework.fixtures.impl.TempDirTestFixtureImpl
import kotlinx.coroutines.delay
import kotlinx.coroutines.runBlocking
import org.junit.FixMethodOrder
import org.junit.Test
import org.junit.runners.MethodSorters
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
class ImportCostIntegrationTest : BasePlatformTestCase() {
    override fun createTempDirTestFixture(): TempDirTestFixture {
        return TempDirTestFixtureImpl()
    }

    override fun getTestDataPath(): String {
        return "src/test/resources/testData"
    }

    override fun setUp() {
        super.setUp()
        myFixture.copyDirectoryToProject("node_modules", "node_modules")
        myFixture.copyFileToProject("package.json", "package.json")
        IndexingTestUtil.waitUntilIndexesAreReady(project)
    }

    @Test
    fun testJSPluginLoaded() {
        val pluginId = PluginId.getId("JavaScript")
        val plugin = PluginManagerCore.getPlugin(pluginId)
        assertNotNull(plugin)
        assertTrue(PluginManagerCore.isLoaded(pluginId))
    }

    @Test
    fun testPluginLibraryExists() {
        val file = JSLanguageServiceUtil.getPluginDirectory(ServiceProtocol::class.java, "lib/index.js")
        assertNotNull("Plugin lib/index.js should exist", file)
        assertTrue("Plugin lib/index.js should be a file", file?.isFile == true)
    }

    @Test
    fun testServiceCanBeCreated() = runBlocking {
        val service = project.getService(ImportCostLanguageService::class.java)
        assertNotNull("ImportCostLanguageService should be available", service)
        assertTrue("Service should be initialized", service.checkCanUseService())
    }

    @Test
    fun testImportSizeCalculation() = runBlocking {
        val service = project.getService(ImportCostLanguageService::class.java)
        assertTrue("Service should be initialized", service.checkCanUseService())

        val psiFile = myFixture.configureByText(
            "test.js",
            """
            import React from 'react';
            import lodash from 'lodash';
            import { debounce } from 'lodash';
            """.trimIndent()
        )
        val virtualFile = requireNotNull(psiFile.virtualFile) { "Virtual file should exist" }
        assertTrue("Virtual file should be from local file system", virtualFile.isInLocalFileSystem)

        val reactSize = waitForImportSize(service, virtualFile, 0, 45.seconds)

        assertTrue("React size should be positive (got ${reactSize.size})", reactSize.size > 0)
        assertTrue("React gzip size should be positive (got ${reactSize.gzip})", reactSize.gzip > 0)
        assertTrue(
            "Gzip size should be smaller than normal size (size=${reactSize.size}, gzip=${reactSize.gzip})",
            reactSize.gzip < reactSize.size
        )
    }

    private suspend fun waitForImportSize(
        service: ImportCostLanguageService,
        file: VirtualFile,
        line: Int,
        timeout: Duration
    ): ImportCostLanguageService.Sizes {
        val timeoutNanos = timeout.inWholeNanoseconds
        val startedAt = System.nanoTime()
        var size = service.getImportSize(file, line)
        while (size.size <= 0 && System.nanoTime() - startedAt < timeoutNanos) {
            delay(250)
            size = service.getImportSize(file, line)
        }
        return size
    }

}
