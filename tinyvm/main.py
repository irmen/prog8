"""
Simplistic 8/16 bit Virtual Machine to execute a stack based instruction language.
Main entry point to launch a VM to execute the given programs.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import sys
from .parse import Parser
from .vm import VM

mainprogram = None
timerprogram = None

if len(sys.argv) >= 2:
    source = open(sys.argv[1]).read()
    parser = Parser(source)
    mainprogram = parser.parse()

if len(sys.argv) == 3:
    source = open(sys.argv[2]).read()
    parser = Parser(source)
    timerprogram = parser.parse()

if len(sys.argv) not in (2, 3):
    raise SystemExit("provide 1 or 2 program file names as arguments")

# ZeroPage and hardware stack of a 6502 cpu are off limits for now
VM.readonly_mem_ranges = [(0x00, 0xff), (0x100, 0x1ff)]
vm = VM(mainprogram, timerprogram)
vm.enable_charscreen(0x0400, 40, 25)
vm.run()
