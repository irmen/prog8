plugins {
    kotlin("jvm")
    id("application")
    id("com.peterabeles.gversion") version "1.10.3"
}

dependencies {
    implementation(project(":codeCore"))
    implementation(project(":simpleAst"))
    implementation(project(":intermediate"))
    implementation(project(":codeGenIntermediate"))
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.3.1")

    testImplementation(testFixtures(project(":codeCore")))
    testImplementation("io.kotest:kotest-runner-junit5")
}


val debugPort = 8000
val debugArgs = "-agentlib:jdwp=transport=dt_socket,server=y,address=8000,suspend=n,quiet=y"

val serverMainClassName = "prog8.codegen.m68k.MainKt"
val applicationName = "prog8-m68kgen"

application {
    mainClass.set(serverMainClassName)
    description = "New codegen based on IR files"
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
    implementation(project(":codeCore"))
    implementation(project(":intermediate"))
    implementation("org.jetbrains.kotlinx:kotlinx-cli:0.3.6")

    // Test dependencies - Kotest BOM is provided by root build.gradle.kts
    testImplementation("io.kotest:kotest-runner-junit5")
}

configurations.forEach { config ->
    config.resolutionStrategy {
        preferProjectModules()
    }
}

sourceSets {
    main {
        java.srcDir("src")
        resources.srcDir("resources")
    }
}

tasks.startScripts {
    applicationName = "prog8-m68kgen"
}

tasks.register<Exec>("fixFilePermissions") {
    // When running on macOS or Linux the start script
    // needs executable permissions to run.

    onlyIf { !System.getProperty("os.name").lowercase().contains("windows") }
    commandLine("chmod", "+x", "${tasks.installDist.get().destinationDir}/bin/prog8-m68kgen")
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
    applicationName = "prog8-m68kgen"
    mainClass.set(serverMainClassName)
    outputDir = tasks.installDist.get().destinationDir.toPath().resolve("bin").toFile()
    classpath = tasks.startScripts.get().classpath
    defaultJvmOpts = listOf(debugArgs)
}

tasks.register<Sync>("installDebugDist") {
    dependsOn("installDist")
    finalizedBy("debugStartScripts")
}

tasks.installDist {
    finalizedBy("fixFilePermissions")
}

tasks.build {
    finalizedBy("installDist")
}
