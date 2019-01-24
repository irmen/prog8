set PROG8CLASSPATH=./out/production/compiler
set KOTLINPATH=%USERPROFILE%/.IdeaIC2018.3/config/plugins/Kotlin
set LIBJARS=%KOTLINPATH%/lib/kotlin-stdlib.jar;%KOTLINPATH%/lib/kotlin-reflect.jar

java -cp %PROG8CLASSPATH%;%LIBJARS% prog8.StackVmMainKt %*
