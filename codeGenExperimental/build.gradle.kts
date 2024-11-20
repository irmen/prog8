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
    implementation(project(":codeCore"))
    implementation(project(":intermediate"))
    implementation(project(":codeGenIntermediate"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
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