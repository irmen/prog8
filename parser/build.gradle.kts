import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    id("antlr")
    id("java")
}

java {
    targetCompatibility = JavaVersion.VERSION_11
    sourceCompatibility = JavaVersion.VERSION_11
}

dependencies {
    antlr("org.antlr:antlr4:4.13.2")
    implementation("org.antlr:antlr4-runtime:4.13.2")
}

configurations.all {
    exclude(group = "com.ibm.icu", module = "icu4j")
}

tasks.generateGrammarSource {
    outputDirectory = file("src/prog8/parser")
    arguments.addAll(listOf("-no-listener", "-no-visitor"))
}

tasks.compileJava {
    dependsOn(tasks.generateGrammarSource)
}

sourceSets {
    main {
        java {
            srcDir("${project.projectDir}/src")
        }
    }
}