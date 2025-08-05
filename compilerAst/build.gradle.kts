plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":codeCore"))
    // implementation("org.jetbrains.kotlin:kotlin-stdlib-jdk8")
    implementation("org.antlr:antlr4-runtime:4.13.2")
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.1.0")
    implementation(project(":parser"))
}

configurations.all {
    exclude(group = "com.ibm.icu", module = "icu4j")
}

sourceSets {
    main {
        java {
            srcDir("${project.projectDir}/src")
        }
    }
}
