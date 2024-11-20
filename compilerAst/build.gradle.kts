import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("java")
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
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
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