plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":codeCore"))
    implementation(project(":simpleAst"))
    implementation(project(":intermediate"))
    implementation(project(":codeGenIntermediate"))
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.3.1")
}
