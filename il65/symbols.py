"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
Here are the symbol (name) operations such as lookups, datatype definitions.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""


import enum


class DataType(enum.Enum):
    """The possible data types of values"""
    BYTE = 1
    WORD = 2
    FLOAT = 3
    BYTEARRAY = 4
    WORDARRAY = 5
    MATRIX = 6
    STRING = 7
    STRING_P = 8
    STRING_S = 9
    STRING_PS = 10


STRING_DATATYPES = {DataType.STRING, DataType.STRING_P, DataType.STRING_S, DataType.STRING_PS}
