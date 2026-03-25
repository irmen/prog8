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
