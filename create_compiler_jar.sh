#!/bin/sh

# this script uses the Gradle build to compile the code,
# and then adds the contents of several jar files into one output jar.

./gradlew jar

mkdir -p compiler_jar/extracted
mkdir -p compiler_jar/source
cp compiler/build/libs/compiler.jar parser/build/libs/parser.jar parser/antlr/lib/antlr-runtime-4.7.2.jar compiler_jar/source/

KOTLINLIBS=$(kotlinc -verbose -script 2>&1 | grep home | cut -d ' ' -f 6-)/lib
cp ${KOTLINLIBS}/kotlin-stdlib-jdk8.jar ${KOTLINLIBS}/kotlin-stdlib.jar compiler_jar/source/

pushd compiler_jar/extracted
for i in ../source/*.jar; do jar xf $i; done
cd ..
jar cfe ../prog8compiler.jar prog8.CompilerMainKt -C extracted .
popd
rm -r compiler_jar
ls -l prog8compiler.jar
