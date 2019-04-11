#!/usr/bin/env bash

echo "Compiling the parser..."
java -jar ./parser/antlr/lib/antlr-4.7.2-complete.jar -o ./parser/src/prog8/parser -Xexact-output-dir -no-listener -no-visitor ./parser/antlr/prog8.g4


PARSER_CLASSES=./out/production/parser
COMPILER_JAR=prog8compiler.jar
ANTLR_RUNTIME=./parser/antlr/lib/antlr-runtime-4.7.2.jar

mkdir -p ${PARSER_CLASSES}
javac  -d ${PARSER_CLASSES} -cp ${ANTLR_RUNTIME} ./parser/src/prog8/parser/prog8Lexer.java  ./parser/src/prog8/parser/prog8Parser.java

echo "Compiling the compiler itself..."
JAVA_OPTS="-Xmx3G -Xms300M" kotlinc -verbose -include-runtime -d ${COMPILER_JAR} -jvm-target 1.8 -cp ${ANTLR_RUNTIME}:${PARSER_CLASSES} ./compiler/src/prog8

echo "Finalizing the compiler jar file..."
# add the antlr parser classes
jar ufe ${COMPILER_JAR} prog8.CompilerMainKt -C ${PARSER_CLASSES} prog8

# add the resources
jar uf ${COMPILER_JAR} -C ./compiler/res .

# add the antlr runtime classes
rm -rf antlr_runtime_extraction
mkdir antlr_runtime_extraction
(cd antlr_runtime_extraction; jar xf ../${ANTLR_RUNTIME})
jar uf ${COMPILER_JAR} -C antlr_runtime_extraction org
rm -rf antlr_runtime_extraction

echo "Done!"
