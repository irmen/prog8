plugins {
    id("application")
    kotlin("jvm")
    id("com.gradleup.shadow") version "9.6.1"
    id("com.peterabeles.gversion") version "1.11.0"
}

dependencies {
    implementation(project(":codeCore"))
    implementation(project(":simpleAst"))
    implementation(project(":codeOptimizers"))
    implementation(project(":compilerAst"))
    implementation(project(":codeGenCpu6502"))
    implementation(project(":codeGenNew6502"))
    implementation(project(":codeGenM68k"))
    implementation(project(":codeGenIntermediate"))
    implementation(project(":intermediate"))
    implementation(project(":virtualmachine"))
    implementation("com.github.ajalt.clikt:clikt:5.0.3")
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.3.1")

    testImplementation(project(":codeCore"))
    testImplementation(testFixtures(project(":codeCore")))
    testImplementation(project(":intermediate"))
    testImplementation("io.kotest:kotest-runner-junit5")
    testImplementation("com.github.irmen:ksim65:v2.1")
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
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    filesMatching("META-INF/LICENSE") {
        duplicatesStrategy = DuplicatesStrategy.EXCLUDE
    }
    // minimize()
}

gversion {
    srcDir = "src/" // path is relative to the sub-project by default
    classPackage = "prog8.buildversion"
    className = "Version"
    language = "kotlin"
    debug = false
    annotate = ""
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
