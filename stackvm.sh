#!/usr/bin/env sh

PROG8_COMPILER_DIR=compiler
PROG8CLASSPATH=${PROG8_COMPILER_DIR}/out/production/compiler
KOTLINPATH=${HOME}/.IntelliJIdea2018.3/config/plugins/Kotlin
LIBJARS=${KOTLINPATH}/lib/kotlin-stdlib.jar:${KOTLINPATH}/lib/kotlin-reflect.jar:${PROG8_COMPILER_DIR}/antlr/lib/antlr-runtime-4.7.1.jar

java -cp ${PROG8CLASSPATH}:${LIBJARS} prog8.StackVmMainKt $*
