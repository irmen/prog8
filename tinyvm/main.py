import sys
from .parse import Parser
from .core import Program, Opcode, Block, Instruction
from .vm import VM


source = open(sys.argv[1]).read()

parser = Parser(source)
program = parser.parse()
timerprogram = Program([Block("timer", None, [], [
    Instruction(Opcode.RETURN, [], None, None)
], {}, [])])
# zero page and hardware stack of a 6502 cpu are off limits for now
VM.readonly_mem_ranges = [(0x00, 0xff), (0x100, 0x1ff), (0xa000, 0xbfff), (0xe000, 0xffff)]
vm = VM(program)
vm.run()
