import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
    id("application")
    id("org.jetbrains.kotlin.jvm")
}

java {
    targetCompatibility = JavaVersion.VERSION_11
    sourceCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_11
}

dependencies {
    // should have no dependencies to other modules
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.0.0")
}

sourceSets {
    main {
        java {
            srcDir("${project.projectDir}/src")
        }
        resources {
            srcDir("${project.projectDir}/res")
        }
    }
}

// note: there are no unit tests in this module!