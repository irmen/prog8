set PROG8_LIBDIR=../prog8lib
set PROG8CLASSPATH=out/production/compiler/
set KOTLINPATH=%USERPROFILE%/.IdeaIC2018.3/config/plugins/Kotlin
set LIBJARS=%KOTLINPATH%/lib/kotlin-stdlib.jar;%KOTLINPATH%/lib/kotlin-reflect.jar;antlr/lib/antlr-runtime-4.7.1.jar

java -Dprog8.libdir=%PROG8_LIBDIR% -cp %PROG8CLASSPATH%;%LIBJARS% prog8.CompilerMainKt %*
