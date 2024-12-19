import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":codeCore"))
    implementation(project(":intermediate"))
    implementation(project(":codeGenIntermediate"))
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    // implementation "org.jetbrains.kotlin:kotlin-reflect"
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.0.0")
}

sourceSets {
    main {
        java {
            srcDir(file("${project.projectDir}/src"))
        }
        resources {
            srcDir(file("${project.projectDir}/res"))
        }
    }
}

// note: there are no unit tests in this module!