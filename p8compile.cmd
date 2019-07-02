@echo off

set PROG8CLASSPATH=./compiler/build/classes/kotlin/main;./compiler/build/resources/main;./parser/build/classes/java/main
set KOTLINPATH=%USERPROFILE%/.IdeaIC2019.1/config/plugins/Kotlin
set LIBJARS=%KOTLINPATH%/lib/kotlin-stdlib.jar;%KOTLINPATH%/lib/kotlin-reflect.jar;./parser/antlr/lib/antlr-runtime-4.7.2.jar

java -cp %PROG8CLASSPATH%;%LIBJARS% prog8.CompilerMainKt %*

@REM   if you have created a .jar file using the 'create_compiler_jar' script, you can simply do:  java -jar prog8compiler.jar
