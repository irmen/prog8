set PROG8_COMPILER_DIR=compiler

set PROG8_LIBDIR=%PROG8_COMPILER_DIR%/prog8lib
set PROG8CLASSPATH=%PROG8_COMPILER_DIR%/out/production/compiler/
set KOTLINPATH=%USERPROFILE%/.IdeaIC2018.3/config/plugins/Kotlin
set LIBJARS=%KOTLINPATH%/lib/kotlin-stdlib.jar;%KOTLINPATH%/lib/kotlin-reflect.jar;%PROG8_COMPILER_DIR%/antlr/lib/antlr-runtime-4.7.2.jar

java -Dprog8.libdir=%PROG8_LIBDIR% -cp %PROG8CLASSPATH%;%LIBJARS% prog8.CompilerMainKt %*
