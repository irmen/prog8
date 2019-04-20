#!/usr/bin/env sh

PROG8CLASSPATH=./compiler/build/classes/kotlin/main:./compiler/build/resources/main:./parser/build/classes/java/main
KOTLINPATH=${HOME}/.IntelliJIdea2019.1/config/plugins/Kotlin
LIBJARS=${KOTLINPATH}/lib/kotlin-stdlib.jar:${KOTLINPATH}/lib/kotlin-reflect.jar:./parser/antlr/lib/antlr-runtime-4.7.2.jar

java -cp ${PROG8CLASSPATH}:${LIBJARS} prog8.CompilerMainKt $*
