mkdir compiled_java

java -jar ../antlr/lib/antlr-4.7.1-complete.jar -o ./prog8/parser -Xexact-output-dir -no-listener -no-visitor -package prog8.parser ../antlr/prog8.g4

@dir /b /S src *.java > sources.txt
javac  -verbose -d compiled_java -cp ../antlr/lib/antlr-runtime-4.7.1.jar @sources.txt
@del sources.txt
jar cf parser.jar -C compiled_java prog8

set KOTLINC=%USERPROFILE%\.IdeaIC2018.3\config\plugins\kotlin\kotlinc\bin\kotlinc.bat

%KOTLINC% -verbose -include-runtime -d prog8_kotlin.jar -cp ../antlr/lib/antlr-runtime-4.7.1.jar;parser.jar prog8
