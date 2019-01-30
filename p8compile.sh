#!/usr/bin/env sh

PROG8CLASSPATH=./out/production/compiler_main:./out/production/parser_main
KOTLINPATH=${HOME}/.IntelliJIdea2018.3/config/plugins/Kotlin
LIBJARS=${KOTLINPATH}/lib/kotlin-stdlib.jar:${KOTLINPATH}/lib/kotlin-reflect.jar:./parser/antlr/lib/antlr-runtime-4.7.2.jar

java -cp ${PROG8CLASSPATH}:${LIBJARS} prog8.CompilerMainKt $*
