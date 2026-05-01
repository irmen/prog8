import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.dsl.JvmTarget


plugins {
    kotlin("jvm") version "2.3.20"
}

allprojects {
    apply(plugin="kotlin")

    repositories {
        mavenLocal()
        mavenCentral()
        maven { url = uri("https://jitpack.io") }
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs = listOf()
            jvmTarget = JvmTarget.JVM_17
            jvmDefault = JvmDefaultMode.NO_COMPATIBILITY
            // languageVersion.set(KotlinVersion.KOTLIN_2_3)
        }
        sourceSets.all {
            languageSettings {
                // enable language features like so:
                // enableLanguageFeature(LanguageFeature.WhenGuards.name)
            }
        }
    }

    java {
        targetCompatibility = JavaVersion.VERSION_17
        sourceCompatibility = JavaVersion.VERSION_17
    }
}

// ============================================================================
// Common Configuration for All Subprojects
// ============================================================================
// This avoids duplication in each module's build.gradle.kts
// ============================================================================

subprojects {
    // Common dependency exclusions for all subprojects
    // Note: antlr4 exclusion is done per-module since parser module needs it
    configurations.all {
        exclude(group = "com.ibm.icu", module = "icu4j")
    }

    // Common test dependencies via Kotest BOM (Bill of Materials)
    // This manages all Kotest module versions centrally
    dependencies {
        testImplementation(platform("io.kotest:kotest-bom:5.9.1"))
        // implementation("com.github.irmen:ksim65:v2.0")
    }

    // Common sourceSets configuration
    sourceSets {
        main {
            java.srcDir("${project.projectDir}/src")
            resources.srcDir("${project.projectDir}/res")
        }
        test {
            java.srcDir("${project.projectDir}/test")
        }
    }

    // Common test configuration
    tasks.withType<Test>().configureEach {
        // Enable JUnit 5 (required for Kotest)
        useJUnitPlatform()

        // Enable concurrent test execution for Kotest
        // Set parallelism to number of CPU cores
        jvmArgs("-Dkotest.framework.parallelism=${Runtime.getRuntime().availableProcessors()}")

        // Disable Kotest autoscan warning
        jvmArgs("-Dkotest.framework.classpath.scanning.autoscan.disable=true")

        // Enable Gradle's parallel test execution (runs multiple test classes concurrently)
        // Use 50% of available processors to avoid over-subscription
        maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

        // Allow filtering tests via -PtestFilter="*TestName*" from command line
        // Example: gradle test -PtestFilter="*TestLookup*"
        val testFilter = project.findProperty("testFilter")?.toString()
        if(testFilter != null) {
            filter {
                includeTestsMatching(testFilter)
            }
        }

        // Show test results - only show failures to reduce noise
        testLogging {
            events("failed")
            // Show full exception details for failures
            exceptionFormat = org.gradle.api.tasks.testing.logging.TestExceptionFormat.FULL
            showExceptions = true
            showCauses = true
            showStackTraces = true
        }
    }
}
