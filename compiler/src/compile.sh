#!/usr/bin/env bash
mkdir -p compiled_java
javac  -verbose -d compiled_java -cp ../antlr/lib/antlr-runtime-4.7.1.jar $(find . -name \*.java)
jar cf parser.jar -C compiled_java prog8

KOTLINC="bash ${HOME}/.IntelliJIdea2018.2/config/plugins/Kotlin/kotlinc/bin/kotlinc"

${KOTLINC} -verbose -include-runtime -d prog8_kotlin.jar -cp ../antlr/lib/antlr-runtime-4.7.1.jar:parser.jar prog8
