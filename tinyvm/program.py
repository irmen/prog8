import enum
from typing import Any, List, Dict, Optional


class Opcode(enum.IntEnum):
    TERMINATE = 0
    NOP = 1
    PUSH = 10
    PUSH2 = 11
    PUSH3 = 12
    POP = 13
    POP2 = 14
    POP3 = 15
    DUP = 16
    DUP2 = 17
    SWAP = 18
    ADD = 50
    SUB = 51
    MUL = 52
    DIV = 53
    AND = 70
    OR = 71
    XOR = 72
    NOT = 73
    TEST = 100
    CMP_EQ = 101
    CMP_LT = 102
    CMP_GT = 103
    CMP_LTE = 104
    CMP_GTE = 105
    CALL = 200
    RETURN = 201
    JUMP = 202
    JUMP_IF_TRUE = 203
    JUMP_IF_FALSE = 204
    SYSCALL = 205


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
    __slots__ = ["opcode", "args", "next", "alt_next"]

    def __init__(self, opcode: Opcode, args: List[Any], nxt: Optional['Instruction']=None, alt_next: Optional['Instruction']=None) -> None:
        self.opcode = opcode
        self.args = args
        self.next = nxt             # regular next statement, None=end
        self.alt_next = alt_next    # alternate next statement (for condition nodes, and return instruction for call nodes)

    def __str__(self) -> str:
        return "<Instruction {} args: {}>".format(self.opcode.name, self.args)


class Block:
    def __init__(self, name: str, parent: 'Block',
                 variables: List[Variable] = None,
                 instructions: List[Instruction] = None,
                 labels: Dict[str, Instruction] = None,        # named entry points
                 subblocks: List['Block'] = None) -> None:
        self.name = name
        self.parent = parent
        self.variables = variables or []
        self.blocks = subblocks or []
        self.instructions = instructions or []
        self.labels = labels or {}

    def __str__(self) -> str:
        if self.parent:
            return "<Block '{}' in '{}'>".format(self.name, self.parent.name)
        return "<Block '{}'>".format(self.name)


class Program:
    def __init__(self, blocks: List[Block]) -> None:
        self.blocks = blocks
