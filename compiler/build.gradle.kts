plugins {
    id("application")
    kotlin("jvm")
    // id("com.github.johnrengelman.shadow") version "8.1.1"
    // id("io.github.goooler.shadow") version "8.1.8"
    id("com.gradleup.shadow") version "9.2.2"
    id("com.peterabeles.gversion") version "1.10.3"
}

dependencies {
    implementation(project(":codeCore"))
    implementation(project(":simpleAst"))
    implementation(project(":codeOptimizers"))
    implementation(project(":compilerAst"))
    implementation(project(":codeGenCpu6502"))
    implementation(project(":codeGenIntermediate"))
    implementation(project(":codeGenExperimental"))
    implementation(project(":virtualmachine"))
    // implementation(project(":beanshell"))
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    // implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.1.0")

    testImplementation(project(":codeCore"))
    testImplementation(project(":intermediate"))
    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.9.1")
    testImplementation("io.kotest:kotest-framework-datatest:5.9.1")
}

configurations.all {
    exclude(group = "com.ibm.icu", module = "icu4j")
    exclude(group = "org.antlr", module = "antlr4")
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
    test {
        java {
            srcDir("${project.projectDir}/test")
        }
    }
}

tasks.startScripts {
    enabled = true
}

application {
    mainClass.set("prog8.CompilerMainKt")
    applicationName = "prog8c"
}

tasks.shadowJar {
    archiveBaseName.set("prog8c")
    archiveVersion.set(version.toString())
    // minimize()
}

tasks.test {
    // Enable JUnit 5 (Gradle 4.6+).
    useJUnitPlatform()

    // Show test results.
    testLogging {
        events("skipped", "failed")
    }
}

gversion {
    srcDir = "src/" // path is relative to the sub-project by default
    classPackage = "prog8.buildversion"
    className = "Version"
    language = "kotlin"
    debug = false
    annotate = true
}

tasks.build {
    finalizedBy(tasks.installDist, tasks.installShadowDist)
}

tasks.compileKotlin {
    dependsOn(tasks.createVersionFile) // , failDirtyNotSnapshot
}

tasks.compileJava {
    dependsOn(tasks.createVersionFile)
}
