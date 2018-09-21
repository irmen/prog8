#!/usr/bin/env sh

PROG8CLASSPATH=out/production/compiler
KOTLINPATH=${HOME}/.IntelliJIdea2018.2/config/plugins/Kotlin
LIBJARS=${KOTLINPATH}/lib/kotlin-stdlib.jar:${KOTLINPATH}/lib/kotlin-reflect.jar:antlr/lib/antlr-runtime-4.7.1.jar

java  -cp ${PROG8CLASSPATH}:${LIBJARS} prog8.StackVmMainKt $*
