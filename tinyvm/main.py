import sys
from .parse import Parser
# from .program import Program, Opcode, Block, Instruction
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

# zero page and hardware stack of a 6502 cpu are off limits for now
VM.readonly_mem_ranges = [(0x00, 0xff), (0x100, 0x1ff), (0xa000, 0xbfff), (0xe000, 0xffff)]
vm = VM(mainprogram, timerprogram)
vm.enable_charscreen(0x0400, 40, 25)
vm.run()
