plugins {
    kotlin("jvm")
    id("application")
}

val debugPort = 8000
val debugArgs = "-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n,quiet=y"

val serverMainClassName = "prog8lsp.MainKt"
val applicationName = "prog8-language-server"

application {
    mainClass.set(serverMainClassName)
    description = "Code completions, diagnostics and more for Prog8"
    // applicationDefaultJvmArgs = listOf("-DkotlinLanguageServer.version=$version")
    applicationDistribution.into("bin") {
        filePermissions {
            user {
                read=true
                execute=true
                write=true
            }
            other.execute = true
            group.execute = true
        }
    }
}

repositories {
    mavenCentral()
}

dependencies {
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j:0.24.0")
    implementation("org.eclipse.lsp4j:org.eclipse.lsp4j.jsonrpc:0.24.0")
    
    // For JSON processing if needed
    //implementation("com.google.code.gson:gson:2.10.1")
    
    // For more advanced text processing
    //implementation("org.apache.commons:commons-lang3:3.12.0")

    implementation(project(":compiler"))

}

configurations.forEach { config ->
    config.resolutionStrategy {
        preferProjectModules()
    }
}

sourceSets.main {
    java.srcDir("src")
    resources.srcDir("resources")
}

tasks.startScripts {
    applicationName = "prog8-language-server"
}

tasks.register<Exec>("fixFilePermissions") {
    // When running on macOS or Linux the start script
    // needs executable permissions to run.

    onlyIf { !System.getProperty("os.name").lowercase().contains("windows") }
    commandLine("chmod", "+x", "${tasks.installDist.get().destinationDir}/bin/prog8-language-server")
}

tasks.register<JavaExec>("debugRun") {
    mainClass.set(serverMainClassName)
    classpath(sourceSets.main.get().runtimeClasspath)
    standardInput = System.`in`

    jvmArgs(debugArgs)
    doLast {
        println("Using debug port $debugPort")
    }
}

tasks.register<CreateStartScripts>("debugStartScripts") {
    applicationName = "prog8-language-server"
    mainClass.set(serverMainClassName)
    outputDir = tasks.installDist.get().destinationDir.toPath().resolve("bin").toFile()
    classpath = tasks.startScripts.get().classpath
    defaultJvmOpts = listOf(debugArgs)
}

tasks.register<Sync>("installDebugDist") {
    dependsOn("installDist")
    finalizedBy("debugStartScripts")
}

tasks.withType<Test> {
    // Disable tests for now since we don't have any
    enabled = false
}

tasks.installDist {
    finalizedBy("fixFilePermissions")
}

tasks.build {
    finalizedBy("installDist")
}
