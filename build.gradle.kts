import org.jetbrains.kotlin.gradle.dsl.JvmTarget
import org.jetbrains.kotlin.gradle.dsl.JvmDefaultMode
import org.jetbrains.kotlin.gradle.internal.config.LanguageFeature
import org.jetbrains.kotlin.gradle.dsl.KotlinVersion


plugins {
    kotlin("jvm") version "2.2.21"
}

allprojects {
    apply(plugin="kotlin")

    repositories {
        mavenLocal()
        mavenCentral()
    }

    kotlin {
        compilerOptions {
            freeCompilerArgs = listOf("-Xwhen-guards")
            jvmTarget = JvmTarget.JVM_11
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
        targetCompatibility = JavaVersion.VERSION_11
        sourceCompatibility = JavaVersion.VERSION_11
    }
}
