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
    implementation("com.github.ajalt.clikt:clikt:5.1.0")
    implementation("com.github.ajalt.mordant:mordant:3.0.2")
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.3.1")

    testImplementation(project(":codeCore"))
    testImplementation(testFixtures(project(":codeCore")))
    testImplementation(project(":intermediate"))
    testImplementation("io.kotest:kotest-runner-junit5")
    testImplementation("org.codeberg.irmen:ksim65:v2.2")
}

// Exclude transitive antlr4 dependency (we only need it in parser module)
configurations.all {
    exclude(group = "org.antlr", module = "antlr4")
}

// Post-process the generated launcher scripts: pass --enable-native-access only to
// JVMs that understand it (JDK 18+), to avoid JNA restricted-method warnings on newer JDKs.
fun patchLaunchScripts(scriptDir: File) {
    val unixScript = scriptDir.resolve("prog8c")
    if (unixScript.canRead()) {
        val marker = "eval \"set -- $("
        val text = unixScript.readText()
        check(marker in text) { "unexpected POSIX launch script layout for prog8c" }
        val probe = $$"""if "$JAVACMD" --enable-native-access=ALL-UNNAMED -version >/dev/null 2>&1; then
    JAVA_OPTS="$JAVA_OPTS --enable-native-access=ALL-UNNAMED"
fi

"""
        unixScript.writeText(text.replace(marker, probe + marker))
    }
    val windowsScript = scriptDir.resolve("prog8c.bat")
    if (windowsScript.canRead()) {
        val marker = "@rem Execute prog8c"
        val text = windowsScript.readText()
        check(marker in text) { "unexpected Windows launch script layout for prog8c" }
        val probe = $$"""@rem Prog8: add --enable-native-access on JVMs that support it (JDK 18+) to avoid JNA restricted-method warnings
"%JAVA_EXE%" --enable-native-access=ALL-UNNAMED -version >NUL 2>&1
if not errorlevel 1 set JAVA_OPTS=%JAVA_OPTS% --enable-native-access=ALL-UNNAMED

"""
        windowsScript.writeText(text.replace(marker, probe + marker))
    }
}

tasks.startScripts {
    enabled = true
}

tasks.withType<org.gradle.jvm.application.tasks.CreateStartScripts>().configureEach {
    doLast {
        patchLaunchScripts(outputDir!!)
    }
}

application {
    mainClass.set("prog8.CompilerMainKt")
    applicationName = "prog8c"
}

tasks.jar {
    manifest {
        attributes("Main-Class" to "prog8.CompilerMainKt")
        attributes("Enable-Native-Access" to "ALL-UNNAMED")
    }
}

tasks.shadowJar {
    archiveBaseName.set("prog8c")
    archiveVersion.set(version.toString())
    duplicatesStrategy = DuplicatesStrategy.INCLUDE
    mergeServiceFiles()
    manifest {
        attributes("Main-Class" to "prog8.CompilerMainKt")
        attributes("Enable-Native-Access" to "ALL-UNNAMED")
    }
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
