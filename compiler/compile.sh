#!/usr/bin/env sh

PROG8_LIBDIR=../prog8lib
PROG8CLASSPATH=out/production/compiler
KOTLINPATH=${HOME}/.IntelliJIdea2018.3/config/plugins/Kotlin
LIBJARS=${KOTLINPATH}/lib/kotlin-stdlib.jar:${KOTLINPATH}/lib/kotlin-reflect.jar:antlr/lib/antlr-runtime-4.7.1.jar

java -Dprog8.libdir=${PROG8_LIBDIR} -cp ${PROG8CLASSPATH}:${LIBJARS} prog8.CompilerMainKt $*
