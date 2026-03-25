plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":codeCore"))
    implementation(project(":simpleAst"))
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    // implementation "org.jetbrains.kotlin:kotlin-reflect"
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.3.1")

    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.9.1")
    testImplementation("io.kotest:kotest-framework-datatest:5.9.1")
}

sourceSets {
    main {
        java {
            setSrcDirs(listOf("$projectDir/src"))
        }
        resources {
            setSrcDirs(listOf("$projectDir/res"))
        }
    }
    test {
        java {
            setSrcDirs(listOf("$projectDir/test"))
        }
    }
}

tasks.test {
    // Enable JUnit 5 (Gradle 4.6+).
    useJUnitPlatform()

    // Enable concurrent test execution for Kotest 6.x
    // Set parallelism to number of CPU cores minus 1
    jvmArgs("-Dkotest.framework.parallelism=${Runtime.getRuntime().availableProcessors()}")
    
    // Enable Gradle's parallel test execution (runs multiple test classes concurrently)
    maxParallelForks = (Runtime.getRuntime().availableProcessors() / 2).coerceAtLeast(1)

    // Always run tests, even when nothing changed.
    dependsOn("cleanTest")

    // Show test results.
    testLogging {
        events("skipped", "failed")
    }
}
