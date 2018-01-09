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


def to_hex(number: int) -> str:
    # 0..255 -> "$00".."$ff"
    # 256..65536 -> "$0100".."$ffff"
    if number is None:
        raise ValueError("number")
    if 0 <= number < 0x100:
        return "${:02x}".format(number)
    if 0 <= number < 0x10000:
        return "${:04x}".format(number)
    raise OverflowError(number)
