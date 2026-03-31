plugins {
    kotlin("jvm")
    `java-test-fixtures`
}

dependencies {
    // should have no dependencies to other modules
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.3.1")

    testImplementation("io.kotest:kotest-runner-junit5")
    testImplementation("io.kotest:kotest-framework-datatest")
}
