#!/usr/bin/env sh

PROG8CLASSPATH=out/production/compiler
LIBJARS=/opt/irmen/idea-2018/plugins/Kotlin/lib/kotlin-stdlib.jar:/opt/irmen/idea-2018/plugins/Kotlin/lib/kotlin-reflect.jar:antlr/lib/antlr-runtime-4.7.1.jar

java  -cp ${PROG8CLASSPATH}:${LIBJARS} prog8.stackvm.StackVmKt $*
