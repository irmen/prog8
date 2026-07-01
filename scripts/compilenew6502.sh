#!/bin/sh
# This is a simple script to quickly attempt to compile the small test program using the new 6502 codegen backend
# It stops on the first failure.

set -e

prog8c -target cx16 -expericodegen examples/test.p8
codeGenNew6502/build/install/codeGenNew6502/bin/prog8-newgen test.p8ir
x16emu -run -prg test.prg -echo iso

