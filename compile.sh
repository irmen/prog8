#!/usr/bin/env sh

PROG8_COMPILER_DIR=compiler
PROG8_LIBDIR=${PROG8_COMPILER_DIR}/prog8lib
PROG8CLASSPATH=${PROG8_COMPILER_DIR}/out/production/compiler
KOTLINPATH=${HOME}/.IntelliJIdea2018.3/config/plugins/Kotlin
LIBJARS=${KOTLINPATH}/lib/kotlin-stdlib.jar:${KOTLINPATH}/lib/kotlin-reflect.jar:${PROG8_COMPILER_DIR}/antlr/lib/antlr-runtime-4.7.2.jar

java -Dprog8.libdir=${PROG8_LIBDIR} -cp ${PROG8CLASSPATH}:${LIBJARS} prog8.CompilerMainKt $*
