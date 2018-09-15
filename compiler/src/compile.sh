#!/bin/sh
mkdir -p compiled_java
javac -d compiled_java -cp ../antlr/lib/antlr-runtime-4.7.1.jar $(find . -name \*.java)
jar cf parser.jar -C compiled_java prog8
kotlinc -d prog8_kotlin.jar -include-runtime -cp ../antlr/lib/antlr-runtime-4.7.1.jar:parser.jar prog8
