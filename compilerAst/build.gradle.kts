plugins {
    kotlin("jvm")
}

dependencies {
    implementation(project(":codeCore"))
    implementation("org.antlr:antlr4-runtime:4.13.2")
    implementation("com.michael-bull.kotlin-result:kotlin-result-jvm:2.3.1")
    implementation(project(":parser"))
}

// Exclude transitive antlr4 dependency (we only need it in parser module)
configurations.all {
    exclude(group = "org.antlr", module = "antlr4")
}
