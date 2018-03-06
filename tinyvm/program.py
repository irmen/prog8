"""
Simplistic 8/16 bit Virtual Machine to execute a stack based instruction language.
These are the program/instruction definitions that make up a program for the vm

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import enum
import array
import operator
from typing import List, Dict, Optional, Union, Callable, Any
from .core import DataType


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
    SYSCALL = 202
    JUMP = 203
    JUMP_IF_TRUE = 204
    JUMP_IF_FALSE = 205
    JUMP_IF_STATUS_ZERO = 206
    JUMP_IF_STATUS_NE = 207
    JUMP_IF_STATUS_EQ = 208
    JUMP_IF_STATUS_CC = 209
    JUMP_IF_STATUS_CS = 210
    JUMP_IF_STATUS_VC = 211
    JUMP_IF_STATUS_VS = 212
    JUMP_IF_STATUS_GE = 213
    JUMP_IF_STATUS_LE = 214
    JUMP_IF_STATUS_GT = 215
    JUMP_IF_STATUS_LT = 216
    JUMP_IF_STATUS_POS = 217
    JUMP_IF_STATUS_NEG = 218


class Value:
    __slots__ = ["dtype", "value", "length", "height"]

    def __init__(self, dtype: DataType, value: Union[int, float, bytearray, array.array], length: int=0, height: int=0) -> None:
        self.dtype = dtype
        self.value = value
        self.length = length
        self.height = height

    def __str__(self):
        return repr(self)

    def __repr__(self):
        return "<Value dtype={} val={}>".format(self.dtype.name, self.value)

    def number_arithmetic(self, v1: 'Value', oper: Callable, v2: 'Value') -> 'Value':
        if v1.dtype != DataType.FLOAT and v2.dtype == DataType.FLOAT:
            raise TypeError("cannot use a float in arithmetic operation on an integer", v1, oper.__name__, v2)
        if v1.dtype == DataType.BYTE:
            return Value(DataType.BYTE, oper(v1.value, v2.value) & 255)
        if v1.dtype == DataType.SBYTE:
            result = oper(v1.value, v2.value)
            if result < -128 or result > 127:
                raise OverflowError("sbyte", result)
            return Value(DataType.SBYTE, result)
        if v1.dtype == DataType.WORD:
            return Value(DataType.WORD, oper(v1.value, v2.value) & 65535)
        if v1.dtype == DataType.SWORD:
            result = oper(v1.value, v2.value)
            if result < -32768 or result > 32767:
                raise OverflowError("sword", result)
            return Value(DataType.SWORD, result)
        if v1.dtype == DataType.FLOAT:
            return Value(DataType.FLOAT, oper(v1.value, v2.value))
        raise TypeError("cannot {} {}, {}".format(oper.__name__,  v1, v2))

    def number_comparison(self, v1: 'Value', oper: Callable, v2: 'Value') -> bool:
        if v1.dtype != DataType.FLOAT and v2.dtype == DataType.FLOAT:
            raise TypeError("cannot use a float in logical operation on an integer", v1, oper.__name__, v2)
        return oper(v1.value, v2.value)

    def __add__(self, other: 'Value') -> 'Value':
        return self.number_arithmetic(self, operator.add, other)

    def __sub__(self, other: 'Value') -> 'Value':
        return self.number_arithmetic(self, operator.sub, other)

    def __mul__(self, other: 'Value') -> 'Value':
        return self.number_arithmetic(self, operator.sub, other)

    def __truediv__(self, other: 'Value') -> 'Value':
        return self.number_arithmetic(self, operator.truediv, other)

    def __floordiv__(self, other: 'Value') -> 'Value':
        return self.number_arithmetic(self, operator.floordiv, other)

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, Value):
            return False
        return self.number_comparison(self, operator.eq, other)

    def __lt__(self, other: 'Value') -> bool:
        return self.number_comparison(self, operator.lt, other)

    def __le__(self, other: 'Value') -> bool:
        return self.number_comparison(self, operator.le, other)

    def __gt__(self, other: 'Value') -> bool:
        return self.number_comparison(self, operator.gt, other)

    def __ge__(self, other: 'Value') -> bool:
        return self.number_comparison(self, operator.ge, other)


class Variable:
    __slots__ = ["name", "value", "dtype", "length", "height", "const"]

    def __init__(self, name: str, dtype: DataType, value: Value, const: bool=False) -> None:
        self.name = name
        self.value = value
        self.dtype = dtype
        self.const = const

    def __str__(self):
        return repr(self)

    def __repr__(self):
        return "<Var name={} value={} const? {}>".format(self.name, self.value, self.const)


class Instruction:
    __slots__ = ["opcode", "args", "next", "alt_next"]

    def __init__(self, opcode: Opcode, args: List[Union[Value, int, str]],
                 nxt: Optional['Instruction']=None, alt_next: Optional['Instruction']=None) -> None:
        self.opcode = opcode
        self.args = args
        self.next = nxt             # regular next statement, None=end
        self.alt_next = alt_next    # alternate next statement (for condition nodes, and return instruction for call nodes)

    def __str__(self):
        return repr(self)

    def __repr__(self):
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

    def __str__(self):
        return repr(self)

    def __repr__(self):
        if self.parent:
            return "<Block '{}' in '{}'>".format(self.name, self.parent.name)
        return "<Block '{}'>".format(self.name)


class Program:
    def __init__(self, blocks: List[Block]) -> None:
        self.blocks = blocks
