@file:Suppress("JUnitMixedFramework")

package com.github.denofevil.importCost

import com.intellij.testFramework.fixtures.BasePlatformTestCase
import org.junit.Test

class ImportCostStyleTest : BasePlatformTestCase() {

    @Test
    fun testServiceInitialization() {
        val service = project.getService(ImportCostLanguageService::class.java)
        assertNotNull("ImportCostLanguageService should be available", service)

        val settings = project.getService(ImportCostSettings::class.java)
        assertNotNull("ImportCostSettings should be available", settings)

        assertTrue("Code Vision should be enabled by default", settings.isCodeVision())
        assertEquals("Default text template should be correct", "\$size (gzip \$gsize)", settings.getTextTemplate())
        assertEquals("Default error limit should be 100KB", 100, settings.getErrorLimit())
        assertEquals("Default warning limit should be 50KB", 50, settings.getWarningLimit())
    }

    @Test
    fun testSizeThresholds() {
        val settings = project.getService(ImportCostSettings::class.java)

        val infoSize = 30 * 1024L
        val infoKind = calculateSizeInfo(project, infoSize)
        assertEquals("30KB should be INFO level", ImportCostSizeKind.INFO, infoKind)

        val warningSize = 75 * 1024L
        val warningKind = calculateSizeInfo(project, warningSize)
        assertEquals("75KB should be WARNING level", ImportCostSizeKind.WARNING, warningKind)

        val errorSize = 150 * 1024L
        val errorKind = calculateSizeInfo(project, errorSize)
        assertEquals("150KB should be ERROR level", ImportCostSizeKind.ERROR, errorKind)

        settings.setWarningLimit(30)
        settings.setErrorLimit(60)

        val customWarning = calculateSizeInfo(project, 40 * 1024L)
        assertEquals("40KB should be WARNING with custom threshold", ImportCostSizeKind.WARNING, customWarning)

        val customError = calculateSizeInfo(project, 70 * 1024L)
        assertEquals("70KB should be ERROR with custom threshold", ImportCostSizeKind.ERROR, customError)

        settings.setWarningLimit(50)
        settings.setErrorLimit(100)
    }

    @Test
    fun testTextTemplateFormatting() {
        val settings = project.getService(ImportCostSettings::class.java)

        val testSize = ImportCostLanguageService.Sizes(size = 102400, gzip = 51200)

        val defaultTemplate = settings.getTextTemplate()

        val defaultText = convertToText(project, testSize)

        assertTrue("Default template should contain size info",
            defaultText.isNotEmpty() && (defaultText.contains("kB") || defaultText.contains("102")))
        assertTrue("Default template should contain 'gzip'", defaultText.contains("gzip"))

        settings.setTextTemplate("Size: \$size")
        val customText = convertToText(project, testSize)

        assertTrue("Custom template should contain 'Size:'", customText.contains("Size:"))
        assertFalse("Custom template should not contain 'gzip'",
            customText.lowercase().contains("gzip"))

        settings.setTextTemplate(defaultTemplate)
    }
}
