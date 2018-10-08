#!/usr/bin/env bash

find prog8 -name \*.java > javasources.txt
mkdir -p compiled_java
javac  -verbose -d compiled_java -cp ../antlr/lib/antlr-runtime-4.7.1.jar @javasources.txt
rm javasources.txt

KOTLINC="bash ${HOME}/.IntelliJIdea2018.2/config/plugins/Kotlin/kotlinc/bin/kotlinc"
${KOTLINC} -verbose -include-runtime -d prog8_kotlin.jar -cp ../antlr/lib/antlr-runtime-4.7.1.jar:compiled_java prog8

jar uf prog8_kotlin.jar -C compiled_java prog8
