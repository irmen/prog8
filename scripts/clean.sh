#!/usr/bin/env sh
# remove generated artifacts; always operates on the repo root (the parent of this script's dir)
ROOT=$(CDPATH= cd -- "$(dirname -- "$0")/.." && pwd)

rm -f "$ROOT"/*.bin "$ROOT"/*.xex "$ROOT"/*.jar "$ROOT"/*.asm "$ROOT"/*.prg "$ROOT"/*.vm.txt "$ROOT"/*.vice-mon-list "$ROOT"/*.list "$ROOT"/*.p8ir "$ROOT"/*.o "$ROOT"/*.elf "$ROOT"/a.out "$ROOT"/imgui.ini
rm -rf "$ROOT"/build "$ROOT"/out
rm -rf "$ROOT"/compiler/build "$ROOT"/codeCore/build "$ROOT"/simpleAst/build "$ROOT"/codeGenCpu6502/build "$ROOT"/codeGenM68k/build "$ROOT"/codeGenNew6502/build "$ROOT"/codeGenIntermediate/build "$ROOT"/intermediate/build "$ROOT"/virtualmachine/build "$ROOT"/codeOptimizers/build "$ROOT"/compilerAst/build "$ROOT"/languageServer/build "$ROOT"/parser/build "$ROOT"/parser/src/prog8/parser "$ROOT"/docs/build
