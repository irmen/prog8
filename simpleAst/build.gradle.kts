plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":codeCore"))
    testImplementation(testFixtures(project(":codeCore")))
    testImplementation("io.kotest:kotest-framework-datatest")
    testImplementation("io.kotest:kotest-runner-junit5")
}
