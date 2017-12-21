"""
Intermediate Language for 6502/6510 microprocessors
Here are the symbol (name) operations such as lookups, datatype definitions.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

import inspect
import math
import enum
import builtins
from functools import total_ordering
from typing import Optional, Set, Union, Tuple, Dict, Iterable, Sequence, Any, List

PrimitiveType = Union[int, float, str]


REGISTER_SYMBOLS = {"A", "X", "Y", "AX", "AY", "XY", "SC"}
REGISTER_SYMBOLS_RETURNVALUES = REGISTER_SYMBOLS | {"SZ"}
REGISTER_BYTES = {"A", "X", "Y", "SC"}
REGISTER_WORDS = {"AX", "AY", "XY"}

# 5-byte cbm MFLPT format limitations:
FLOAT_MAX_POSITIVE = 1.7014118345e+38
FLOAT_MAX_NEGATIVE = -1.7014118345e+38

RESERVED_NAMES = {'true', 'false', 'var', 'memory', 'const', 'asm'}
RESERVED_NAMES |= REGISTER_SYMBOLS

MATH_SYMBOLS = {name for name in dir(math) if name[0].islower()}
BUILTIN_SYMBOLS = {name for name in dir(builtins) if name[0].islower()}


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

    def assignable_from_value(self, value: PrimitiveType) -> bool:
        if isinstance(value, (int, float)):
            if self == DataType.BYTE:
                return 0 <= value < 0x100
            if self == DataType.WORD:
                return 0 <= value < 0x10000
            if self == DataType.FLOAT:
                return type(value) in (float, int)
        return False

    def __lt__(self, other):
        if self.__class__ == other.__class__:
            return self.value < other.value
        return NotImplemented


STRING_DATATYPES = {DataType.STRING, DataType.STRING_P, DataType.STRING_S, DataType.STRING_PS}


class SymbolError(Exception):
    pass


_identifier_seq_nr = 0


class SourceRef:
    __slots__ = ("file", "line", "column")

    def __init__(self, file: str, line: int, column: int=0) -> None:
        self.file = file
        self.line = line
        self.column = column

    def __str__(self) -> str:
        if self.column:
            return "{:s}:{:d}:{:d}".format(self.file, self.line, self.column)
        if self.line:
            return "{:s}:{:d}".format(self.file, self.line)
        return self.file

    def copy(self) -> 'SourceRef':
        return SourceRef(self.file, self.line, self.column)


class SymbolDefinition:
    def __init__(self, blockname: str, name: str, sourceref: SourceRef, allocate: bool) -> None:
        self.blockname = blockname
        self.name = name
        self.sourceref = sourceref
        self.allocate = allocate     # set to false if the variable is memory mapped (or a constant) instead of allocated
        global _identifier_seq_nr
        self.seq_nr = _identifier_seq_nr
        _identifier_seq_nr += 1

    def __lt__(self, other: 'SymbolDefinition') -> bool:
        if not isinstance(other, SymbolDefinition):
            return NotImplemented
        return (self.blockname, self.name, self.seq_nr) < (other.blockname, other.name, self.seq_nr)

    def __str__(self):
        return "<{:s} {:s}.{:s}>".format(self.__class__.__name__, self.blockname, self.name)


class LabelDef(SymbolDefinition):
    pass


class VariableDef(SymbolDefinition):
    # if address is None, it's a dynamically allocated variable.
    # if address is not None, it's a memory mapped variable (=memory address referenced by a name).
    def __init__(self, blockname: str, name: str, sourceref: SourceRef,
                 datatype: DataType, allocate: bool, *,
                 value: PrimitiveType, length: int, address: Optional[int]=None,
                 register: str=None, matrixsize: Tuple[int, int]=None) -> None:
        super().__init__(blockname, name, sourceref, allocate)
        self.type = datatype
        self.address = address
        self.length = length
        self.value = value
        self.register = register
        self.matrixsize = matrixsize

    @property
    def is_memmap(self):
        return self.address is not None

    def __repr__(self):
        return "<Variable {:s}.{:s}, {:s}, addr {:s}, len {:s}, value {:s}>"\
            .format(self.blockname, self.name, self.type, str(self.address), str(self.length), str(self.value))

    def __lt__(self, other: 'SymbolDefinition') -> bool:
        if not isinstance(other, VariableDef):
            return NotImplemented
        v1 = (self.blockname, self.name or "", self.address or 0, self.seq_nr)
        v2 = (other.blockname, other.name or "", other.address or 0, self.seq_nr)
        return v1 < v2


class ConstantDef(SymbolDefinition):
    def __init__(self, blockname: str, name: str, sourceref: SourceRef, datatype: DataType, *,
                 value: PrimitiveType, length: int) -> None:
        super().__init__(blockname, name, sourceref, False)
        self.type = datatype
        self.length = length
        self.value = value

    def __repr__(self):
        return "<Constant {:s}.{:s}, {:s}, len {:s}, value {:s}>"\
            .format(self.blockname, self.name, self.type, str(self.length), str(self.value))

    def __lt__(self, other: 'SymbolDefinition') -> bool:
        if not isinstance(other, ConstantDef):
            return NotImplemented
        v1 = (str(self.value) or "", self.blockname, self.name or "", self.seq_nr)
        v2 = (str(other.value) or "", other.blockname, other.name or "", self.seq_nr)
        return v1 < v2


class SubroutineDef(SymbolDefinition):
    def __init__(self, blockname: str, name: str, sourceref: SourceRef,
                 parameters: Sequence[Tuple[str, str]], returnvalues: Set[str], address: Optional[int]=None) -> None:
        super().__init__(blockname, name, sourceref, False)
        self.address = address
        self.parameters = parameters
        self.input_registers = set()        # type: Set[str]
        self.return_registers = set()       # type: Set[str]
        self.clobbered_registers = set()    # type: Set[str]
        for _, param in parameters:
            if param in REGISTER_BYTES:
                self.input_registers.add(param)
            elif param in REGISTER_WORDS:
                self.input_registers.add(param[0])
                self.input_registers.add(param[1])
            else:
                raise SymbolError("invalid parameter spec: " + param)
        for register in returnvalues:
            if register in REGISTER_SYMBOLS_RETURNVALUES:
                self.return_registers.add(register)
            elif register[-1] == "?":
                for r in register[:-1]:
                    if r not in REGISTER_SYMBOLS_RETURNVALUES:
                        raise SymbolError("invalid return value spec: " + r)
                    self.clobbered_registers.add(r)
            else:
                raise SymbolError("invalid return value spec: " + register)


class Zeropage:
    SCRATCH_B1 = 0x02
    SCRATCH_B2 = 0x03

    def __init__(self) -> None:
        self.unused_bytes = []  # type: List[int]
        self.unused_words = []  # type: List[int]

    def configure(self, clobber_zp: bool = False) -> None:
        if clobber_zp:
            self.unused_bytes = list(range(0x04, 0x80))
            self.unused_words = list(range(0x80, 0x100, 2))
        else:
            # these are valid for the C-64:
            # ($02 and $03 are reserved as scratch addresses for various routines)
            self.unused_bytes = [0x06, 0x0a, 0x2a, 0x52, 0x93]  # 5 zp variables (8 bits each)
            self.unused_words = [0x04, 0xf7, 0xf9, 0xfb, 0xfd]  # 5 zp variables (16 bits each)
        assert self.SCRATCH_B1 not in self.unused_bytes and self.SCRATCH_B1 not in self.unused_words
        assert self.SCRATCH_B2 not in self.unused_bytes and self.SCRATCH_B2 not in self.unused_words

    def get_unused_byte(self):
        return self.unused_bytes.pop()

    def get_unused_word(self):
        return self.unused_words.pop()

    @property
    def available_byte_vars(self) -> int:
        return len(self.unused_bytes)

    @property
    def available_word_vars(self) -> int:
        return len(self.unused_words)


# the single, global Zeropage object
zeropage = Zeropage()


class SymbolTable:

    def __init__(self, name: str, parent: Optional['SymbolTable'], owning_block: Any) -> None:
        self.name = name
        self.symbols = {}       # type: Dict[str, Union[SymbolDefinition, SymbolTable]]
        self.parent = parent
        self.owning_block = owning_block
        self.eval_dict = None

    def __iter__(self):
        yield from self.symbols.values()

    def __getitem__(self, symbolname: str) -> Union[SymbolDefinition, 'SymbolTable']:
        return self.symbols[symbolname]

    def __contains__(self, symbolname: str) -> bool:
        return symbolname in self.symbols

    def lookup(self, dottedname: str, include_builtin_names: bool=False) -> Tuple['SymbolTable', Union[SymbolDefinition, 'SymbolTable']]:
        nameparts = dottedname.split('.')
        if len(nameparts) == 1:
            try:
                return self, self.symbols[nameparts[0]]
            except LookupError:
                if include_builtin_names:
                    if nameparts[0] in MATH_SYMBOLS:
                        return self, getattr(math, nameparts[0])
                    elif nameparts[0] in BUILTIN_SYMBOLS:
                        return self, getattr(builtins, nameparts[0])
                raise SymbolError("undefined symbol '{:s}'".format(nameparts[0]))
        # start from toplevel namespace:
        scope = self
        while scope.parent:
            scope = scope.parent
        for namepart in nameparts[:-1]:
            try:
                scope = scope.symbols[namepart]     # type: ignore
                assert scope.name == namepart
            except LookupError:
                raise SymbolError("undefined block '{:s}'".format(namepart))
        if isinstance(scope, SymbolTable):
            return scope.lookup(nameparts[-1])
        else:
            raise SymbolError("invalid block name '{:s}' in dotted name".format(namepart))

    def get_address(self, name: str) -> int:
        scope, symbol = self.lookup(name)
        if isinstance(symbol, ConstantDef):
            raise SymbolError("cannot take the address of a constant")
        if not symbol or not isinstance(symbol, VariableDef):
            raise SymbolError("no var or const defined by that name")
        if symbol.address is None:
            raise SymbolError("can only take address of memory mapped variables")
        return symbol.address

    def as_eval_dict(self) -> Dict[str, Any]:
        # return a dictionary suitable to be passed as locals or globals to eval()
        if self.eval_dict is None:
            d = Eval_symbol_dict(self)
            self.eval_dict = d      # type: ignore
        return self.eval_dict

    def iter_variables(self) -> Iterable[VariableDef]:
        yield from sorted((v for v in self.symbols.values() if isinstance(v, VariableDef)))

    def iter_constants(self) -> Iterable[ConstantDef]:
        yield from sorted((v for v in self.symbols.values() if isinstance(v, ConstantDef)))

    def iter_subroutines(self) -> Iterable[SubroutineDef]:
        yield from sorted((v for v in self.symbols.values() if isinstance(v, SubroutineDef)))

    def iter_labels(self) -> Iterable[LabelDef]:
        yield from sorted((v for v in self.symbols.values() if isinstance(v, LabelDef)))

    def check_identifier_valid(self, name: str, sourceref: SourceRef) -> None:
        if not name.isidentifier():
            raise SymbolError("invalid identifier")
        identifier = self.symbols.get(name, None)
        if identifier:
            if isinstance(identifier, SymbolDefinition):
                raise SymbolError("identifier was already defined at " + str(identifier.sourceref))
            raise SymbolError("identifier already defined as " + str(type(identifier)))
        if name in MATH_SYMBOLS:
            print("warning: {}: identifier shadows a name from the math module".format(sourceref))
        elif name in BUILTIN_SYMBOLS:
            print("warning: {}: identifier shadows a builtin name".format(sourceref))

    def define_variable(self, name: str, sourceref: SourceRef, datatype: DataType, *,
                        address: int=None, length: int=0, value: PrimitiveType=0,
                        matrixsize: Tuple[int, int]=None, register: str=None) -> None:
        # this defines a new variable and also checks if the prefill value is allowed for the variable type.
        assert value is not None
        self.check_identifier_valid(name, sourceref)
        range_error = check_value_in_range(datatype, register, length, value)
        if range_error:
            raise ValueError(range_error)
        if type(value) in (int, float):
            _, value = coerce_value(sourceref, datatype, value)   # type: ignore
        allocate = address is None
        if datatype == DataType.BYTE:
            if allocate and self.name == "ZP":
                try:
                    address = zeropage.get_unused_byte()
                except LookupError:
                    raise SymbolError("too many global 8-bit variables in ZP")
            self.symbols[name] = VariableDef(self.name, name, sourceref, DataType.BYTE, allocate,
                                             value=value, length=1, address=address)
        elif datatype == DataType.WORD:
            if allocate and self.name == "ZP":
                try:
                    address = zeropage.get_unused_word()
                except LookupError:
                    raise SymbolError("too many global 16-bit variables in ZP")
            self.symbols[name] = VariableDef(self.name, name, sourceref, DataType.WORD, allocate,
                                             value=value, length=1, address=address)
        elif datatype == DataType.FLOAT:
            if allocate and self.name == "ZP":
                raise SymbolError("floats cannot be stored in the ZP")
            self.symbols[name] = VariableDef(self.name, name, sourceref, DataType.FLOAT, allocate,
                                             value=value, length=1, address=address)
        elif datatype == DataType.BYTEARRAY:
            self.symbols[name] = VariableDef(self.name, name, sourceref, DataType.BYTEARRAY, allocate,
                                             value=value, length=length, address=address)
        elif datatype == DataType.WORDARRAY:
            self.symbols[name] = VariableDef(self.name, name, sourceref, DataType.WORDARRAY, allocate,
                                             value=value, length=length, address=address)
        elif datatype in (DataType.STRING, DataType.STRING_P, DataType.STRING_S, DataType.STRING_PS):
            self.symbols[name] = VariableDef(self.name, name, sourceref, datatype, True,
                                             value=value, length=len(value))     # type: ignore
        elif datatype == DataType.MATRIX:
            assert isinstance(matrixsize, tuple)
            length = matrixsize[0] * matrixsize[1]
            self.symbols[name] = VariableDef(self.name, name, sourceref, DataType.MATRIX, allocate,
                                             value=value, length=length, address=address, matrixsize=matrixsize)
        else:
            raise ValueError("unknown type " + str(datatype))
        self.eval_dict = None

    def define_sub(self, name: str, sourceref: SourceRef,
                   parameters: Sequence[Tuple[str, str]], returnvalues: Set[str], address: Optional[int]) -> None:
        self.check_identifier_valid(name, sourceref)
        self.symbols[name] = SubroutineDef(self.name, name, sourceref, parameters, returnvalues, address)

    def define_label(self, name: str, sourceref: SourceRef) -> None:
        self.check_identifier_valid(name, sourceref)
        self.symbols[name] = LabelDef(self.name, name, sourceref, False)

    def define_scope(self, scope: 'SymbolTable', sourceref: SourceRef) -> None:
        self.check_identifier_valid(scope.name, sourceref)
        self.symbols[scope.name] = scope

    def define_constant(self, name: str, sourceref: SourceRef, datatype: DataType, *,
                        length: int=0, value: PrimitiveType=0) -> None:
        # this defines a new constant and also checks if the value is allowed for the data type.
        assert value is not None
        self.check_identifier_valid(name, sourceref)
        if type(value) in (int, float):
            _, value = coerce_value(sourceref, datatype, value)   # type: ignore
        range_error = check_value_in_range(datatype, "", length, value)
        if range_error:
            raise ValueError(range_error)
        if datatype in (DataType.BYTE, DataType.WORD, DataType.FLOAT):
            self.symbols[name] = ConstantDef(self.name, name, sourceref, datatype, value=value, length=length or 1)
        elif datatype in STRING_DATATYPES:
            strlen = len(value)  # type: ignore
            self.symbols[name] = ConstantDef(self.name, name, sourceref, datatype, value=value, length=strlen)
        else:
            raise ValueError("invalid data type for constant: " + str(datatype))
        self.eval_dict = None

    def merge_roots(self, other_root: 'SymbolTable') -> None:
        for name, thing in other_root.symbols.items():
            if isinstance(thing, SymbolTable):
                self.define_scope(thing, thing.owning_block.sourceref)

    def print_table(self, summary_only: bool=False) -> None:
        if summary_only:
            def count_symbols(symbols: 'SymbolTable') -> int:
                count = 0
                for s in symbols.symbols.values():
                    if isinstance(s, SymbolTable):
                        count += count_symbols(s)
                    else:
                        count += 1
                return count
            print("number of symbols:", count_symbols(self))
        else:
            def print_symbols(symbols: 'SymbolTable', level: int) -> None:
                indent = '\t' * level
                print("\n" + indent + "BLOCK:", symbols.name)
                for name, s in sorted(symbols.symbols.items(), key=lambda x: getattr(x[1], "sourceref", ("", 0))):
                    if isinstance(s, SymbolTable):
                        print_symbols(s, level + 1)
                    elif isinstance(s, SubroutineDef):
                        print(indent * 2 + "SUB:   " + s.name, s.sourceref, sep="\t")
                    elif isinstance(s, LabelDef):
                        print(indent * 2 + "LABEL: " + s.name, s.sourceref, sep="\t")
                    elif isinstance(s, VariableDef):
                        print(indent * 2 + "VAR:   " + s.name, s.sourceref, s.type, sep="\t")
                    elif isinstance(s, ConstantDef):
                        print(indent * 2 + "CONST: " + s.name, s.sourceref, s.type, sep="\t")
                    else:
                        raise TypeError("invalid symbol def type", s)
            print("\nSymbols defined in the symbol table:")
            print("------------------------------------")
            print_symbols(self, 0)
            print()


class Eval_symbol_dict(dict):
    def __init__(self, symboltable: SymbolTable, constants: bool=True) -> None:
        super().__init__()
        self._symboltable = symboltable
        self._constants = constants

    def __getattr__(self, name):
        return self.__getitem__(name)

    def __getitem__(self, name):
        if name[0] != '_' and name in builtins.__dict__:
            return builtins.__dict__[name]
        try:
            scope, symbol = self._symboltable.lookup(name)
        except (LookupError, SymbolError):
            # attempt lookup from global scope
            global_scope = self._symboltable
            while global_scope.parent:
                global_scope = global_scope.parent
            scope, symbol = global_scope.lookup(name, True)
        if self._constants:
            if isinstance(symbol, ConstantDef):
                return symbol.value
            elif isinstance(symbol, VariableDef):
                return symbol.value
            elif inspect.isbuiltin(symbol):
                return symbol
            elif isinstance(symbol, SymbolTable):
                return symbol.as_eval_dict()
            else:
                raise SymbolError("invalid datatype referenced" + repr(symbol))
        else:
            raise SymbolError("no support for non-constant expression evaluation yet")


def coerce_value(sourceref: SourceRef, datatype: DataType, value: PrimitiveType) -> Tuple[bool, PrimitiveType]:
    # if we're a BYTE type, and the value is a single character, convert it to the numeric value
    if datatype in (DataType.BYTE, DataType.BYTEARRAY, DataType.MATRIX) and isinstance(value, str):
        if len(value) == 1:
            return True, char_to_bytevalue(value)
    # if we're an integer value and the passed value is float, truncate it (and give a warning)
    if datatype in (DataType.BYTE, DataType.WORD, DataType.MATRIX) and type(value) is float:
        frac = math.modf(value)   # type:ignore
        if frac != 0:
            print("warning: {}: Float value truncated.".format(sourceref))
            return True, int(value)
    return False, value


def check_value_in_range(datatype: DataType, register: str, length: int, value: PrimitiveType) -> Optional[str]:
    if register:
        if register in REGISTER_BYTES:
            if value < 0 or value > 0xff:  # type: ignore
                return "value out of range, must be (unsigned) byte for a single register"
        elif register in REGISTER_WORDS:
            if value is None and datatype in (DataType.BYTE, DataType.WORD):
                return None
            if value < 0 or value > 0xffff:  # type: ignore
                return "value out of range, must be (unsigned) word for 2 combined registers"
        else:
            return "strange register"
    elif datatype in (DataType.BYTE, DataType.BYTEARRAY, DataType.MATRIX):
        if value is None and datatype == DataType.BYTE:
            return None
        if value < 0 or value > 0xff:       # type: ignore
            return "value out of range, must be (unsigned) byte"
    elif datatype in (DataType.WORD, DataType.WORDARRAY):
        if value is None and datatype in (DataType.BYTE, DataType.WORD):
            return None
        if value < 0 or value > 0xffff:     # type: ignore
            return "value out of range, must be (unsigned) word"
    elif datatype in STRING_DATATYPES:
        if type(value) is not str:
            return "value must be a string"
    elif datatype == DataType.FLOAT:
        if type(value) not in (int, float):
            return "value must be a number"
    else:
        raise SymbolError("missing value check for type", datatype, register, length, value)
    return None  # all ok !


def char_to_bytevalue(character: str, petscii: bool=True) -> int:
    assert len(character) == 1
    if petscii:
        return ord(character.translate(ascii_to_petscii_trans))
    else:
        raise NotImplementedError("screencode conversion not yet implemented for chars")


# ASCII/UNICODE-to-PETSCII translation table
# Unicode symbols supported that map to a PETSCII character:  £ ↑ ← ♠ ♥ ♦ ♣ π ● ○ and various others
ascii_to_petscii_trans = str.maketrans({
    '\f': 147,  # form feed becomes ClearScreen
    '\n': 13,   # line feed becomes a RETURN
    '\r': 17,   # CR becomes CursorDown
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
