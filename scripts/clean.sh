#!/usr/bin/env sh

rm -f *.bin *.xex *.jar *.asm *.prg *.vm.txt *.vice-mon-list *.list *.p8ir a.out imgui.ini
rm -rf build out
rm -rf compiler/build codeGenCpu6502/build codeGenM68k/build codeGenNew6502/build codeGenIntermediate/build intermediate/build virtualmachine/build codeOptimizers/build compilerAst/build languageServer/build parser/build parser/src/prog8/parser

