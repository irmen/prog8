import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":codeCore"))
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.0.1")
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

// note: there are no unit tests yet in this module!
