"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
Here are the data type definitions and -conversions.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import enum
from functools import total_ordering


@total_ordering
class VarType(enum.Enum):
    CONST = 1
    MEMORY = 2
    VAR = 3

    def __lt__(self, other):
        if self.__class__ == other.__class__:
            return self.value < other.value
        return NotImplemented


@total_ordering
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

    def __lt__(self, other):
        if self.__class__ == other.__class__:
            return self.value < other.value
        return NotImplemented

    def isnumeric(self) -> bool:
        return self.value in (1, 2, 3)

    def isinteger(self) -> bool:
        return self.value in (1, 2)

    def isarray(self) -> bool:
        return self.value in (4, 5, 6)

    def isstring(self) -> bool:
        return self.value in (7, 8, 9, 10)


STRING_DATATYPES = {DataType.STRING, DataType.STRING_P, DataType.STRING_S, DataType.STRING_PS}


REGISTER_SYMBOLS = {"A", "X", "Y", "AX", "AY", "XY", "SC", "SI"}
REGISTER_SYMBOLS_RETURNVALUES = REGISTER_SYMBOLS | {"SZ"}
REGISTER_BYTES = {"A", "X", "Y"}
REGISTER_SBITS = {"SC", "SI", "SZ"}
REGISTER_WORDS = {"AX", "AY", "XY"}

# 5-byte cbm MFLPT format limitations:
FLOAT_MAX_POSITIVE = 1.7014118345e+38
FLOAT_MAX_NEGATIVE = -1.7014118345e+38


def char_to_bytevalue(character: str, petscii: bool=True) -> int:
    assert len(character) == 1
    if petscii:
        return ord(character.translate(ascii_to_petscii_trans))
    else:
        raise NotImplementedError("screencode conversion not yet implemented for chars")


# ASCII/UNICODE-to-PETSCII translation table
# Unicode symbols supported that map to a PETSCII character:  £ ↑ ← ♠ ♥ ♦ ♣ π ● ○ and various others
ascii_to_petscii_trans = str.maketrans({
    '\f': 147,  # form feed becomes ClearScreen  "{clear}"
    '\n': 13,   # line feed becomes a RETURN  "{cr}"  (not a line feed)
    '\r': 17,   # CR becomes CursorDown  "{down}"
    'a': 65,
    'b': 66,
    'c': 67,
    'd': 68,
    'e': 69,
    'f': 70,
    'g': 71,
    'h': 72,
    'i': 73,
    'j': 74,
    'k': 75,
    'l': 76,
    'm': 77,
    'n': 78,
    'o': 79,
    'p': 80,
    'q': 81,
    'r': 82,
    's': 83,
    't': 84,
    'u': 85,
    'v': 86,
    'w': 87,
    'x': 88,
    'y': 89,
    'z': 90,
    'A': 97,
    'B': 98,
    'C': 99,
    'D': 100,
    'E': 101,
    'F': 102,
    'G': 103,
    'H': 104,
    'I': 105,
    'J': 106,
    'K': 107,
    'L': 108,
    'M': 109,
    'N': 110,
    'O': 111,
    'P': 112,
    'Q': 113,
    'R': 114,
    'S': 115,
    'T': 116,
    'U': 117,
    'V': 118,
    'W': 119,
    'X': 120,
    'Y': 121,
    'Z': 122,
    '{': 179,       # left squiggle
    '}': 235,       # right squiggle
    '£': 92,        # pound currency sign
    '^': 94,        # up arrow
    '~': 126,       # pi math symbol
    'π': 126,       # pi symbol
    '`': 39,        # single quote
    '✓': 250,       # check mark

    '|': 221,       # vertical bar
    '│': 221,       # vertical bar
    '─': 96,        # horizontal bar
    '┼': 123,       # vertical and horizontal bar

    '↑': 94,        # up arrow
    '←': 95,        # left arrow

    '▔': 163,       # upper bar
    '_': 164,       # lower bar (underscore)
    '▁': 164,       # lower bar
    '▎': 165,       # left bar

    '♠': 97,        # spades
    '●': 113,       # circle
    '♥': 115,       # hearts
    '○': 119,       # open circle
    '♣': 120,       # clubs
    '♦': 122,       # diamonds

    '├': 171,       # vertical and right
    '┤': 179,       # vertical and left
    '┴': 177,       # horiz and up
    '┬': 178,       # horiz and down
    '└': 173,       # up right
    '┐': 174,       # down left
    '┌': 175,       # down right
    '┘': 189,       # up left
    '▗': 172,       # block lr
    '▖': 187,       # block ll
    '▝': 188,       # block ur
    '▘': 190,       # block ul
    '▚': 191,       # block ul and lr
    '▌': 161,       # left half
    '▄': 162,       # lower half
    '▒': 230,       # raster
})
