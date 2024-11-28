import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

java {
    targetCompatibility = JavaVersion.VERSION_11
    sourceCompatibility = JavaVersion.VERSION_11
}

kotlin {
    compilerOptions.jvmTarget = JvmTarget.JVM_11
}


dependencies {
    implementation(project(":codeCore"))
    implementation(project(":compilerAst"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.0.0")
    // implementation "org.jetbrains.kotlin:kotlin-reflect"
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