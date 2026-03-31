plugins {
    id("application")
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.3.2"
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
    implementation(project(":intermediate"))
    implementation(project(":virtualmachine"))
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.3.1")

    testImplementation(project(":codeCore"))
    testImplementation(testFixtures(project(":codeCore")))
    testImplementation(project(":intermediate"))
    testImplementation("io.kotest:kotest-runner-junit5")
    testImplementation("io.kotest:kotest-framework-datatest")
}

// Exclude transitive antlr4 dependency (we only need it in parser module)
configurations.all {
    exclude(group = "org.antlr", module = "antlr4")
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
