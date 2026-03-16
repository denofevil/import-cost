import org.jetbrains.intellij.platform.gradle.TestFrameworkType
import org.jetbrains.intellij.platform.gradle.tasks.PrepareSandboxTask

plugins {
    id("java") // Java support
    id("org.jetbrains.kotlin.jvm") version "2.3.20"
    id("org.jetbrains.intellij.platform") version "2.13.1"
}

version = "1.4.261"
group = "ImportCost"

// Set the JVM language level used to build the project.
kotlin {
    jvmToolchain(21)
}


// Configure project's dependencies
repositories {
    mavenCentral()
    intellijPlatform {
        defaultRepositories()
        localPlatformArtifacts()
        intellijDependencies()
    }
}

dependencies {
    testImplementation("junit:junit:4.13.2")
    testImplementation("org.opentest4j:opentest4j:1.3.0")


    intellijPlatform {
        webstorm("261.22158.36")
        bundledPlugins(listOf("JavaScript"))
        testFramework(TestFrameworkType.Platform)
    }
}


intellijPlatform {
    pluginConfiguration {
        name = "Import Cost"
        version = "1.4.261"
        ideaVersion {
            sinceBuild = "261"
            untilBuild = "261.*"
        }
    }
}

// js packing
afterEvaluate {
    tasks.named<PrepareSandboxTask>("prepareSandbox") {
        doLast {
            val libraries = "${destinationDir.path}/ImportCost/lib/"
            copy {
                from("$projectDir/javascript")
                into(libraries)
                logger.info("Copying javascript files to $libraries")
            }
        }
    }
}

//runIde {
//    jvmArgs = listOf("-Didea.javascript.language.service.debug.options.importcost=--inspect=9229")
//}
