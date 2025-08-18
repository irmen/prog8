plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":codeCore"))
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.9.1")
    testImplementation("io.kotest:kotest-framework-datatest:5.9.1")
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

tasks.test {
    // Enable JUnit 5 (Gradle 4.6+).
    useJUnitPlatform()

    // Always run tests, even when nothing changed.
    dependsOn("cleanTest")

    // Show test results.
    testLogging {
        events("skipped", "failed")
    }
}