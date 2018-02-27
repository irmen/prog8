import enum
from typing import Any, List, Dict, Optional


class Opcode(enum.IntEnum):
    NOP = 0
    TERMINATE = 1
    PUSH = 10
    PUSH2 = 11
    PUSH3 = 12
    POP = 20
    POP2 = 21
    POP3 = 22
    ADD = 100
    SUB = 101
    MUL = 102
    DIV = 103
    AND = 200
    OR = 201
    XOR = 202
    NOT = 203
    CMP_EQ = 300
    CMP_LT = 301
    CMP_GT = 302
    CMP_LTE = 303
    CMP_GTE = 304
    TEST = 305
    RETURN = 500
    JUMP = 501
    JUMP_IF_TRUE = 502
    JUMP_IF_FALSE = 503
    SYSCALL = 504


CONDITIONAL_OPCODES = {Opcode.JUMP_IF_FALSE, Opcode.JUMP_IF_TRUE}


class DataType(enum.IntEnum):
    BOOL = 1
    BYTE = 2
    SBYTE = 3
    WORD = 4
    SWORD = 5
    FLOAT = 6
    ARRAY_BYTE = 7
    ARRAY_SBYTE = 8
    ARRAY_WORD = 9
    ARRAY_SWORD = 10
    MATRIX_BYTE = 11
    MATRIX_SBYTE = 12


class Variable:
    __slots__ = ["name", "value", "dtype", "length", "height", "const"]

    def __init__(self, name: str, dtype: DataType, value: Any, length: int=0, height: int=0, const: bool=False) -> None:
        self.name = name
        self.value = value
        self.dtype = dtype
        self.const = const
        self.length = length
        self.height = height


class Instruction:
    __slots__ = ["opcode", "args", "next", "condnext"]

    def __init__(self, opcode: Opcode, args: List[Any], nxt: Optional['Instruction'], condnxt: Optional['Instruction']) -> None:
        self.opcode = opcode
        self.args = args
        self.next = nxt            # regular next statement, None=end
        self.condnext = condnxt    # alternate next statement (for condition nodes)

    def __str__(self) -> str:
        return "<Instruction {} args: {}>".format(self.opcode.name, self.args)


class Block:
    def __init__(self, name: str, parent: 'Block',
                 variables: List[Variable],
                 instructions: List[Instruction],
                 labels: Dict[str, Instruction],        # named entry points
                 blocks: List['Block']) -> None:
        self.name = name
        self.parent = parent
        self.variables = variables
        self.blocks = blocks
        self.instructions = instructions
        self.labels = labels

    def __str__(self) -> str:
        if self.parent:
            return "<Block '{}' in '{}'>".format(self.name, self.parent.name)
        return "<Block '{}'>".format(self.name)


class Program:
    def __init__(self, blocks: List[Block]) -> None:
        self.blocks = blocks
