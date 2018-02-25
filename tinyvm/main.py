import sys
from .parse import Parser
from .core import Program
from .vm import VM


source = open(sys.argv[1]).read()

parser = Parser(source)
program = parser.parse()
timerprogram = Program([])
vm = VM(program, timerprogram)
vm.run()
