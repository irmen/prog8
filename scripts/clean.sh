#!/usr/bin/env sh

rm -f *.bin *.xex *.jar *.asm *.prg *.vm.txt *.vice-mon-list *.list a.out imgui.ini
rm -rf build out
rm -rf compiler/build codeGenTargets/build codeGenCpu6502/build codeGenExperimental6502/build codeOptimizers/build compilerInterfaces/build compilerAst/build dbusCompilerService/build httpCompilerService/build parser/build

