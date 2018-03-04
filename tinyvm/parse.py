"""
Simplistic 8/16 bit Virtual Machine to execute a stack based instruction language.
Parser for the simplistic text based program representation

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import array
from typing import Optional, List, Tuple, Dict, Any
from .program import DataType, Opcode, Program, Block, Variable, Instruction, Value
from .vm import StackValueType


class ParseError(Exception):
    pass


class Parser:
    def __init__(self, source: str) -> None:
        self.source = source.splitlines()
        self.lineno = 0

    def parse(self) -> Program:
        blocklist = []
        while self.lineno < len(self.source):
            self.skip_empty()
            blocklist.append(self.parse_block(None))
        return Program(blocklist)

    def skip_empty(self) -> None:
        while self.lineno < len(self.source):
            line = self.source[self.lineno].strip()
            if not line or line.startswith(";"):
                self.lineno += 1
            else:
                break

    def parse_block(self, parent: Optional[Block]) -> Block:
        assert self.source[self.lineno].startswith("%block")
        blockname = self.source[self.lineno].split()[1]
        variables = []      # type: List[Variable]
        instructions = []   # type: List[Instruction]
        labels = {}         # type: Dict[str, Instruction]
        self.lineno += 1
        self.skip_empty()
        if self.source[self.lineno].startswith("%vardefs"):
            variables = self.parse_vardefs()
        self.skip_empty()
        if self.source[self.lineno].startswith("%instructions"):
            instructions, labels = self.parse_instructions()
        block = Block(blockname, parent, variables, instructions, labels, [])
        self.skip_empty()
        if self.source[self.lineno].startswith("%subblocks"):
            block.blocks = self.parse_subblocks(block)
            self.skip_empty()
        assert self.source[self.lineno].startswith("%end_block")
        self.lineno += 1
        return block

    def get_array_type(self, dtype: DataType) -> str:
        return {
            DataType.ARRAY_BYTE: 'B',
            DataType.ARRAY_SBYTE: 'b',
            DataType.ARRAY_WORD: 'H',
            DataType.ARRAY_SWORD: 'h',
            DataType.MATRIX_BYTE: 'B',
            DataType.MATRIX_SBYTE: 'b'
        }[dtype]

    def parse_vardefs(self) -> List[Variable]:
        assert self.source[self.lineno].startswith("%vardefs")
        self.lineno += 1
        variables = []
        while not self.source[self.lineno].startswith("%"):
            vartype, datatype, name, argstr = self.source[self.lineno].split(maxsplit=3)
            dtype = DataType[datatype.upper()]
            length = height = 0
            value = None  # type: StackValueType
            if dtype in (DataType.BYTE, DataType.WORD, DataType.SBYTE, DataType.SWORD):
                value = Value(dtype, int(argstr))
            elif dtype == DataType.FLOAT:
                value = Value(dtype, float(argstr))
            elif dtype == DataType.BOOL:
                value = Value(dtype, argstr.lower() not in ("0", "false"))
            elif dtype in (DataType.ARRAY_BYTE, DataType.ARRAY_SBYTE, DataType.ARRAY_WORD, DataType.ARRAY_SWORD):
                args = argstr.split(maxsplit=1)
                length = int(args[0])
                valuestr = args[1]
                typecode = self.get_array_type(dtype)
                if valuestr[0] == '[' and valuestr[-1] == ']':
                    value = Value(dtype, array.array(typecode, [int(v) for v in valuestr[1:-1].split()]))
                else:
                    value = Value(dtype, array.array(typecode, [int(valuestr)]) * length)
            elif dtype in (DataType.MATRIX_BYTE, DataType.MATRIX_SBYTE):
                args = argstr.split(maxsplit=2)
                length = int(args[0])
                height = int(args[1])
                valuestr = args[2]
                typecode = self.get_array_type(dtype)
                if valuestr[0] == '[' and valuestr[-1] == ']':
                    value = Value(dtype, array.array(typecode, [int(v) for v in valuestr[1:-1].split()]))
                else:
                    value = Value(dtype, array.array(typecode, [int(valuestr)] * length * height))
            else:
                raise TypeError("weird dtype", dtype)
            variables.append(Variable(name, dtype, value, vartype == "const"))
            self.lineno += 1
        self.skip_empty()
        assert self.source[self.lineno].startswith("%end_vardefs")
        self.lineno += 1
        return variables

    def parse_instructions(self) -> Tuple[List[Instruction], Dict[str, Instruction]]:
        assert self.source[self.lineno].startswith("%instructions")
        self.lineno += 1
        instructions = []
        labels = {}    # type: Dict[str, Instruction]

        def parse_instruction(ln: str) -> Instruction:
            parts = ln.split(maxsplit=1)
            opcode = Opcode[parts[0].upper()]
            args = []   # type: List[Any]
            if len(parts) == 2:
                args = parts[1].split()
            else:
                args = []
            if opcode in (Opcode.CALL, Opcode.RETURN):
                args[0] = int(args[0])   # the number of arguments/parameters
            return Instruction(opcode, args, None, None)

        while not self.source[self.lineno].startswith("%"):
            line = self.source[self.lineno].strip()
            if line.endswith(":"):
                # a label that points to an instruction
                label = line[:-1].rstrip()
                self.lineno += 1
                line = self.source[self.lineno]
                next_instruction = parse_instruction(line)
                labels[label] = next_instruction
                instructions.append(next_instruction)
                self.lineno += 1
            else:
                instructions.append(parse_instruction(line))
                self.lineno += 1
        self.skip_empty()
        assert self.source[self.lineno].startswith("%end_instructions")
        self.lineno += 1
        return instructions, labels

    def parse_subblocks(self, parent: Block) -> List[Block]:
        assert self.source[self.lineno].startswith("%subblocks")
        self.lineno += 1
        blocks = []
        while not self.source[self.lineno].startswith("%end_subblocks"):
            self.lineno += 1
            while True:
                if self.source[self.lineno].startswith("%block"):
                    blocks.append(self.parse_block(parent))
                else:
                    break
            self.skip_empty()
        self.lineno += 1
        return blocks


if __name__ == "__main__":
    src = """
%block   b1
%vardefs
var     byte    v1      0
var     word    w1      2222
var     sword   ws      -3333
const   byte    c1      99
const   sword   cws     -5444
var     array_byte  ba  10  33
var     array_byte  ba2  10  [1 2 3 4 5 6 7 8 9 10]
var     matrix_byte mxb  4 5 33
var     matrix_byte mxb2 3 2 [1 2 3 4 5 6]
%end_vardefs

%instructions
    nop
    nop
l1:
    nop
    push  c1
    push2  c1 cws
    call  3  l1
    return  2
%end_instructions

%subblocks

%block  b2
%vardefs
%end_vardefs
%end_block  ; b2

%end_subblocks
%end_block ;b1



%block   b3
%vardefs
%end_vardefs
%instructions
    nop
    nop
l1:
    nop
    return 99
%end_instructions
%end_block    ; b3
"""
    parser = Parser(src)
    program = parser.parse()
