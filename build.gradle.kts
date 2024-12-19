import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm") version "2.1.0"
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
        }
    }

    java {
        targetCompatibility = JavaVersion.VERSION_11
        sourceCompatibility = JavaVersion.VERSION_11
    }
}
