import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    kotlin("jvm")
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
    implementation(project(":intermediate"))
    implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    // implementation "org.jetbrains.kotlin:kotlin-reflect"
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.0.0")

    testImplementation("io.kotest:kotest-runner-junit5-jvm:5.9.1")
    testImplementation("org.junit.jupiter:junit-jupiter:5.9.2")
    testImplementation("io.kotest:kotest-framework-datatest:5.9.1")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
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