@echo off

set PROG8CLASSPATH=./out/production/compiler_main
set KOTLINPATH=%USERPROFILE%/.IdeaIC2019.1/config/plugins/Kotlin
set LIBJARS=%KOTLINPATH%/lib/kotlin-stdlib.jar;%KOTLINPATH%/lib/kotlin-reflect.jar

java -cp %PROG8CLASSPATH%;%LIBJARS% prog8.StackVmMainKt %*
