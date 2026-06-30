#!/bin/sh
# This is a simple script to quickly attempt to compile the small test program using the new 6502 codegen backend
# It stops on the first failure.

set -e

prog8c -target cx16 -expericodegen -noopt examples/test.p8
new6502gen/build/install/new6502gen/bin/prog8-newgen test.p8ir
x16emu -run -prg test.prg -echo iso

