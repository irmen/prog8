plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":codeCore"))
    implementation(project(":compilerAst"))
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.3.1")
}
