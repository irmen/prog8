plugins {
    kotlin("jvm")
}

dependencies {
    // should have no dependencies to other modules
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.3.1")
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
}

// note: there are no unit tests in this module!
