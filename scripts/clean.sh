#!/usr/bin/env sh

rm -f *.bin *.xex *.jar *.asm *.prg *.vm.txt *.vice-mon-list *.list *.p8ir a.out imgui.ini
rm -rf build out
rm -rf compiler/build codeGenCpu6502/build codeGenExperimental/build codeGenIntermediate/build intermediate/build virtualmachine/build codeOptimizers/build compilerAst/build dbusCompilerService/build httpCompilerService/build parser/build parser/src/prog8/parser 

