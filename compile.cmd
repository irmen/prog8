set PROG8CLASSPATH=./out/production/compiler;./out/production/parser
set KOTLINPATH=%USERPROFILE%/.IdeaIC2018.3/config/plugins/Kotlin
set LIBJARS=%KOTLINPATH%/lib/kotlin-stdlib.jar;%KOTLINPATH%/lib/kotlin-reflect.jar;./parser/antlr/lib/antlr-runtime-4.7.2.jar

java -cp %PROG8CLASSPATH%;%LIBJARS% prog8.CompilerMainKt %*
