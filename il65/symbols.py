"""
Programming Language for 6502/6510 microprocessors
Here are the symbol (name) operations such as lookups, datatype definitions.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

import inspect
import math
import enum
import builtins
from functools import total_ordering
from typing import Optional, Set, Union, Tuple, Dict, Iterable, Sequence, Any, List, Generator


PrimitiveType = Union[int, float, str]


REGISTER_SYMBOLS = {"A", "X", "Y", "AX", "AY", "XY", "SC", "SI"}
REGISTER_SYMBOLS_RETURNVALUES = REGISTER_SYMBOLS | {"SZ"}
REGISTER_BYTES = {"A", "X", "Y"}
REGISTER_SBITS = {"SC", "SI", "SZ"}
REGISTER_WORDS = {"AX", "AY", "XY"}

# 5-byte cbm MFLPT format limitations:
FLOAT_MAX_POSITIVE = 1.7014118345e+38
FLOAT_MAX_NEGATIVE = -1.7014118345e+38

RESERVED_NAMES = {'true', 'false', 'var', 'memory', 'const', 'asm', 'byte', 'word', 'float'}
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
        self.sourceref = sourceref.copy()
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
                 register: str=None, matrixsize: Tuple[int, int]=None, sourcecomment: str="") -> None:
        super().__init__(blockname, name, sourceref, allocate)
        self.type = datatype
        self.address = address
        self.length = length
        self.value = value
        self.register = register
        self.matrixsize = matrixsize
        self.sourcecomment = sourcecomment

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
                 parameters: Sequence[Tuple[str, str]], returnvalues: Sequence[str],
                 address: Optional[int]=None, sub_block: 'Block'=None) -> None:
        super().__init__(blockname, name, sourceref, False)
        self.address = address
        self.sub_block = sub_block
        self.parameters = parameters
        self.clobbered_registers = set()    # type: Set[str]
        self.return_registers = []      # type: List[str]  # ordered!
        for _, param in parameters:
            if param in REGISTER_BYTES | REGISTER_SBITS:
                self.clobbered_registers.add(param)
            elif param in REGISTER_WORDS:
                self.clobbered_registers.add(param[0])
                self.clobbered_registers.add(param[1])
            else:
                raise SymbolError("invalid parameter spec: " + param)
        for register in returnvalues:
            if register in REGISTER_SYMBOLS_RETURNVALUES:
                self.return_registers.append(register)
                if len(register) == 1:
                    self.clobbered_registers.add(register)
                else:
                    self.clobbered_registers.add(register[0])
                    self.clobbered_registers.add(register[1])
            elif register[-1] == "?":
                for r in register[:-1]:
                    if r not in REGISTER_SYMBOLS_RETURNVALUES:
                        raise SymbolError("invalid return value spec: " + r)
                    if len(r) == 1:
                        self.clobbered_registers.add(r)
                    else:
                        self.clobbered_registers.add(r[0])
                        self.clobbered_registers.add(r[1])
            else:
                raise SymbolError("invalid return value spec: " + register)


class Zeropage:
    SCRATCH_B1 = 0x02
    SCRATCH_B2 = 0x03
    SCRATCH_W1 = 0xfb     # $fb/$fc
    SCRATCH_W2 = 0xfd     # $fd/$fe

    def __init__(self) -> None:
        self.free = []  # type: List[int]
        self.allocations = {}   # type: Dict[int, Tuple[str, DataType]]
        self._configured = False

    def configure(self, clobber_zp: bool = False) -> None:
        if self._configured:
            raise SymbolError("cannot configure the ZP multiple times")
        if clobber_zp:
            self.free = list(range(0x04, 0xfb)) + [0xff]
            for updated_by_irq in [0xa0, 0xa1, 0xa2, 0x91, 0xc0, 0xc5, 0xcb, 0xf5, 0xf6]:
                self.free.remove(updated_by_irq)
        else:
            # these are valid for the C-64 (when no RS232 I/O is performed):
            # ($02, $03, $fb-$fc, $fd-$fe are reserved as scratch addresses for various routines)
            self.free = [0x04, 0x05, 0x06, 0x2a, 0x52, 0xf7, 0xf8, 0xf9, 0xfa]
        assert self.SCRATCH_B1 not in self.free
        assert self.SCRATCH_B2 not in self.free
        assert self.SCRATCH_W1 not in self.free
        assert self.SCRATCH_W2 not in self.free
        self._configured = True

    def allocate(self, name: str, datatype: DataType) -> int:
        assert self._configured
        size = {
            DataType.BYTE: 1,
            DataType.WORD: 2,
            DataType.FLOAT: 5
        }[datatype]

        def sequential(loc: int) -> bool:
            for i in range(size):
                if loc+i not in self.free:
                    return False
            return True

        if len(self.free) > 0:
            if size == 1:
                assert not name or name not in {a[0] for a in self.allocations.values()}
                loc = self.free.pop()
                self.allocations[loc] = (name or "<unnamed>", datatype)
                return loc
            for candidate in range(min(self.free), max(self.free)+1):
                if sequential(candidate):
                    assert not name or name not in {a[0] for a in self.allocations.values()}
                    for loc in range(candidate, candidate+size):
                        self.free.remove(loc)
                    self.allocations[candidate] = (name or "<unnamed>", datatype)
                    return candidate
        raise LookupError("no more free space in ZP to allocate {:d} sequential bytes".format(size))

    def available(self) -> int:
        return len(self.free)


class SymbolTable:

    def __init__(self, name: str, parent: Optional['SymbolTable'], owning_block: 'Block') -> None:
        self.name = name
        self.symbols = {}       # type: Dict[str, Union[SymbolDefinition, SymbolTable]]
        self.parent = parent
        self.owning_block = owning_block
        self.eval_dict = None
        self._zeropage = parent._zeropage if parent else None       # type: Zeropage

    def set_zeropage(self, zp: Zeropage) -> None:
        if self._zeropage is None:
            self._zeropage = zp
        else:
            raise SymbolError("already have a zp")

    def __iter__(self):
        yield from self.symbols.values()

    def __getitem__(self, symbolname: str) -> Union[SymbolDefinition, 'SymbolTable']:
        return self.symbols[symbolname]

    def __contains__(self, symbolname: str) -> bool:
        return symbolname in self.symbols

    def lookup(self, dottedname: str, include_builtin_names: bool=False) -> Tuple['SymbolTable', Union[SymbolDefinition, 'SymbolTable']]:
        # Tries to find the dottedname in the current symbol table (if it is not scoped),
        # or globally if it is scoped (=contains a '.'). If required, math and builtin symbols
        # such as 'sin' or 'max' are also resolved.
        # Does NOT utilize a symbol table from a preprocessing parse phase, only looks in the current.
        nameparts = dottedname.split('.')
        if not nameparts[0]:
            nameparts = nameparts[1:]
        if len(nameparts) == 1:
            try:
                return self, self.symbols[nameparts[0]]
            except LookupError:
                if include_builtin_names:
                    if nameparts[0] in MATH_SYMBOLS:
                        return self, getattr(math, nameparts[0])
                    elif nameparts[0] in BUILTIN_SYMBOLS:
                        return self, getattr(builtins, nameparts[0])
                raise SymbolError("undefined symbol '{:s}'".format(nameparts[0])) from None
        # restart from global namespace:
        scope = self
        while scope.parent:
            scope = scope.parent
        for namepart in nameparts[:-1]:
            try:
                scope = scope.symbols[namepart]     # type: ignore
                assert scope.name == namepart
            except LookupError:
                raise SymbolError("undefined block '{:s}'".format(namepart)) from None
        if isinstance(scope, SymbolTable):
            return scope.lookup(nameparts[-1])
        elif isinstance(scope, SubroutineDef):
            return scope.sub_block.symbols.lookup(nameparts[-1])
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

    def as_eval_dict(self, ppsymbols: 'SymbolTable') -> Dict[str, Any]:     # @todo type
        # return a dictionary suitable to be passed as locals or globals to eval()
        if self.eval_dict is None:
            d = EvalSymbolDict(self, ppsymbols)
            self.eval_dict = d      # type: ignore
        return self.eval_dict

    def iter_variables(self) -> Iterable[VariableDef]:
        yield from sorted((v for v in self.symbols.values() if isinstance(v, VariableDef)))

    def iter_constants(self) -> Iterable[ConstantDef]:
        yield from sorted((v for v in self.symbols.values() if isinstance(v, ConstantDef)))

    def iter_subroutines(self, userdefined_only: bool=False) -> Iterable[SubroutineDef]:
        if userdefined_only:
            yield from sorted((sub for sub in self.symbols.values()
                               if isinstance(sub, SubroutineDef) and sub.address is None and sub.sub_block is not None))
        else:
            yield from sorted((sub for sub in self.symbols.values() if isinstance(sub, SubroutineDef)))

    def iter_labels(self) -> Iterable[LabelDef]:
        yield from sorted((v for v in self.symbols.values() if isinstance(v, LabelDef)))

    def check_identifier_valid(self, name: str, sourceref: SourceRef) -> None:
        if not name.isidentifier():
            raise SymbolError("invalid identifier")
        identifier = self.symbols.get(name, None)
        if identifier:
            if isinstance(identifier, SymbolDefinition):
                raise SymbolError("identifier was already defined at " + str(identifier.sourceref))
            elif isinstance(identifier, SymbolTable):
                raise SymbolError("identifier already defined as block at " + str(identifier.owning_block.sourceref))
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
        allocate = address is None
        if datatype == DataType.BYTE:
            if allocate and self.name == "ZP":
                try:
                    address = self._zeropage.allocate(name, datatype)
                except LookupError:
                    raise SymbolError("no space in ZP left for global 8-bit variable (try zp clobber)")
            self.symbols[name] = VariableDef(self.name, name, sourceref, DataType.BYTE, allocate,
                                             value=value, length=1, address=address)
        elif datatype == DataType.WORD:
            if allocate and self.name == "ZP":
                try:
                    address = self._zeropage.allocate(name, datatype)
                except LookupError:
                    raise SymbolError("no space in ZP left for global 16-bit word variable (try zp clobber)")
            self.symbols[name] = VariableDef(self.name, name, sourceref, DataType.WORD, allocate,
                                             value=value, length=1, address=address)
        elif datatype == DataType.FLOAT:
            if allocate and self.name == "ZP":
                try:
                    address = self._zeropage.allocate(name, datatype)
                except LookupError:
                    raise SymbolError("no space in ZP left for global 5-byte MFLT float variable (try zp clobber)")
            sourcecomment = "float " + str(value)
            self.symbols[name] = VariableDef(self.name, name, sourceref, DataType.FLOAT, allocate,
                                             value=value, length=1, address=address, sourcecomment=sourcecomment)
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
                   parameters: Sequence[Tuple[str, str]], returnvalues: Sequence[str],
                   address: Optional[int], sub_block: 'Block') -> None:
        self.check_identifier_valid(name, sourceref)
        self.symbols[name] = SubroutineDef(self.name, name, sourceref, parameters, returnvalues, address, sub_block)

    def discard_sub(self, name: str) -> None:
        sub = self.symbols[name]
        if isinstance(sub, SubroutineDef):
            del self.symbols[name]
        else:
            raise TypeError("not a subroutine")

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
        assert self.parent is None and other_root.parent is None
        for name, thing in other_root.symbols.items():
            if isinstance(thing, SymbolTable):
                try:
                    self.define_scope(thing, thing.owning_block.sourceref)
                except SymbolError as x:
                    raise SymbolError("problematic symbol '{:s}' from {}; {:s}"
                                      .format(thing.name, thing.owning_block.sourceref, str(x))) from None

    def print_table(self) -> None:
        def print_symbols(symbols: 'SymbolTable', level: int) -> None:
            indent = '\t' * level
            print("\n" + indent + "BLOCK:", symbols.name)
            for name, s in sorted(symbols.symbols.items(), key=lambda x: str(getattr(x[1], "sourceref", ""))):
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


class EvalSymbolDict(dict):
    def __init__(self, symboltable: SymbolTable, ppsymbols: SymbolTable, constant: bool=True) -> None:
        super().__init__()
        self._symboltable = symboltable
        self._ppsymbols = ppsymbols
        self._is_constant = constant

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
            try:
                scope, symbol = global_scope.lookup(name, True)
            except (LookupError, SymbolError):
                # try the ppsymbols
                if self._ppsymbols:
                    return self._ppsymbols.as_eval_dict(None)[name]
                raise SymbolError("undefined symbol '{:s}'".format(name)) from None
        if self._is_constant:
            if isinstance(symbol, ConstantDef):
                return symbol.value
            elif isinstance(symbol, VariableDef):
                raise SymbolError("can't reference a variable inside a (constant) expression")
            elif inspect.isbuiltin(symbol):
                return symbol
            elif isinstance(symbol, SymbolTable):
                return symbol.as_eval_dict(self._ppsymbols)
            elif isinstance(symbol, (LabelDef, SubroutineDef)):
                raise SymbolError("can't reference a label or subroutine inside a (constant) expression")
            else:
                raise SymbolError("invalid symbol type referenced " + repr(symbol))
        else:
            raise SymbolError("no support for non-constant expression evaluation yet")


def check_value_in_range(datatype: DataType, register: str, length: int, value: PrimitiveType) -> Optional[str]:
    if register:
        if register in REGISTER_BYTES:
            if value < 0 or value > 0xff:  # type: ignore
                return "value out of range, must be (unsigned) byte for a single register"
        elif register in REGISTER_SBITS:
            if value not in (0, 1):
                return "value out of range, must be 0 or 1 for a status bit register"
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


class AstNode:
    def __init__(self, sourceref: SourceRef) -> None:
        self.sourceref = sourceref.copy()

    @property
    def lineref(self) -> str:
        return "src l. " + str(self.sourceref.line)

    def __str__(self) -> str:
        def tostr(node: Any, level: int) -> str:
            indent = "   "
            clsname = node.__class__.__name__
            attrs = []
            try:
                nvars = vars(node)
            except TypeError:
                if type(node) is str:
                    sv = repr(node)
                    if len(sv) > 20:
                        sv = sv[:20] + "...'"
                    return sv
                return str(node)
            for name, value in nvars.items():
                if name == "sourceref":
                    continue
                elif type(value) in (str, int, float, bool, type(None)):
                    attrs.append((name, tostr(value, level+1)))
                elif type(value) is list:
                    strvalue = "["
                    strvalue += (",\n" + indent*(level+1)).join(tostr(v, level+1) for v in value) + "\n" + (1+level)*indent + "]"
                    attrs.append((name, strvalue))
                elif type(value) is not list and type(value) not in (str, int, float, bool, type(None)):
                    attrs.append((name, tostr(value, level+2)))
                else:
                    raise TypeError("WEIRD TYPE", type(value))
            attrstr = ("\n" + indent*(1+level)).join("{} = {}".format(name, sv) for name, sv in attrs)
            result = "\n" + indent * level + "<{0:s}  l={1:d}  c={2:d}".format(clsname, node.sourceref.line, node.sourceref.column)
            return result + "{} |end {} l={:d}|>".format(attrstr, clsname, node.sourceref.line)
        return tostr(self, 0)


class Block(AstNode):
    _unnamed_block_labels = {}  # type: Dict[Block, str]

    def __init__(self, name: str, sourceref: SourceRef, parent_scope: SymbolTable, preserve_registers: bool=False) -> None:
        super().__init__(sourceref)
        self.address = 0
        self.name = name
        self.statements = []  # type: List[AstNode]
        self.symbols = SymbolTable(name, parent_scope, self)
        self.preserve_registers = preserve_registers

    @property
    def ignore(self) -> bool:
        return not self.name and not self.address

    @property
    def label_names(self) -> Set[str]:
        return {symbol.name for symbol in self.symbols.iter_labels()}

    @property
    def label(self) -> str:
        if self.name:
            return self.name
        if self in self._unnamed_block_labels:
            return self._unnamed_block_labels[self]
        label = "il65_block_{:d}".format(len(self._unnamed_block_labels))
        self._unnamed_block_labels[self] = label
        return label

    def lookup(self, dottedname: str) -> Tuple[Optional['Block'], Optional[Union[SymbolDefinition, SymbolTable]]]:
        # Searches a name in the current block or globally, if the name is scoped (=contains a '.').
        # Does NOT utilize a symbol table from a preprocessing parse phase, only looks in the current.
        try:
            scope, result = self.symbols.lookup(dottedname)
            return scope.owning_block, result
        except (SymbolError, LookupError):
            return None, None

    def all_statements(self) -> Generator[Tuple['Block', Optional[SubroutineDef], AstNode], None, None]:
        for stmt in self.statements:
            yield self, None, stmt
        for sub in self.symbols.iter_subroutines(True):
            for stmt in sub.sub_block.statements:
                yield sub.sub_block, sub, stmt


class Value(AstNode):
    def __init__(self, datatype: DataType, sourceref: SourceRef, name: str = None, constant: bool = False) -> None:
        super().__init__(sourceref)
        self.datatype = datatype
        self.name = name
        self.constant = constant

    def assignable_from(self, other: 'Value') -> Tuple[bool, str]:
        if self.constant:
            return False, "cannot assign to a constant"
        return False, "incompatible value for assignment"


class IndirectValue(Value):
    # only constant integers, memmapped and register values are wrapped in this.
    def __init__(self, value: Value, type_modifier: DataType, sourceref: SourceRef) -> None:
        assert type_modifier
        super().__init__(type_modifier, sourceref, value.name, False)
        self.value = value

    def __str__(self):
        return "<IndirectValue {} itype={} name={}>".format(self.value, self.datatype, self.name)

    def __hash__(self):
        return hash((self.datatype, self.name, self.value))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, IndirectValue):
            return NotImplemented
        elif self is other:
            return True
        else:
            vvo = getattr(other.value, "value", getattr(other.value, "address", None))
            vvs = getattr(self.value, "value", getattr(self.value, "address", None))
            return (other.datatype, other.name, other.value.name, other.value.datatype, other.value.constant, vvo) == \
                   (self.datatype, self.name, self.value.name, self.value.datatype, self.value.constant, vvs)

    def assignable_from(self, other: Value) -> Tuple[bool, str]:
        if self.constant:
            return False, "cannot assign to a constant"
        if self.datatype == DataType.BYTE:
            if other.datatype == DataType.BYTE:
                return True, ""
        if self.datatype == DataType.WORD:
            if other.datatype in {DataType.BYTE, DataType.WORD} | STRING_DATATYPES:
                return True, ""
        if self.datatype == DataType.FLOAT:
            if other.datatype in {DataType.BYTE, DataType.WORD, DataType.FLOAT}:
                return True, ""
        if isinstance(other, (IntegerValue, FloatValue, StringValue)):
            rangefault = check_value_in_range(self.datatype, "", 1, other.value)
            if rangefault:
                return False, rangefault
            return True, ""
        return False, "incompatible value for indirect assignment (need byte, word, float or string)"


class IntegerValue(Value):
    def __init__(self, value: Optional[int], sourceref: SourceRef, *, datatype: DataType = None, name: str = None) -> None:
        if type(value) is int:
            if datatype is None:
                if 0 <= value < 0x100:
                    datatype = DataType.BYTE
                elif value < 0x10000:
                    datatype = DataType.WORD
                else:
                    raise OverflowError("value too big: ${:x}".format(value))
            else:
                faultreason = check_value_in_range(datatype, "", 1, value)
                if faultreason:
                    raise OverflowError(faultreason)
            super().__init__(datatype, sourceref, name, True)
            self.value = value
        elif value is None:
            if not name:
                raise ValueError("when integer value is not given, the name symbol should be speicified")
            super().__init__(datatype, sourceref, name, True)
            self.value = None
        else:
            raise TypeError("invalid data type")

    def __hash__(self):
        return hash((self.datatype, self.value, self.name))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, IntegerValue):
            return NotImplemented
        elif self is other:
            return True
        else:
            return (other.datatype, other.value, other.name) == (self.datatype, self.value, self.name)

    def __str__(self):
        return "<IntegerValue {} name={}>".format(self.value, self.name)

    def negative(self) -> 'IntegerValue':
        return IntegerValue(-self.value, self.sourceref, datatype=self.datatype, name=self.name)


class FloatValue(Value):
    def __init__(self, value: float, sourceref: SourceRef, name: str = None) -> None:
        if type(value) is float:
            super().__init__(DataType.FLOAT, sourceref, name, True)
            self.value = value
        else:
            raise TypeError("invalid data type")

    def __hash__(self):
        return hash((self.datatype, self.value, self.name))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, FloatValue):
            return NotImplemented
        elif self is other:
            return True
        else:
            return (other.datatype, other.value, other.name) == (self.datatype, self.value, self.name)

    def __str__(self):
        return "<FloatValue {} name={}>".format(self.value, self.name)

    def negative(self) -> 'FloatValue':
        return FloatValue(-self.value, self.sourceref, name=self.name)


class StringValue(Value):
    def __init__(self, value: str, sourceref: SourceRef, name: str = None, constant: bool = False) -> None:
        super().__init__(DataType.STRING, sourceref, name, constant)
        self.value = value

    def __hash__(self):
        return hash((self.datatype, self.value, self.name, self.constant))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, StringValue):
            return NotImplemented
        elif self is other:
            return True
        else:
            return (other.datatype, other.value, other.name, other.constant) == (self.datatype, self.value, self.name, self.constant)

    def __str__(self):
        return "<StringValue {!r:s} name={} constant={}>".format(self.value, self.name, self.constant)


class RegisterValue(Value):
    def __init__(self, register: str, datatype: DataType, sourceref: SourceRef, name: str = None) -> None:
        assert datatype in (DataType.BYTE, DataType.WORD)
        assert register in REGISTER_SYMBOLS
        super().__init__(datatype, sourceref, name, False)
        self.register = register

    def __hash__(self):
        return hash((self.datatype, self.register, self.name))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, RegisterValue):
            return NotImplemented
        elif self is other:
            return True
        else:
            return (other.datatype, other.register, other.name) == (self.datatype, self.register, self.name)

    def __str__(self):
        return "<RegisterValue {:s} type {:s} name={}>".format(self.register, self.datatype, self.name)

    def assignable_from(self, other: Value) -> Tuple[bool, str]:
        if isinstance(other, IndirectValue):
            if self.datatype == DataType.BYTE:
                if other.datatype == DataType.BYTE:
                    return True, ""
                return False, "(unsigned) byte required"
            if self.datatype == DataType.WORD:
                if other.datatype in (DataType.BYTE, DataType.WORD):
                    return True, ""
                return False, "(unsigned) byte required"
            return False, "incompatible indirect value for register assignment"
        if self.register in ("SC", "SI"):
            if isinstance(other, IntegerValue) and other.value in (0, 1):
                return True, ""
            return False, "can only assign an integer constant value of 0 or 1 to SC and SI"
        if self.constant:
            return False, "cannot assign to a constant"
        if isinstance(other, RegisterValue):
            if other.register in {"SI", "SC", "SZ"}:
                return False, "cannot explicitly assign from a status bit register alias"
            if len(self.register) < len(other.register):
                return False, "register size mismatch"
        if isinstance(other, StringValue) and self.register in REGISTER_BYTES | REGISTER_SBITS:
            return False, "string address requires 16 bits combined register"
        if isinstance(other, IntegerValue):
            if other.value is not None:
                range_error = check_value_in_range(self.datatype, self.register, 1, other.value)
                if range_error:
                    return False, range_error
                return True, ""
            if self.datatype == DataType.WORD:
                return True, ""
            return False, "cannot assign address to single register"
        if isinstance(other, FloatValue):
            range_error = check_value_in_range(self.datatype, self.register, 1, other.value)
            if range_error:
                return False, range_error
            return True, ""
        if self.datatype == DataType.BYTE:
            if other.datatype != DataType.BYTE:
                return False, "(unsigned) byte required"
            return True, ""
        if self.datatype == DataType.WORD:
            if other.datatype in (DataType.BYTE, DataType.WORD) or other.datatype in STRING_DATATYPES:
                return True, ""
            return False, "(unsigned) byte, word or string required"
        return False, "incompatible value for register assignment"


class MemMappedValue(Value):
    def __init__(self, address: Optional[int], datatype: DataType, length: int,
                 sourceref: SourceRef, name: str = None, constant: bool = False) -> None:
        super().__init__(datatype, sourceref, name, constant)
        self.address = address
        self.length = length
        assert address is None or type(address) is int

    def __hash__(self):
        return hash((self.datatype, self.address, self.length, self.name, self.constant))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, MemMappedValue):
            return NotImplemented
        elif self is other:
            return True
        else:
            return (other.datatype, other.address, other.length, other.name, other.constant) == \
                   (self.datatype, self.address, self.length, self.name, self.constant)

    def __str__(self):
        addr = "" if self.address is None else "${:04x}".format(self.address)
        return "<MemMappedValue {:s} type={:s} #={:d} name={} constant={}>" \
            .format(addr, self.datatype, self.length, self.name, self.constant)

    def assignable_from(self, other: Value) -> Tuple[bool, str]:
        if self.constant:
            return False, "cannot assign to a constant"
        if isinstance(other, IndirectValue):
            if self.datatype == other.datatype:
                return True, ""
            return False, "data type of value and target are not the same"
        if self.datatype == DataType.BYTE:
            if isinstance(other, (IntegerValue, RegisterValue, MemMappedValue)):
                if other.datatype == DataType.BYTE:
                    return True, ""
                return False, "(unsigned) byte required"
            elif isinstance(other, FloatValue):
                range_error = check_value_in_range(self.datatype, "", 1, other.value)
                if range_error:
                    return False, range_error
                return True, ""
            else:
                return False, "(unsigned) byte required"
        elif self.datatype in (DataType.WORD, DataType.FLOAT):
            if isinstance(other, (IntegerValue, FloatValue)):
                range_error = check_value_in_range(self.datatype, "", 1, other.value)
                if range_error:
                    return False, range_error
                return True, ""
            elif isinstance(other, (RegisterValue, MemMappedValue)):
                if other.datatype in (DataType.BYTE, DataType.WORD, DataType.FLOAT):
                    return True, ""
                else:
                    return False, "byte or word or float required"
            elif isinstance(other, StringValue):
                if self.datatype == DataType.WORD:
                    return True, ""
                return False, "string address requires 16 bits (a word)"
            if self.datatype == DataType.BYTE:
                return False, "(unsigned) byte required"
            if self.datatype == DataType.WORD:
                return False, "(unsigned) word required"
        return False, "incompatible value for assignment"


class Comment(AstNode):
    def __init__(self, text: str, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.text = text


class Label(AstNode):
    def __init__(self, name: str, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.name = name


class AssignmentStmt(AstNode):
    def __init__(self, leftvalues: List[Value], right: Value, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.leftvalues = leftvalues
        self.right = right

    def __str__(self):
        return "<Assign {:s} to {:s}>".format(str(self.right), ",".join(str(lv) for lv in self.leftvalues))

    def remove_identity_lvalues(self) -> None:
        for lv in self.leftvalues:
            if lv == self.right:
                print("{}: removed identity assignment".format(self.sourceref))
        remaining_leftvalues = [lv for lv in self.leftvalues if lv != self.right]
        self.leftvalues = remaining_leftvalues

    def is_identity(self) -> bool:
        return all(lv == self.right for lv in self.leftvalues)


class AugmentedAssignmentStmt(AssignmentStmt):
    SUPPORTED_OPERATORS = {"+=", "-=", "&=", "|=", "^=", ">>=", "<<="}

    # full set: {"+=", "-=", "*=", "/=", "%=", "//=", "**=", "&=", "|=", "^=", ">>=", "<<="}

    def __init__(self, left: Value, operator: str, right: Value, sourceref: SourceRef) -> None:
        assert operator in self.SUPPORTED_OPERATORS
        super().__init__([left], right, sourceref)
        self.operator = operator

    def __str__(self):
        return "<AugAssign {:s} {:s} {:s}>".format(str(self.leftvalues[0]), self.operator, str(self.right))


class ReturnStmt(AstNode):
    def __init__(self, sourceref: SourceRef, a: Optional[Value] = None,
                 x: Optional[Value] = None,
                 y: Optional[Value] = None) -> None:
        super().__init__(sourceref)
        self.a = a
        self.x = x
        self.y = y


class InplaceIncrStmt(AstNode):
    def __init__(self, what: Value, value: Union[IntegerValue, FloatValue], sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        assert value.constant
        assert (value.value is None and value.name) or value.value > 0
        self.what = what
        self.value = value


class InplaceDecrStmt(AstNode):
    def __init__(self, what: Value, value: Union[IntegerValue, FloatValue], sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        assert value.constant
        assert (value.value is None and value.name) or value.value > 0
        self.what = what
        self.value = value


class IfCondition(AstNode):
    SWAPPED_OPERATOR = {"==": "==",
                        "!=": "!=",
                        "<=": ">=",
                        ">=": "<=",
                        "<": ">",
                        ">": "<"}
    IF_STATUSES = {"cc", "cs", "vc", "vs", "eq", "ne", "true", "not", "zero", "pos", "neg", "lt", "gt", "le", "ge"}

    def __init__(self, ifstatus: str, leftvalue: Optional[Value],
                 operator: str, rightvalue: Optional[Value], sourceref: SourceRef) -> None:
        assert ifstatus in self.IF_STATUSES
        assert operator in (None, "") or operator in self.SWAPPED_OPERATOR
        if operator:
            assert ifstatus in ("true", "not", "zero")
        super().__init__(sourceref)
        self.ifstatus = ifstatus
        self.lvalue = leftvalue
        self.comparison_op = operator
        self.rvalue = rightvalue

    def __str__(self):
        return "<IfCondition if_{:s} {} {:s} {}>".format(self.ifstatus, self.lvalue, self.comparison_op, self.rvalue)

    def make_if_true(self) -> bool:
        # makes a condition of the form if_not a < b  into: if a > b (gets rid of the not)
        # returns whether the change was made or not
        if self.ifstatus == "not" and self.comparison_op:
            self.ifstatus = "true"
            self.comparison_op = self.SWAPPED_OPERATOR[self.comparison_op]
            return True
        return False

    def swap(self) -> Tuple[Value, str, Value]:
        self.lvalue, self.comparison_op, self.rvalue = self.rvalue, self.SWAPPED_OPERATOR[self.comparison_op], self.lvalue
        return self.lvalue, self.comparison_op, self.rvalue


class CallStmt(AstNode):
    def __init__(self, sourceref: SourceRef, target: Optional[Value] = None, *,
                 address: Optional[int] = None, arguments: List[Tuple[str, Any]] = None,
                 outputs: List[Tuple[str, Value]] = None, is_goto: bool = False,
                 preserve_regs: Set[str] = None, condition: IfCondition = None) -> None:
        if not is_goto:
            assert condition is None
        super().__init__(sourceref)
        self.target = target
        self.address = address
        self.arguments = arguments
        self.outputvars = outputs
        self.is_goto = is_goto
        self.condition = condition
        self.preserve_regs = preserve_regs
        self.desugared_call_arguments = []  # type: List[AssignmentStmt]
        self.desugared_output_assignments = []  # type: List[AssignmentStmt]


class InlineAsm(AstNode):
    def __init__(self, asmlines: List[str], sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.asmlines = asmlines


class BreakpointStmt(AstNode):
    def __init__(self, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
