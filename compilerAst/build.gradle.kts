import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":codeCore"))
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.antlr:antlr4-runtime:4.13.2")
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.0.0")
    implementation(project(":parser"))
}

configurations.all {
    exclude(group = "com.ibm.icu", module = "icu4j")
}

sourceSets {
    main {
        java {
            srcDir("${project.projectDir}/src")
        }
    }
}