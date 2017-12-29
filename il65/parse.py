"""
Programming Language for 6502/6510 microprocessors
This is the parser of the IL65 code, that generates a parse tree.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

import math
import re
import os
import sys
import shutil
import enum
from collections import defaultdict
from typing import Set, List, Tuple, Optional, Any, Dict, Union, Generator
from .astparse import ParseError, parse_expr_as_int, parse_expr_as_number, parse_expr_as_primitive,\
    parse_expr_as_string, parse_arguments, parse_expr_as_comparison
from .symbols import SourceRef, SymbolTable, DataType, SymbolDefinition, SubroutineDef, LabelDef, \
    Zeropage, check_value_in_range, char_to_bytevalue, \
    PrimitiveType, VariableDef, ConstantDef, SymbolError, STRING_DATATYPES, \
    REGISTER_SYMBOLS, REGISTER_WORDS, REGISTER_BYTES, REGISTER_SBITS, RESERVED_NAMES


class ProgramFormat(enum.Enum):
    PRG = "prg"
    RAW = "raw"


class ParseResult:
    def __init__(self, sourcefile: str) -> None:
        self.format = ProgramFormat.RAW
        self.with_sys = False
        self.sourcefile = sourcefile
        self.clobberzp = False
        self.restorezp = False
        self.start_address = 0
        self.blocks = []          # type: List['ParseResult.Block']
        self.subroutine_usage = defaultdict(set)    # type: Dict[Tuple[str, str], Set[str]]
        self.zeropage = Zeropage()

    def all_blocks(self) -> Generator['ParseResult.Block', None, None]:
        for block in self.blocks:
            yield block
            for sub in block.symbols.iter_subroutines(True):
                yield sub.sub_block

    class Block:
        _unnamed_block_labels = {}  # type: Dict[ParseResult.Block, str]

        def __init__(self, name: str, sourceref: SourceRef, parent_scope: SymbolTable) -> None:
            self.sourceref = sourceref.copy()
            self.address = 0
            self.name = name
            self.statements = []    # type: List[ParseResult._AstNode]
            self.symbols = SymbolTable(name, parent_scope, self)

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

        def lookup(self, dottedname: str) -> Tuple[Optional['ParseResult.Block'], Optional[Union[SymbolDefinition, SymbolTable]]]:
            # Searches a name in the current block or globally, if the name is scoped (=contains a '.').
            # Does NOT utilize a symbol table from a preprocessing parse phase, only looks in the current.
            try:
                scope, result = self.symbols.lookup(dottedname)
                return scope.owning_block, result
            except (SymbolError, LookupError):
                return None, None

        def all_statements(self) -> Generator[Tuple['ParseResult.Block', Optional[SubroutineDef], 'ParseResult._AstNode'], None, None]:
            for stmt in self.statements:
                yield self, None, stmt
            for sub in self.symbols.iter_subroutines(True):
                for stmt in sub.sub_block.statements:
                    yield sub.sub_block, sub, stmt

    class Value:
        def __init__(self, datatype: DataType, name: str=None, constant: bool=False) -> None:
            self.datatype = datatype
            self.name = name
            self.constant = constant

        def assignable_from(self, other: 'ParseResult.Value') -> Tuple[bool, str]:
            if self.constant:
                return False, "cannot assign to a constant"
            return False, "incompatible value for assignment"

    class IndirectValue(Value):
        # only constant integers, memmapped and register values are wrapped in this.
        def __init__(self, value: 'ParseResult.Value', type_modifier: DataType) -> None:
            assert type_modifier
            super().__init__(type_modifier, value.name, False)
            self.value = value

        def __str__(self):
            return "<IndirectValue {} itype={} name={}>".format(self.value, self.datatype, self.name)

        def __hash__(self):
            return hash((self.datatype, self.name, self.value))

        def __eq__(self, other: Any) -> bool:
            if not isinstance(other, ParseResult.IndirectValue):
                return NotImplemented
            elif self is other:
                return True
            else:
                vvo = getattr(other.value, "value", getattr(other.value, "address", None))
                vvs = getattr(self.value, "value", getattr(self.value, "address", None))
                return (other.datatype, other.name, other.value.name, other.value.datatype, other.value.constant, vvo) ==\
                       (self.datatype, self.name, self.value.name, self.value.datatype, self.value.constant, vvs)

        def assignable_from(self, other: 'ParseResult.Value') -> Tuple[bool, str]:
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
            if isinstance(other, (ParseResult.IntegerValue, ParseResult.FloatValue, ParseResult.StringValue)):
                rangefault = check_value_in_range(self.datatype, "", 1, other.value)
                if rangefault:
                    return False, rangefault
                return True, ""
            return False, "incompatible value for indirect assignment (need byte, word, float or string)"

    class IntegerValue(Value):
        def __init__(self, value: Optional[int], *, datatype: DataType=None, name: str=None) -> None:
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
                super().__init__(datatype, name, True)
                self.value = value
            elif value is None:
                if not name:
                    raise ValueError("when integer value is not given, the name symbol should be speicified")
                super().__init__(datatype, name, True)
                self.value = None
            else:
                raise TypeError("invalid data type")

        def __hash__(self):
            return hash((self.datatype, self.value, self.name))

        def __eq__(self, other: Any) -> bool:
            if not isinstance(other, ParseResult.IntegerValue):
                return NotImplemented
            elif self is other:
                return True
            else:
                return (other.datatype, other.value, other.name) == (self.datatype, self.value, self.name)

        def __str__(self):
            return "<IntegerValue {} name={}>".format(self.value, self.name)

    class FloatValue(Value):
        def __init__(self, value: float, name: str=None) -> None:
            if type(value) is float:
                super().__init__(DataType.FLOAT, name, True)
                self.value = value
            else:
                raise TypeError("invalid data type")

        def __hash__(self):
            return hash((self.datatype, self.value, self.name))

        def __eq__(self, other: Any) -> bool:
            if not isinstance(other, ParseResult.FloatValue):
                return NotImplemented
            elif self is other:
                return True
            else:
                return (other.datatype, other.value, other.name) == (self.datatype, self.value, self.name)

        def __str__(self):
            return "<FloatValue {} name={}>".format(self.value, self.name)

    class StringValue(Value):
        def __init__(self, value: str, name: str=None, constant: bool=False) -> None:
            super().__init__(DataType.STRING, name, constant)
            self.value = value

        def __hash__(self):
            return hash((self.datatype, self.value, self.name, self.constant))

        def __eq__(self, other: Any) -> bool:
            if not isinstance(other, ParseResult.StringValue):
                return NotImplemented
            elif self is other:
                return True
            else:
                return (other.datatype, other.value, other.name, other.constant) == (self.datatype, self.value, self.name, self.constant)

        def __str__(self):
            return "<StringValue {!r:s} name={} constant={}>".format(self.value, self.name, self.constant)

    class RegisterValue(Value):
        def __init__(self, register: str, datatype: DataType, name: str=None) -> None:
            assert datatype in (DataType.BYTE, DataType.WORD)
            assert register in REGISTER_SYMBOLS
            super().__init__(datatype, name, False)
            self.register = register

        def __hash__(self):
            return hash((self.datatype, self.register, self.name))

        def __eq__(self, other: Any) -> bool:
            if not isinstance(other, ParseResult.RegisterValue):
                return NotImplemented
            elif self is other:
                return True
            else:
                return (other.datatype, other.register, other.name) == (self.datatype, self.register, self.name)

        def __str__(self):
            return "<RegisterValue {:s} type {:s} name={}>".format(self.register, self.datatype, self.name)

        def assignable_from(self, other: 'ParseResult.Value') -> Tuple[bool, str]:
            if isinstance(other, ParseResult.IndirectValue):
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
                if isinstance(other, ParseResult.IntegerValue) and other.value in (0, 1):
                    return True, ""
                return False, "can only assign an integer constant value of 0 or 1 to SC and SI"
            if self.constant:
                return False, "cannot assign to a constant"
            if isinstance(other, ParseResult.RegisterValue):
                if other.register in {"SI", "SC", "SZ"}:
                    return False, "cannot explicitly assign from a status bit register alias"
                if len(self.register) < len(other.register):
                    return False, "register size mismatch"
            if isinstance(other, ParseResult.StringValue) and self.register in REGISTER_BYTES | REGISTER_SBITS:
                return False, "string address requires 16 bits combined register"
            if isinstance(other, ParseResult.IntegerValue):
                if other.value is not None:
                    range_error = check_value_in_range(self.datatype, self.register, 1, other.value)
                    if range_error:
                        return False, range_error
                    return True, ""
                if self.datatype == DataType.WORD:
                    return True, ""
                return False, "cannot assign address to single register"
            if isinstance(other, ParseResult.FloatValue):
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
        def __init__(self, address: Optional[int], datatype: DataType, length: int, name: str=None, constant: bool=False) -> None:
            super().__init__(datatype, name, constant)
            self.address = address
            self.length = length
            assert address is None or type(address) is int

        def __hash__(self):
            return hash((self.datatype, self.address, self.length, self.name, self.constant))

        def __eq__(self, other: Any) -> bool:
            if not isinstance(other, ParseResult.MemMappedValue):
                return NotImplemented
            elif self is other:
                return True
            else:
                return (other.datatype, other.address, other.length, other.name, other.constant) ==\
                       (self.datatype, self.address, self.length, self.name, self.constant)

        def __str__(self):
            addr = "" if self.address is None else "${:04x}".format(self.address)
            return "<MemMappedValue {:s} type={:s} #={:d} name={} constant={}>"\
                .format(addr, self.datatype, self.length, self.name, self.constant)

        def assignable_from(self, other: 'ParseResult.Value') -> Tuple[bool, str]:
            if self.constant:
                return False, "cannot assign to a constant"
            if isinstance(other, ParseResult.IndirectValue):
                return False, "can not yet assign memory mapped value from indirect value"  # @todo indirect v assign
            if self.datatype == DataType.BYTE:
                if isinstance(other, (ParseResult.IntegerValue, ParseResult.RegisterValue, ParseResult.MemMappedValue)):
                    if other.datatype == DataType.BYTE:
                        return True, ""
                    return False, "(unsigned) byte required"
                elif isinstance(other, ParseResult.FloatValue):
                    range_error = check_value_in_range(self.datatype, "", 1, other.value)
                    if range_error:
                        return False, range_error
                    return True, ""
                else:
                    return False, "(unsigned) byte required"
            elif self.datatype in (DataType.WORD, DataType.FLOAT):
                if isinstance(other, (ParseResult.IntegerValue, ParseResult.FloatValue)):
                    range_error = check_value_in_range(self.datatype, "", 1, other.value)
                    if range_error:
                        return False, range_error
                    return True, ""
                elif isinstance(other, (ParseResult.RegisterValue, ParseResult.MemMappedValue)):
                    if other.datatype in (DataType.BYTE, DataType.WORD, DataType.FLOAT):
                        return True, ""
                    else:
                        return False, "byte or word or float required"
                elif isinstance(other, ParseResult.StringValue):
                    if self.datatype == DataType.WORD:
                        return True, ""
                    return False, "string address requires 16 bits (a word)"
                if self.datatype == DataType.BYTE:
                    return False, "(unsigned) byte required"
                if self.datatype == DataType.WORD:
                    return False, "(unsigned) word required"
            return False, "incompatible value for assignment"

    class _AstNode:     # @todo merge Value with this?
        def __init__(self, lineno: int) -> None:
            self.lineno = lineno

    class Comment(_AstNode):
        def __init__(self, text: str, lineno: int) -> None:
            super().__init__(lineno)
            self.text = text

    class Label(_AstNode):
        def __init__(self, name: str, lineno: int) -> None:
            super().__init__(lineno)
            self.name = name

    class AssignmentStmt(_AstNode):
        def __init__(self, leftvalues: List['ParseResult.Value'], right: 'ParseResult.Value', lineno: int) -> None:
            super().__init__(lineno)
            self.leftvalues = leftvalues
            self.right = right

        def __str__(self):
            return "<Assign {:s} to {:s}>".format(str(self.right), ",".join(str(lv) for lv in self.leftvalues))

        _immediate_string_vars = {}   # type: Dict[str, Tuple[str, str]]

        def desugar_immediate_string(self, parser: 'Parser') -> None:
            if self.right.name or not isinstance(self.right, ParseResult.StringValue):
                return
            if self.right.value in self._immediate_string_vars:
                blockname, stringvar_name = self._immediate_string_vars[self.right.value]
                if blockname:
                    self.right.name = blockname + '.' + stringvar_name
                else:
                    self.right.name = stringvar_name
            else:
                cur_block = parser.cur_block
                stringvar_name = "il65_str_{:d}".format(id(self))
                value = self.right.value
                cur_block.symbols.define_variable(stringvar_name, cur_block.sourceref, DataType.STRING, value=value)
                self.right.name = stringvar_name
                self._immediate_string_vars[self.right.value] = (cur_block.name, stringvar_name)

        def remove_identity_lvalues(self, filename: str, lineno: int) -> None:
            for lv in self.leftvalues:
                if lv == self.right:
                    print("{:s}:{:d}: removed identity assignment".format(filename, lineno))
            remaining_leftvalues = [lv for lv in self.leftvalues if lv != self.right]
            self.leftvalues = remaining_leftvalues

        def is_identity(self) -> bool:
            return all(lv == self.right for lv in self.leftvalues)

    class AugmentedAssignmentStmt(AssignmentStmt):
        SUPPORTED_OPERATORS = {"+=", "-=", "&=", "|=", "^=", ">>=", "<<="}
        # full set: {"+=", "-=", "*=", "/=", "%=", "//=", "**=", "&=", "|=", "^=", ">>=", "<<="}

        def __init__(self, left: 'ParseResult.Value', operator: str, right: 'ParseResult.Value', lineno: int) -> None:
            assert operator in self.SUPPORTED_OPERATORS
            super().__init__([left], right, lineno)
            self.operator = operator

        def __str__(self):
            return "<AugAssign {:s} {:s} {:s}>".format(str(self.leftvalues[0]), self.operator, str(self.right))

    class ReturnStmt(_AstNode):
        def __init__(self, lineno: int, a: Optional['ParseResult.Value']=None,
                     x: Optional['ParseResult.Value']=None,
                     y: Optional['ParseResult.Value']=None) -> None:
            super().__init__(lineno)
            self.a = a
            self.x = x
            self.y = y

    class InplaceIncrStmt(_AstNode):
        def __init__(self, what: 'ParseResult.Value', howmuch: Union[int, float], lineno: int) -> None:
            super().__init__(lineno)
            assert howmuch > 0
            self.what = what
            self.howmuch = howmuch

    class InplaceDecrStmt(_AstNode):
        def __init__(self, what: 'ParseResult.Value', howmuch: Union[int, float], lineno: int) -> None:
            super().__init__(lineno)
            assert howmuch > 0
            self.what = what
            self.howmuch = howmuch

    class CallStmt(_AstNode):
        def __init__(self, lineno: int, target: Optional['ParseResult.Value']=None, *,
                     address: Optional[int]=None, arguments: List[Tuple[str, Any]]=None,
                     outputs: List[Tuple[str, 'ParseResult.Value']]=None, is_goto: bool=False,
                     preserve_regs: bool=True, condition: 'ParseResult.IfCondition'=None) -> None:
            if not is_goto:
                assert condition is None
            super().__init__(lineno)
            self.target = target
            self.address = address
            self.arguments = arguments
            self.outputvars = outputs
            self.is_goto = is_goto
            self.condition = condition
            self.preserve_regs = preserve_regs
            self.desugared_call_arguments = []  # type: List[ParseResult.AssignmentStmt]
            self.desugared_output_assignments = []   # type: List[ParseResult.AssignmentStmt]

        def desugar_call_arguments_and_outputs(self, parser: 'Parser') -> None:
            self.desugared_call_arguments.clear()
            self.desugared_output_assignments.clear()
            for name, value in self.arguments or []:
                assert name is not None, "all call arguments should have a name or be matched on a named parameter"
                assignment = parser.parse_assignment(name, value)
                if assignment.leftvalues[0].datatype != DataType.BYTE:
                    if isinstance(assignment.right, ParseResult.IntegerValue) and assignment.right.constant:
                        # a call that doesn't expect a BYTE argument but gets one, converted from a 1-byte string most likely
                        if value.startswith("'") and value.endswith("'"):
                            parser.print_warning("possible problematic string to byte conversion (use a .text var instead?)")
                if not assignment.is_identity():
                    assignment.lineno = self.lineno
                    self.desugared_call_arguments.append(assignment)
            for register, value in self.outputvars or []:
                rvalue = parser.parse_expression(register)
                assignment = ParseResult.AssignmentStmt([value], rvalue, self.lineno)
                # note: we need the identity assignment here or the output register handling generates buggy code
                assignment.lineno = self.lineno
                self.desugared_output_assignments.append(assignment)

    class InlineAsm(_AstNode):
        def __init__(self, asmlines: List[str], lineno: int) -> None:
            super().__init__(lineno)
            self.asmlines = asmlines

    class IfCondition(_AstNode):
        SWAPPED_OPERATOR = {"==": "==",
                            "!=": "!=",
                            "<=": ">=",
                            ">=": "<=",
                            "<": ">",
                            ">": "<"}
        IF_STATUSES = {"cc", "cs", "vc", "vs", "eq", "ne", "true", "not", "zero", "lt", "gt", "le", "ge"}

        def __init__(self, ifstatus: str, leftvalue: Optional['ParseResult.Value'],
                     operator: str, rightvalue: Optional['ParseResult.Value'], lineno: int) -> None:
            assert ifstatus in self.IF_STATUSES
            assert operator in (None, "") or operator in self.SWAPPED_OPERATOR
            if operator:
                assert ifstatus in ("true", "not", "zero")
            super().__init__(lineno)
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

        def swap(self) -> Tuple['ParseResult.Value', str, 'ParseResult.Value']:
            self.lvalue, self.comparison_op, self.rvalue = self.rvalue, self.SWAPPED_OPERATOR[self.comparison_op], self.lvalue
            return self.lvalue, self.comparison_op, self.rvalue

    class BreakpointStmt(_AstNode):
        def __init__(self, lineno: int) -> None:
            super().__init__(lineno)

    def add_block(self, block: 'ParseResult.Block', position: Optional[int]=None) -> None:
        if position is not None:
            self.blocks.insert(position, block)
        else:
            self.blocks.append(block)

    def merge(self, parsed: 'ParseResult') -> None:
        existing_blocknames = set(block.name for block in self.blocks)
        other_blocknames = set(block.name for block in parsed.blocks)
        overlap = existing_blocknames & other_blocknames
        if overlap != {"<header>"}:
            raise SymbolError("double block names: {}".format(overlap))
        for block in parsed.blocks:
            if block.name != "<header>":
                self.blocks.append(block)

    def find_block(self, name: str) -> Block:
        for block in self.blocks:
            if block.name == name:
                return block
        raise KeyError("block not found: " + name)

    def sub_used_by(self, sub: SubroutineDef, sourceref: SourceRef) -> None:
        self.subroutine_usage[(sub.blockname, sub.name)].add(str(sourceref))


class Parser:
    def __init__(self, filename: str, outputdir: str, sourcelines: List[Tuple[int, str]] = None, parsing_import: bool = False,
                 ppsymbols: SymbolTable = None, sub_usage: Dict=None) -> None:
        self.result = ParseResult(filename)
        if sub_usage is not None:
            # re-use the (global) subroutine usage tracking
            self.result.subroutine_usage = sub_usage
        self.sourceref = SourceRef(filename, -1, 0)
        if sourcelines:
            self.lines = sourcelines
        else:
            self.lines = self.load_source(filename)
        self.outputdir = outputdir
        self.parsing_import = parsing_import     # are we parsing a import file?
        self._cur_lineidx = -1      # used to efficiently go to next/previous line in source
        self.cur_block = None  # type: ParseResult.Block
        self.root_scope = SymbolTable("<root>", None, None)
        self.root_scope.set_zeropage(self.result.zeropage)
        self.ppsymbols = ppsymbols  # symboltable from preprocess phase
        self.print_block_parsing = True

    def load_source(self, filename: str) -> List[Tuple[int, str]]:
        with open(filename, "rU") as source:
            sourcelines = source.readlines()
        # store all lines that aren't empty
        # comments are kept (end-of-line comments are stripped though)
        lines = []
        for num, line in enumerate(sourcelines, start=1):
            line = line.rstrip()
            if line.lstrip().startswith(';'):
                lines.append((num, line.lstrip()))
            else:
                line2, sep, comment = line.rpartition(';')
                if sep:
                    line = line2.rstrip()
                if line:
                    lines.append((num, line))
        return lines

    def parse(self) -> Optional[ParseResult]:
        # start the parsing
        try:
            return self.parse_file()
        except ParseError as x:
            if sys.stderr.isatty():
                print("\x1b[1m", file=sys.stderr)
            print("", file=sys.stderr)
            if x.sourcetext:
                print("\tsource text: '{:s}'".format(x.sourcetext), file=sys.stderr)
                if x.sourceref.column:
                    print("\t" + ' '*x.sourceref.column + '             ^', file=sys.stderr)
            if self.parsing_import:
                print("Error (in imported file):", str(x), file=sys.stderr)
            else:
                print("Error:", str(x), file=sys.stderr)
            if sys.stderr.isatty():
                print("\x1b[0m", file=sys.stderr)
            raise   # XXX temporary solution to get stack trace info in the event of parse errors
        except Exception as x:
            if sys.stderr.isatty():
                print("\x1b[1m", file=sys.stderr)
            print("\nERROR: internal parser error: ", x, file=sys.stderr)
            if self.cur_block:
                print("    file:", self.sourceref.file, "block:", self.cur_block.name, "line:", self.sourceref.line, file=sys.stderr)
            else:
                print("    file:", self.sourceref.file, file=sys.stderr)
            if sys.stderr.isatty():
                print("\x1b[0m", file=sys.stderr)
            raise   # XXX temporary solution to get stack trace info in the event of parse errors

    def parse_file(self) -> ParseResult:
        print("\nparsing", self.sourceref.file)
        self._parse_1()
        self._parse_2()
        return self.result

    def print_warning(self, text: str, sourceref: SourceRef=None) -> None:
        self.print_bold("warning: {}: {:s}".format(sourceref or self.sourceref, text))

    def print_bold(self, text: str) -> None:
        if sys.stdout.isatty():
            print("\x1b[1m" + text + "\x1b[0m")
        else:
            print(text)

    def _parse_comments(self) -> None:
        while True:
            line = self.next_line().lstrip()
            if line.startswith(';'):
                self.cur_block.statements.append(ParseResult.Comment(line, self.sourceref.line))
                continue
            self.prev_line()
            break

    def _parse_1(self) -> None:
        self.cur_block = ParseResult.Block("<header>", self.sourceref, self.root_scope)
        self.result.add_block(self.cur_block)
        self.parse_header()
        if not self.parsing_import:
            self.result.zeropage.configure(self.result.clobberzp)
        while True:
            self._parse_comments()
            next_line = self.peek_next_line().lstrip()
            if next_line.startswith("~"):
                block = self.parse_block()
                if block:
                    self.result.add_block(block)
            elif next_line.startswith("import"):
                self.parse_import()
            else:
                break
        line = self.next_line()
        if line:
            raise self.PError("invalid statement or characters, block expected")
        if not self.parsing_import:
            # check if we have a proper main block to contain the program's entry point
            main_found = False
            for block in self.result.blocks:
                if block.name == "main":
                    main_found = True
                    if "start" not in block.label_names:
                        self.sourceref.line = block.sourceref.line
                        self.sourceref.column = 0
                        raise self.PError("block 'main' should contain the program entry point 'start'")
                    self._check_return_statement(block, "'main' block")
                for sub in block.symbols.iter_subroutines(True):
                    self._check_return_statement(sub.sub_block, "subroutine '{:s}'".format(sub.name))
            if not main_found:
                raise self.PError("a block 'main' should be defined and contain the program's entry point label 'start'")

    def _check_return_statement(self, block: ParseResult.Block, message: str) -> None:
        # find last statement that isn't a comment
        for stmt in reversed(block.statements):
            if isinstance(stmt, ParseResult.Comment):
                continue
            if isinstance(stmt, ParseResult.ReturnStmt) or isinstance(stmt, ParseResult.CallStmt) and stmt.is_goto:
                return
            if isinstance(stmt, ParseResult.InlineAsm):
                # check that the last asm line is a jmp or a rts
                for asmline in reversed(stmt.asmlines):
                    if asmline.lstrip().startswith(';'):
                        continue
                    if " rts" in asmline or "\trts" in asmline or " jmp" in asmline or "\tjmp" in asmline:
                        return
                    if asmline.strip():
                        if asmline.split()[0].isidentifier():
                            continue
                    break
            break
        self.print_warning("{:s} doesn't end with a return statement".format(message), block.sourceref)

    def _parse_2(self) -> None:
        # parsing pass 2 (not done during preprocessing!)
        self.cur_block = None
        self.sourceref.line = -1
        self.sourceref.column = 0

        def desugar_immediate_strings(stmt: ParseResult._AstNode) -> None:
            if isinstance(stmt, ParseResult.CallStmt):
                for s in stmt.desugared_call_arguments:
                    self.sourceref.line = s.lineno
                    self.sourceref.column = 0
                    s.desugar_immediate_string(self)
                for s in stmt.desugared_output_assignments:
                    self.sourceref.line = s.lineno
                    self.sourceref.column = 0
                    s.desugar_immediate_string(self)
            if isinstance(stmt, ParseResult.AssignmentStmt):
                self.sourceref.line = stmt.lineno
                self.sourceref.column = 0
                stmt.desugar_immediate_string(self)

        for block in self.result.blocks:
            self.cur_block = block
            self.sourceref = block.sourceref.copy()
            self.sourceref.column = 0
            for block, sub, stmt in block.all_statements():
                if isinstance(stmt, ParseResult.CallStmt):
                    self.sourceref.line = stmt.lineno
                    stmt.desugar_call_arguments_and_outputs(self)
                desugar_immediate_strings(stmt)

    def next_line(self) -> str:
        self._cur_lineidx += 1
        try:
            self.sourceref.line, line = self.lines[self._cur_lineidx]
            self.sourceref.column = 0
            return line
        except IndexError:
            return ""

    def prev_line(self) -> str:
        self._cur_lineidx -= 1
        self.sourceref.line, line = self.lines[self._cur_lineidx]
        return line

    def peek_next_line(self) -> str:
        if (self._cur_lineidx + 1) < len(self.lines):
            return self.lines[self._cur_lineidx + 1][1]
        return ""

    def PError(self, message: str, lineno: int=0, column: int=0) -> ParseError:
        sourceline = ""
        lineno = lineno or self.sourceref.line
        column = column or self.sourceref.column
        for num, text in self.lines:
            if num == lineno:
                sourceline = text.strip()
                break
        return ParseError(message, sourceline, SourceRef(self.sourceref.file, lineno, column))

    def get_datatype(self, typestr: str) -> Tuple[DataType, int, Optional[Tuple[int, int]]]:
        if typestr == ".byte":
            return DataType.BYTE, 1, None
        elif typestr == ".word":
            return DataType.WORD, 1, None
        elif typestr == ".float":
            return DataType.FLOAT, 1, None
        elif typestr.endswith("text"):
            if typestr == ".text":
                return DataType.STRING, 0, None
            elif typestr == ".ptext":
                return DataType.STRING_P, 0, None
            elif typestr == ".stext":
                return DataType.STRING_S, 0, None
            elif typestr == ".pstext":
                return DataType.STRING_PS, 0, None
        elif typestr.startswith(".array(") and typestr.endswith(")"):
            return DataType.BYTEARRAY, self._size_from_arraydecl(typestr), None
        elif typestr.startswith(".wordarray(") and typestr.endswith(")"):
            return DataType.WORDARRAY, self._size_from_arraydecl(typestr), None
        elif typestr.startswith(".matrix(") and typestr.endswith(")"):
            dimensions = self._size_from_matrixdecl(typestr)
            return DataType.MATRIX, dimensions[0] * dimensions[1], dimensions
        raise self.PError("invalid data type: " + typestr)

    def parse_header(self) -> None:
        self.result.with_sys = False
        self.result.format = ProgramFormat.RAW
        output_specified = False
        zp_specified = False
        while True:
            self._parse_comments()
            line = self.next_line()
            if line.startswith(("output ", "output\t")):
                if output_specified:
                    raise self.PError("can only specify output options once")
                output_specified = True
                _, _, optionstr = line.partition(" ")
                options = set(optionstr.replace(' ', '').split(','))
                self.result.with_sys = False
                self.result.format = ProgramFormat.RAW
                if "raw" in options:
                    options.remove("raw")
                if "prg" in options:
                    options.remove("prg")
                    self.result.format = ProgramFormat.PRG
                if "basic" in options:
                    options.remove("basic")
                    if self.result.format == ProgramFormat.PRG:
                        self.result.with_sys = True
                    else:
                        raise self.PError("can only use basic output option with prg, not raw")
                if options:
                    raise self.PError("invalid output option(s): " + str(options))
            elif line.startswith(("zp ", "zp\t")):
                if zp_specified:
                    raise self.PError("can only specify ZP options once")
                zp_specified = True
                _, _, optionstr = line.partition(" ")
                options = set(optionstr.replace(' ', '').split(','))
                self.result.clobberzp = False
                self.result.restorezp = False
                if "clobber" in options:
                    options.remove("clobber")
                    self.result.clobberzp = True
                if "restore" in options:
                    options.remove("restore")
                    if self.result.clobberzp:
                        self.result.restorezp = True
                    else:
                        raise self.PError("can only use restore zp option if clobber zp is used as well")
                if options:
                    raise self.PError("invalid zp option(s): " + str(options))
            elif line.startswith("address"):
                if self.result.start_address:
                    raise self.PError("multiple occurrences of 'address'")
                _, _, arg = line.partition(" ")
                try:
                    self.result.start_address = parse_expr_as_int(arg, None, None, self.sourceref)
                except ParseError:
                    raise self.PError("invalid address")
                if self.result.format == ProgramFormat.PRG and self.result.with_sys and self.result.start_address != 0x0801:
                    raise self.PError("cannot use non-default 'address' when output format includes basic SYS program")
            else:
                # header parsing finished!
                self.prev_line()
                if not self.result.start_address:
                    # set the proper default start address
                    if self.result.format == ProgramFormat.PRG:
                        self.result.start_address = 0x0801  # normal C-64 basic program start address
                    elif self.result.format == ProgramFormat.RAW:
                        self.result.start_address = 0xc000  # default start for raw assembly
                if self.result.format == ProgramFormat.PRG and self.result.with_sys and self.result.start_address != 0x0801:
                    raise self.PError("cannot use non-default 'address' when output format includes basic SYS program")
                return

    def parse_import(self) -> None:
        line = self.next_line()
        line = line.lstrip()
        if not line.startswith("import"):
            raise self.PError("expected import")
        try:
            _, arg = line.split(maxsplit=1)
        except ValueError:
            raise self.PError("invalid import statement")
        if not arg.startswith('"') or not arg.endswith('"'):
            raise self.PError("filename must be between quotes")
        filename = arg[1:-1]
        if not filename:
            raise self.PError("invalid filename")
        filename_at_source_location = os.path.join(os.path.split(self.sourceref.file)[0], filename)
        filename_at_libs_location = os.path.join(os.getcwd(), "lib", filename)
        candidates = [filename,
                      filename_at_source_location,
                      filename_at_libs_location,
                      filename+".ill",
                      filename_at_source_location+".ill",
                      filename_at_libs_location+".ill"]
        for filename in candidates:
            if os.path.isfile(filename):
                print("importing", filename)
                parser = self.create_import_parser(filename, self.outputdir)
                result = parser.parse()
                print("\ncontinuing", self.sourceref.file)
                if result:
                    # merge the symbol table of the imported file into our own
                    try:
                        self.root_scope.merge_roots(parser.root_scope)
                        self.result.merge(result)
                    except SymbolError as x:
                        raise self.PError(str(x))
                    return
                else:
                    raise self.PError("Error while parsing imported file")
        raise self.PError("imported file not found")

    def create_import_parser(self, filename: str, outputdir: str) -> 'Parser':
        return Parser(filename, outputdir, parsing_import=True, ppsymbols=self.ppsymbols, sub_usage=self.result.subroutine_usage)

    def parse_block(self) -> Optional[ParseResult.Block]:
        # first line contains block header "~ [name] [addr]" followed by a '{'
        self._parse_comments()
        line = self.next_line()
        line = line.lstrip()
        if not line.startswith("~"):
            raise self.PError("expected '~' (block)")
        block_args = line[1:].split()
        arg = ""
        self.cur_block = ParseResult.Block("", self.sourceref.copy(), self.root_scope)
        is_zp_block = False
        while block_args:
            arg = block_args.pop(0)
            if arg.isidentifier():
                if arg.lower() == "zeropage" or arg in ("zp", "zP", "Zp"):
                    raise self.PError("zero page block should be named 'ZP'")
                is_zp_block = arg == "ZP"
                if arg in set(b.name for b in self.result.blocks):
                    orig = [b for b in self.result.blocks if b.name == arg][0]
                    if not is_zp_block:
                        raise self.PError("duplicate block name '{:s}', original definition at {}".format(arg, orig.sourceref))
                    self.cur_block = orig  # zero page block occurrences are merged
                else:
                    self.cur_block = ParseResult.Block(arg, self.sourceref.copy(), self.root_scope)
                    try:
                        self.root_scope.define_scope(self.cur_block.symbols, self.cur_block.sourceref)
                    except SymbolError as x:
                        raise self.PError(str(x))
            elif arg == "{":
                break
            elif arg.endswith("{"):
                # when there is no whitespace before the {
                block_args.insert(0, "{")
                block_args.insert(0, arg[:-1])
                continue
            else:
                try:
                    block_address = parse_expr_as_int(arg, self.cur_block.symbols, self.ppsymbols, self.sourceref)
                except ParseError:
                    raise self.PError("Invalid block address")
                if block_address == 0 or (block_address < 0x0200 and not is_zp_block):
                    raise self.PError("block address must be >= $0200 (or omitted)")
                if is_zp_block:
                    if block_address not in (0, 0x04):
                        raise self.PError("zero page block address must be $04 (or omittted)")
                    block_address = 0x04
                self.cur_block.address = block_address
        if arg != "{":
            line = self.peek_next_line()
            if line != "{":
                raise self.PError("expected '{' after block")
            else:
                self.next_line()
        if self.print_block_parsing:
            if self.cur_block.address:
                print("  parsing block '{:s}' at ${:04x}".format(self.cur_block.name, self.cur_block.address))
            else:
                print("  parsing block '{:s}'".format(self.cur_block.name))
        if self.cur_block.ignore:
            # just skip the lines until we hit a '}' that closes the block
            nesting_level = 1
            while True:
                line = self.next_line().strip()
                if line.endswith("{"):
                    nesting_level += 1
                elif line == "}":
                    nesting_level -= 1
                    if nesting_level == 0:
                        self.print_warning("ignoring block without name and address", self.cur_block.sourceref)
                        return None
            else:
                raise self.PError("invalid statement in block")
        while True:
            self._parse_comments()
            line = self.next_line()
            unstripped_line = line
            line = line.strip()
            if line == "}":
                if is_zp_block and any(b.name == "ZP" for b in self.result.blocks):
                    return None     # we already have the ZP block
                if self.cur_block.ignore:
                    self.print_warning("ignoring block without name and address", self.cur_block.sourceref)
                    return None
                return self.cur_block
            if line.startswith(("var ", "var\t")):
                self.parse_var_def(line)
            elif line.startswith(("const ", "const\t")):
                self.parse_const_def(line)
            elif line.startswith(("memory ", "memory\t")):
                self.parse_memory_def(line, is_zp_block)
            elif line.startswith(("sub ", "sub\t")):
                if is_zp_block:
                    raise self.PError("ZP block cannot contain subroutines")
                self.parse_subroutine_def(line)
            elif line.startswith(("asminclude ", "asminclude\t", "asmbinary ", "asmbinary\t")):
                if is_zp_block:
                    raise self.PError("ZP block cannot contain assembler directives")
                self.cur_block.statements.append(self.parse_asminclude(line))
            elif line.startswith(("asm ", "asm\t")):
                if is_zp_block:
                    raise self.PError("ZP block cannot contain code statements")
                self.prev_line()
                self.cur_block.statements.append(self.parse_asm())
            elif line == "breakpoint":
                self.cur_block.statements.append(ParseResult.BreakpointStmt(self.sourceref.line))
                self.print_warning("breakpoint defined")
            elif unstripped_line.startswith((" ", "\t")):
                if is_zp_block:
                    raise self.PError("ZP block cannot contain code statements")
                self.cur_block.statements.append(self.parse_statement(line))
            elif line:
                if is_zp_block:
                    raise self.PError("ZP block cannot contain code labels")
                self.parse_label(line)
            else:
                raise self.PError("invalid statement in block")

    def parse_label(self, line: str) -> None:
        label_line = line.split(maxsplit=1)
        if str.isidentifier(label_line[0]):
            labelname = label_line[0]
            if labelname in self.cur_block.label_names:
                raise self.PError("label already defined")
            if labelname in self.cur_block.symbols:
                raise self.PError("symbol already defined")
            self.cur_block.symbols.define_label(labelname, self.sourceref)
            self.cur_block.statements.append(ParseResult.Label(labelname, self.sourceref.line))
            if len(label_line) > 1:
                rest = label_line[1]
                self.cur_block.statements.append(self.parse_statement(rest))
        else:
            raise self.PError("invalid label name")

    def parse_memory_def(self, line: str, is_zeropage: bool=False) -> None:
        varname, datatype, length, dimensions, valuetext = self.parse_def_common(line, "memory")
        memaddress = parse_expr_as_int(valuetext, self.cur_block.symbols, self.ppsymbols, self.sourceref)
        if is_zeropage and memaddress > 0xff:
            raise self.PError("address must be in zeropage $00-$ff")
        try:
            self.cur_block.symbols.define_variable(varname, self.sourceref, datatype,
                                                   length=length, address=memaddress, matrixsize=dimensions)
        except SymbolError as x:
            raise self.PError(str(x)) from x

    def parse_const_def(self, line: str) -> None:
        varname, datatype, length, dimensions, valuetext = self.parse_def_common(line, "const")
        if dimensions:
            raise self.PError("cannot declare a constant matrix")
        value = parse_expr_as_primitive(valuetext, self.cur_block.symbols, self.ppsymbols, self.sourceref)
        _, value = self.coerce_value(self.sourceref, datatype, value)
        try:
            self.cur_block.symbols.define_constant(varname, self.sourceref, datatype, length=length, value=value)
        except (ValueError, SymbolError) as x:
            raise self.PError(str(x)) from x

    def parse_subroutine_def(self, line: str) -> None:
        match = re.fullmatch(r"sub\s+(?P<name>\w+)\s+"
                             r"\((?P<parameters>[\w\s:,]*)\)"
                             r"\s*->\s*"
                             r"\((?P<results>[\w\s?,]*)\)\s*"
                             r"(?P<decltype>\s+=\s+(?P<address>\S*)|{)\s*", line)
        if not match:
            raise self.PError("invalid sub declaration")
        groups = match.groupdict()
        code_decl = groups["decltype"] == "{"
        name, parameterlist, resultlist, address_str = groups["name"], groups["parameters"], groups["results"], groups["address"]
        parameters = [(m.group("name"), m.group("target"))
                      for m in re.finditer(r"(?:(?:(?P<name>[\w]+)\s*:\s*)?(?P<target>[\w]+))(?:,|$)", parameterlist)]
        for _, regs in parameters:
            if regs not in REGISTER_SYMBOLS:
                raise self.PError("invalid register(s) in parameter or return values")
        all_paramnames = [p[0] for p in parameters if p[0]]
        if len(all_paramnames) != len(set(all_paramnames)):
            raise self.PError("duplicates in parameter names")
        results = [m.group("name") for m in re.finditer(r"\s*(?P<name>(?:\w+)\??)\s*(?:,|$)", resultlist)]
        subroutine_block = None
        if code_decl:
            address = None
            # parse the subroutine code lines (until the closing '}')
            subroutine_block = ParseResult.Block(self.cur_block.name + "." + name, self.sourceref, self.cur_block.symbols)
            current_block = self.cur_block
            self.cur_block = subroutine_block
            while True:
                self._parse_comments()
                line = self.next_line()
                unstripped_line = line
                line = line.strip()
                if line == "}":
                    # subroutine end
                    break
                if line.startswith(("sub ", "sub\t")):
                    raise self.PError("cannot nest subroutines")
                elif line.startswith(("asm ", "asm\t")):
                    self.prev_line()
                    subroutine_block.statements.append(self.parse_asm())
                elif unstripped_line.startswith((" ", "\t")):
                    subroutine_block.statements.append(self.parse_statement(line))
                elif line:
                    self.parse_label(line)
                else:
                    raise self.PError("invalid statement in subroutine")
            self.cur_block = current_block
            self.cur_block.sourceref = subroutine_block.sourceref
        else:
            try:
                address = parse_expr_as_int(address_str, self.cur_block.symbols, self.ppsymbols, self.sourceref)
            except ParseError:
                raise self.PError("invalid subroutine address")
        try:
            self.cur_block.symbols.define_sub(name, self.sourceref, parameters, results, address, subroutine_block)
        except SymbolError as x:
            raise self.PError(str(x)) from x

    def parse_var_def(self, line: str) -> None:
        varname, datatype, length, dimensions, valuetext = self.parse_def_common(line, "var", False)
        value = parse_expr_as_primitive(valuetext, self.cur_block.symbols, self.ppsymbols, self.sourceref)
        _, value = self.coerce_value(self.sourceref, datatype, value)
        try:
            self.cur_block.symbols.define_variable(varname, self.sourceref, datatype,
                                                   length=length, value=value, matrixsize=dimensions)
        except (ValueError, SymbolError) as x:
            raise self.PError(str(x)) from x

    def parse_def_common(self, line: str, what: str, value_required: bool=True) -> \
            Tuple[str, DataType, int, Optional[Tuple[int, int]], str]:
        try:
            vartext, valuetext = line.split("=", maxsplit=1)
        except ValueError:
            if '=' not in line:
                if value_required:
                    raise self.PError("missing value assignment")
                vartext, valuetext = line, "0"  # unspecified value is '0'
            else:
                raise self.PError("invalid {:s} decl, '=' missing?".format(what))
        args = self.psplit(vartext)
        if args[0] != what or len(args) < 2:
            raise self.PError("invalid {:s} decl".format(what))
        if len(args) > 3 or valuetext.startswith('='):
            raise self.PError("invalid {:s} decl, '=' missing?".format(what))
        if len(args) == 2:
            args.insert(1, ".byte")  # unspecified data type is ".byte"
        if not args[1].startswith("."):
            raise self.PError("invalid {:s} decl, type is missing".format(what))
        varname = args[2]
        if not varname.isidentifier():
            raise self.PError("invalid {:s} name".format(what))
        if varname in RESERVED_NAMES:
            raise self.PError("can't use a reserved name as {:s} name".format(what))
        datatype, length, matrix_dimensions = self.get_datatype(args[1])
        return varname, datatype, length, matrix_dimensions, valuetext

    def parse_statement(self, line: str) -> ParseResult._AstNode:
        match = re.fullmatch(r"(?P<if>if(_[a-z]+)?)\s+(?P<cond>.+)?goto\s+(?P<subname>[\S]+?)\s*(\((?P<arguments>.*)\))?\s*", line)
        if match:
            # conditional goto
            groups = match.groupdict()
            subname = groups["subname"]
            if '!' in subname:
                raise self.PError("goto is always without register preservation, should not have exclamation mark")
            if groups["if"] == "if" and not groups["cond"]:
                raise self.PError("need explicit if status when a condition is not present")
            condition = self.parse_if_condition(groups["if"], groups["cond"])
            return self.parse_call_or_goto(subname, groups["arguments"], None, False, True, condition=condition)
        match = re.fullmatch(r"goto\s+(?P<subname>[\S]+?)\s*(\((?P<arguments>.*)\))?\s*", line)
        if match:
            # goto
            groups = match.groupdict()
            subname = groups["subname"]
            if '!' in subname:
                raise self.PError("goto is always without register preservation, should not have exclamation mark")
            return self.parse_call_or_goto(subname, groups["arguments"], None, False, True)
        match = re.fullmatch(r"(?P<outputs>[^\(]*\s*=)?\s*(?P<subname>[\S]+?)\s*(?P<fcall>[!]?)\s*(\((?P<arguments>.*)\))?\s*", line)
        if match:
            # subroutine call (not a goto) with possible output param assignment
            groups = match.groupdict()
            preserve = not bool(groups["fcall"])
            subname = groups["subname"]
            arguments = groups["arguments"]
            outputs = groups["outputs"] or ""
            if outputs.strip() == "=":
                raise self.PError("missing assignment target variables")
            outputs = outputs.rstrip("=")
            if arguments or match.group(4):     # group 4 = (possibly empty) parenthesis
                return self.parse_call_or_goto(subname, arguments, outputs, preserve, False)
            # apparently it is not a call (no arguments), fall through
        if line == "return" or line.startswith(("return ", "return\t")):
            return self.parse_return(line)
        elif line.endswith(("++", "--")):
            incr = line.endswith("++")
            what = self.parse_expression(line[:-2].rstrip())
            if isinstance(what, ParseResult.IntegerValue):
                raise self.PError("cannot in/decrement a constant value")
            if incr:
                return ParseResult.InplaceIncrStmt(what, 1, self.sourceref.line)
            return ParseResult.InplaceDecrStmt(what, 1, self.sourceref.line)
        else:
            # perhaps it is an augmented assignment statement
            match = re.fullmatch(r"(?P<left>\S+)\s*(?P<assignment>\+=|-=|\*=|/=|%=|//=|\*\*=|&=|\|=|\^=|>>=|<<=)\s*(?P<right>\S.*)", line)
            if match:
                return self.parse_augmented_assignment(match.group("left"), match.group("assignment"), match.group("right"))
            # a normal assignment perhaps?
            splits = [s.strip() for s in line.split('=')]
            if len(splits) > 1 and all(splits):
                return self.parse_assignment(*splits)
            raise self.PError("invalid statement")

    def parse_call_or_goto(self, targetstr: str, argumentstr: str, outputstr: str,
                           preserve_regs=True, is_goto=False, condition: ParseResult.IfCondition=None) -> ParseResult.CallStmt:
        if not is_goto:
            assert condition is None
        argumentstr = argumentstr.strip() if argumentstr else ""
        outputstr = outputstr.strip() if outputstr else ""
        arguments = None
        outputvars = None
        if argumentstr:
            arguments = parse_arguments(argumentstr, self.sourceref)
        target = None  # type: ParseResult.Value
        if targetstr[0] == '[' and targetstr[-1] == ']':
            # indirect call to address in register pair or memory location
            targetstr, target = self.parse_indirect_value(targetstr, True)
            if target.datatype != DataType.WORD:
                raise self.PError("invalid call target (should contain 16-bit)")
        else:
            target = self.parse_expression(targetstr)
        if not isinstance(target, (ParseResult.IntegerValue, ParseResult.RegisterValue,
                                   ParseResult.MemMappedValue, ParseResult.IndirectValue)):
            raise self.PError("cannot call that type of symbol")
        if isinstance(target, ParseResult.IndirectValue) \
                and not isinstance(target.value, (ParseResult.IntegerValue, ParseResult.RegisterValue, ParseResult.MemMappedValue)):
            raise self.PError("cannot call that type of indirect symbol")
        address = target.address if isinstance(target, ParseResult.MemMappedValue) else None
        try:
            _, symbol = self.lookup_with_ppsymbols(targetstr)
        except ParseError:
            symbol = None   # it's probably a number or a register then
        if isinstance(symbol, SubroutineDef):
            if condition and symbol.parameters:
                raise self.PError("cannot use a subroutine that requires parameters as a target for conditional goto")
            # verify subroutine arguments
            if len(arguments or []) != len(symbol.parameters):
                raise self.PError("invalid number of arguments ({:d}, expected {:d})"
                                  .format(len(arguments or []), len(symbol.parameters)))
            args_with_pnames = []
            for i, (argname, value) in enumerate(arguments or []):
                pname, preg = symbol.parameters[i]
                required_name = pname or preg
                if argname and argname != required_name:
                    raise self.PError("parameter mismatch ('{:s}', expected '{:s}')".format(argname, required_name))
                argname = preg
                args_with_pnames.append((argname, value))
            arguments = args_with_pnames
            # verify output parameters
            if symbol.return_registers:
                if outputstr:
                    outputs = [r.strip() for r in outputstr.split(",")]
                    if len(outputs) != len(symbol.return_registers):
                        raise self.PError("invalid number of output parameters consumed ({:d}, expected {:d})"
                                          .format(len(outputs), len(symbol.return_registers)))
                    outputvars = list(zip(symbol.return_registers, (self.parse_expression(out) for out in outputs)))
                else:
                    self.print_warning("return values discarded")
            else:
                if outputstr:
                    raise self.PError("this subroutine doesn't have output parameters")
            self.result.sub_used_by(symbol, self.sourceref)  # sub usage tracking
        else:
            if outputstr:
                raise self.PError("call cannot use output parameter assignment here, a subroutine is required for that")
            if arguments:
                raise self.PError("call cannot take any arguments here, a subroutine is required for that")
        # verify that all arguments have gotten a name
        if any(not a[0] for a in arguments or []):
            raise self.PError("all call arguments should have a name or be matched on a named parameter")
        if isinstance(target, (type(None), ParseResult.Value)):
            # special case for the C-64 lib's print function, to be able to use it with a single character argument
            if target.name == "c64util.print_string" and len(arguments) == 1 and len(arguments[0][0]) > 1:
                if arguments[0][1].startswith("'") and arguments[0][1].endswith("'"):
                    target = self.parse_expression("c64.CHROUT")
                    address = target.address
                    outputvars = None
                    _, newsymbol = self.lookup_with_ppsymbols("c64.CHROUT")
                    assert len(newsymbol.parameters) == 1
                    arguments = [(newsymbol.parameters[0][1], arguments[0][1])]
            if is_goto:
                return ParseResult.CallStmt(self.sourceref.line, target, address=address,
                                            arguments=arguments, outputs=outputvars, is_goto=True, condition=condition)
            else:
                return ParseResult.CallStmt(self.sourceref.line, target, address=address,
                                            arguments=arguments, outputs=outputvars, preserve_regs=preserve_regs)
        else:
            raise TypeError("target should be a Value", target)

    def parse_integer(self, text: str) -> int:
        text = text.strip()
        if text.startswith('$'):
            return int(text[1:], 16)
        if text.startswith('%'):
            return int(text[1:], 2)
        return int(text)

    def parse_assignment(self, *parts) -> ParseResult.AssignmentStmt:
        # parses the assignment of one rvalue to one or more lvalues
        l_values = [self.parse_expression(p) for p in parts[:-1]]
        r_value = self.parse_expression(parts[-1])
        if any(lv.constant for lv in l_values):
            raise self.PError("can't have a constant as assignment target, perhaps you wanted indirection [...] instead?")
        for lv in l_values:
            assignable, reason = lv.assignable_from(r_value)
            if not assignable:
                raise self.PError("cannot assign {0} to {1}; {2}".format(r_value, lv, reason))
            if lv.datatype in (DataType.BYTE, DataType.WORD, DataType.MATRIX):
                # truncate the rvalue if needed
                if isinstance(r_value, ParseResult.FloatValue):
                    truncated, value = self.coerce_value(self.sourceref, lv.datatype, r_value.value)
                    if truncated:
                        r_value = ParseResult.IntegerValue(int(value), datatype=lv.datatype, name=r_value.name)
        return ParseResult.AssignmentStmt(l_values, r_value, self.sourceref.line)

    def parse_augmented_assignment(self, leftstr: str, operator: str, rightstr: str) \
            -> Union[ParseResult.AssignmentStmt, ParseResult.InplaceDecrStmt, ParseResult.InplaceIncrStmt]:
        # parses an augmented assignment (for instance: value += 3)
        if operator not in ParseResult.AugmentedAssignmentStmt.SUPPORTED_OPERATORS:
            raise self.PError("augmented assignment operator '{:s}' not supported".format(operator))
        l_value = self.parse_expression(leftstr)
        r_value = self.parse_expression(rightstr)
        if l_value.constant:
            raise self.PError("can't have a constant as assignment target, perhaps you wanted indirection [...] instead?")
        if l_value.datatype in (DataType.BYTE, DataType.WORD, DataType.MATRIX):
            # truncate the rvalue if needed
            if isinstance(r_value, ParseResult.FloatValue):
                truncated, value = self.coerce_value(self.sourceref, l_value.datatype, r_value.value)
                if truncated:
                    r_value = ParseResult.IntegerValue(int(value), datatype=l_value.datatype, name=r_value.name)
        if r_value.constant and operator in ("+=", "-="):
            if operator == "+=":
                if r_value.value > 0:  # type: ignore
                    return ParseResult.InplaceIncrStmt(l_value, r_value.value, self.sourceref.line)  # type: ignore
                elif r_value.value < 0:  # type: ignore
                    return ParseResult.InplaceDecrStmt(l_value, -r_value.value, self.sourceref.line)  # type: ignore
                else:
                    self.print_warning("incr with zero, ignored")
            else:
                if r_value.value > 0:  # type: ignore
                    return ParseResult.InplaceDecrStmt(l_value, r_value.value, self.sourceref.line)  # type: ignore
                elif r_value.value < 0:  # type: ignore
                    return ParseResult.InplaceIncrStmt(l_value, -r_value.value, self.sourceref.line)  # type: ignore
                else:
                    self.print_warning("decr with zero, ignored")
        return ParseResult.AugmentedAssignmentStmt(l_value, operator, r_value, self.sourceref.line)

    def parse_return(self, line: str) -> ParseResult.ReturnStmt:
        parts = line.split(maxsplit=1)
        if parts[0] != "return":
            raise self.PError("invalid statement, return expected")
        a = x = y = None
        values = []  # type: List[str]
        if len(parts) > 1:
            values = parts[1].split(",")
        if len(values) == 0:
            return ParseResult.ReturnStmt(self.sourceref.line)
        else:
            a = self.parse_expression(values[0]) if values[0] else None
            if len(values) > 1:
                x = self.parse_expression(values[1]) if values[1] else None
                if len(values) > 2:
                    y = self.parse_expression(values[2]) if values[2] else None
                    if len(values) > 3:
                        raise self.PError("too many returnvalues")
        return ParseResult.ReturnStmt(self.sourceref.line, a, x, y)

    def parse_asm(self) -> ParseResult.InlineAsm:
        line = self.next_line()
        lineno = self.sourceref.line
        aline = line.split()
        if not len(aline) == 2 or aline[0] != "asm" or aline[1] != "{":
            raise self.PError("invalid asm start")
        asmlines = []   # type: List[str]
        while True:
            line = self.next_line()
            if line.strip() == "}":
                return ParseResult.InlineAsm(asmlines, lineno)
            # asm can refer to other symbols as well, track subroutine usage
            splits = line.split(maxsplit=1)
            if len(splits) == 2:
                for match in re.finditer(r"(?P<symbol>[a-zA-Z_$][a-zA-Z0-9_\.]+)", splits[1]):
                    name = match.group("symbol")
                    if name[0] == '$':
                        continue
                    try:
                        if '.' not in name:
                            name = self.cur_block.symbols.parent.name + '.' + name
                        _, symbol = self.lookup_with_ppsymbols(name)
                    except ParseError:
                        pass
                    else:
                        if isinstance(symbol, SubroutineDef):
                            self.result.sub_used_by(symbol, self.sourceref)
            asmlines.append(line)

    def parse_asminclude(self, line: str) -> ParseResult.InlineAsm:
        aline = line.split()
        if len(aline) < 2:
            raise self.PError("invalid asminclude or asmbinary statement")
        filename = aline[1]
        if not filename.startswith('"') or not filename.endswith('"'):
            raise self.PError("filename must be between quotes")
        filename = filename[1:-1]
        if not filename:
            raise self.PError("invalid filename")
        filename_in_sourcedir = os.path.join(os.path.split(self.sourceref.file)[0], filename)
        filename_in_output_location = os.path.join(self.outputdir, filename)
        if not os.path.isfile(filename_in_sourcedir):
            raise self.PError("included file not found")
        print("copying included file to output location:", filename)
        shutil.copy(filename_in_sourcedir, filename_in_output_location)
        if aline[0] == "asminclude":
            if len(aline) == 3:
                scopename = aline[2]
                lines = ['{:s}\t.binclude "{:s}"'.format(scopename, filename)]
            else:
                raise self.PError("invalid asminclude statement")
            return ParseResult.InlineAsm(lines, self.sourceref.line)
        elif aline[0] == "asmbinary":
            if len(aline) == 4:
                offset = parse_expr_as_int(aline[2], None, None, self.sourceref)
                length = parse_expr_as_int(aline[3], None, None, self.sourceref)
                lines = ['\t.binary "{:s}", ${:04x}, ${:04x}'.format(filename, offset, length)]
            elif len(aline) == 3:
                offset = parse_expr_as_int(aline[2], None, None, self.sourceref)
                lines = ['\t.binary "{:s}", ${:04x}'.format(filename, offset)]
            elif len(aline) == 2:
                lines = ['\t.binary "{:s}"'.format(filename)]
            else:
                raise self.PError("invalid asmbinary statement")
            return ParseResult.InlineAsm(lines, self.sourceref.line)
        else:
            raise self.PError("invalid statement")

    def parse_expression(self, text: str, is_indirect=False) -> ParseResult.Value:
        # parse an expression into whatever it is (primitive value, register, memory, register, etc)
        text = text.strip()
        if not text:
            raise self.PError("value expected")
        if text[0] == '#':
            if is_indirect:
                raise self.PError("using the address-of something in an indirect value makes no sense")
            # take the pointer (memory address) from the thing that follows this
            expression = self.parse_expression(text[1:])
            if isinstance(expression, ParseResult.StringValue):
                return expression
            elif isinstance(expression, ParseResult.MemMappedValue):
                return ParseResult.IntegerValue(expression.address, datatype=DataType.WORD, name=expression.name)
            else:
                raise self.PError("cannot take the address of this type")
        elif text[0] in "-.0123456789$%~":
            number = parse_expr_as_number(text, self.cur_block.symbols, self.ppsymbols, self.sourceref)
            try:
                if type(number) is int:
                    return ParseResult.IntegerValue(int(number))
                elif type(number) is float:
                    return ParseResult.FloatValue(number)
                else:
                    raise TypeError("invalid number type")
            except (ValueError, OverflowError) as ex:
                raise self.PError(str(ex))
        elif text in REGISTER_WORDS:
            return ParseResult.RegisterValue(text, DataType.WORD)
        elif text in REGISTER_BYTES | REGISTER_SBITS:
            return ParseResult.RegisterValue(text, DataType.BYTE)
        elif (text.startswith("'") and text.endswith("'")) or (text.startswith('"') and text.endswith('"')):
            strvalue = parse_expr_as_string(text, self.cur_block.symbols, self.ppsymbols, self.sourceref)
            if len(strvalue) == 1:
                petscii_code = char_to_bytevalue(strvalue)
                return ParseResult.IntegerValue(petscii_code)
            return ParseResult.StringValue(strvalue)
        elif text == "true":
            return ParseResult.IntegerValue(1)
        elif text == "false":
            return ParseResult.IntegerValue(0)
        elif self.is_identifier(text):
            symblock, sym = self.lookup_with_ppsymbols(text)
            if isinstance(sym, (VariableDef, ConstantDef)):
                constant = isinstance(sym, ConstantDef)
                if self.cur_block is symblock:
                    symbolname = sym.name
                else:
                    symbolname = "{:s}.{:s}".format(sym.blockname, sym.name)
                if isinstance(sym, VariableDef) and sym.register:
                    return ParseResult.RegisterValue(sym.register, sym.type, name=symbolname)
                elif sym.type in (DataType.BYTE, DataType.WORD, DataType.FLOAT):
                    if isinstance(sym, ConstantDef):
                        if sym.type == DataType.FLOAT:
                            return ParseResult.FloatValue(sym.value, sym.name)   # type: ignore
                        elif sym.type in (DataType.BYTE, DataType.WORD):
                            return ParseResult.IntegerValue(sym.value, datatype=sym.type, name=sym.name)  # type: ignore
                        elif sym.type in STRING_DATATYPES:
                            return ParseResult.StringValue(sym.value, sym.name, True)   # type: ignore
                        else:
                            raise TypeError("invalid const type", sym.type)
                    else:
                        return ParseResult.MemMappedValue(sym.address, sym.type, sym.length, name=symbolname, constant=constant)
                elif sym.type in STRING_DATATYPES:
                    return ParseResult.StringValue(sym.value, name=symbolname, constant=constant)      # type: ignore
                elif sym.type == DataType.MATRIX:
                    raise self.PError("cannot manipulate matrix directly, use one of the matrix procedures")
                elif sym.type == DataType.BYTEARRAY or sym.type == DataType.WORDARRAY:
                    raise self.PError("cannot manipulate array directly, use one of the array procedures")
                else:
                    raise self.PError("invalid symbol type")
            elif isinstance(sym, LabelDef):
                name = sym.name if symblock is self.cur_block else sym.blockname + '.' + sym.name
                return ParseResult.MemMappedValue(None, DataType.WORD, 1, name, True)
            elif isinstance(sym, SubroutineDef):
                self.result.sub_used_by(sym, self.sourceref)
                name = sym.name if symblock is self.cur_block else sym.blockname + '.' + sym.name
                return ParseResult.MemMappedValue(sym.address, DataType.WORD, 1, name, True)
            else:
                raise self.PError("invalid symbol type")
        elif text.startswith('[') and text.endswith(']'):
            return self.parse_indirect_value(text)[1]
        else:
            raise self.PError("invalid single value '" + text + "'")    # @todo understand complex expressions

    def parse_indirect_value(self, text: str, allow_mmapped_for_call: bool=False) -> Tuple[str, ParseResult.IndirectValue]:
        indirect = text[1:-1].strip()
        indirect2, sep, typestr = indirect.rpartition('.')
        type_modifier = None
        if sep:
            if typestr in ("byte", "word", "float"):
                type_modifier, type_len, _ = self.get_datatype(sep + typestr)
                indirect = indirect2
        expr = self.parse_expression(indirect, True)
        if not isinstance(expr, (ParseResult.IntegerValue, ParseResult.MemMappedValue, ParseResult.RegisterValue)):
            raise self.PError("only integers, memmapped vars, and registers can be used in an indirect value")
        if type_modifier is None:
            if isinstance(expr, (ParseResult.RegisterValue, ParseResult.MemMappedValue)):
                type_modifier = expr.datatype
            else:
                type_modifier = DataType.BYTE
        if isinstance(expr, ParseResult.IntegerValue):
            if type_modifier not in (DataType.BYTE, DataType.WORD, DataType.FLOAT):
                raise self.PError("invalid type modifier for the value's datatype")
        elif isinstance(expr, ParseResult.MemMappedValue):
            if allow_mmapped_for_call:
                if type_modifier and expr.datatype != type_modifier:
                    raise self.PError("invalid type modifier for the value's datatype, must be " + expr.datatype.name)
            else:
                raise self.PError("use variable directly instead of using indirect addressing")
        return indirect, ParseResult.IndirectValue(expr, type_modifier)

    def is_identifier(self, name: str) -> bool:
        if name.isidentifier():
            return True
        blockname, sep, name = name.partition(".")
        if sep:
            return blockname.isidentifier() and name.isidentifier()
        return False

    def lookup_with_ppsymbols(self, dottedname: str) -> Tuple[ParseResult.Block, Union[SymbolDefinition, SymbolTable]]:
        # Tries to find a symbol, if it cannot be located, the symbol table from the preprocess parse phase is consulted as well
        symblock, sym = self.cur_block.lookup(dottedname)
        if sym is None and self.ppsymbols:
            # symbol is not (yet) known, see if the symbols from the preprocess parse phase know about it
            if '.' not in dottedname:
                dottedname = self.cur_block.name + '.' + dottedname
            try:
                symtable, sym = self.ppsymbols.lookup(dottedname)
                assert dottedname.startswith(symtable.name)
                symblock = None   # the block might not have been parsed yet, so just return this instead
            except (LookupError, SymbolError) as x:
                raise self.PError(str(x))
        return symblock, sym

    def _size_from_arraydecl(self, decl: str) -> int:
        return parse_expr_as_int(decl[:-1].split("(")[-1], self.cur_block.symbols, self.ppsymbols, self.sourceref)

    def _size_from_matrixdecl(self, decl: str) -> Tuple[int, int]:
        dimensions = decl[:-1].split("(")[-1]
        try:
            xs, ys = dimensions.split(",")
        except ValueError:
            raise self.PError("invalid matrix dimensions")
        return (parse_expr_as_int(xs, self.cur_block.symbols, self.ppsymbols, self.sourceref),
                parse_expr_as_int(ys, self.cur_block.symbols, self.ppsymbols, self.sourceref))

    def coerce_value(self, sourceref: SourceRef, datatype: DataType, value: PrimitiveType) -> Tuple[bool, PrimitiveType]:
        # if we're a BYTE type, and the value is a single character, convert it to the numeric value
        if datatype in (DataType.BYTE, DataType.BYTEARRAY, DataType.MATRIX) and isinstance(value, str):
            if len(value) == 1:
                return True, char_to_bytevalue(value)
        # if we're an integer value and the passed value is float, truncate it (and give a warning)
        if datatype in (DataType.BYTE, DataType.WORD, DataType.MATRIX) and type(value) is float:
            frac = math.modf(value)  # type:ignore
            if frac != 0:
                self.print_warning("float value truncated")
                return True, int(value)
        return False, value

    @staticmethod
    def to_hex(number: int) -> str:
        # 0..255 -> "$00".."$ff"
        # 256..65536 -> "$0100".."$ffff"
        if 0 <= number < 0x100:
            return "${:02x}".format(number)
        if number < 0x10000:
            return "${:04x}".format(number)
        raise OverflowError(number)

    def psplit(self, sentence: str, separators: str=" \t", lparen: str="(", rparen: str=")") -> List[str]:
        """split a sentence but not on separators within parenthesis"""
        nb_brackets = 0
        sentence = sentence.strip(separators)  # get rid of leading/trailing seps
        indices = [0]
        for i, c in enumerate(sentence):
            if c == lparen:
                nb_brackets += 1
            elif c == rparen:
                nb_brackets -= 1
            elif c in separators and nb_brackets == 0:
                indices.append(i)
            # handle malformed string
            if nb_brackets < 0:
                raise self.PError("syntax error")

        indices.append(len(sentence))
        # handle missing closing parentheses
        if nb_brackets > 0:
            raise self.PError("syntax error")
        result = [sentence[i:j].strip(separators) for i, j in zip(indices, indices[1:])]
        return list(filter(None, result))   # remove empty strings

    def parse_if_condition(self, ifpart: str, conditionpart: str) -> ParseResult.IfCondition:
        if ifpart == "if":
            ifstatus = "true"
        else:
            ifstatus = ifpart[3:]
        if ifstatus not in ParseResult.IfCondition.IF_STATUSES:
            raise self.PError("invalid if form")
        if conditionpart:
            if ifstatus not in ("true", "not", "zero"):
                raise self.PError("can only have if[_true], if_not or if_zero when using a comparison expression")
            left, operator, right = parse_expr_as_comparison(conditionpart, self.sourceref)
            leftv = self.parse_expression(left)
            if not operator and isinstance(leftv, (ParseResult.IntegerValue, ParseResult.FloatValue, ParseResult.StringValue)):
                raise self.PError("condition is a constant value")
            if isinstance(leftv, ParseResult.RegisterValue):
                if leftv.register in {"SC", "SZ", "SI"}:
                    raise self.PError("cannot use a status bit register explicitly in a condition")
            if operator:
                rightv = self.parse_expression(right)
            else:
                rightv = None
            if leftv == rightv:
                raise self.PError("left and right values in comparison are identical")
            result = ParseResult.IfCondition(ifstatus, leftv, operator, rightv, self.sourceref.line)
        else:
            result = ParseResult.IfCondition(ifstatus, None, "", None, self.sourceref.line)
        if result.make_if_true():
            self.print_warning("if_not condition inverted to if")
        return result


class Optimizer:
    def __init__(self, parseresult: ParseResult) -> None:
        self.parsed = parseresult

    def optimize(self) -> ParseResult:
        print("\noptimizing parse tree")
        for block in self.parsed.all_blocks():
            self.remove_identity_assigns(block)
            self.combine_assignments_into_multi(block)
            self.optimize_multiassigns(block)
            self.remove_unused_subroutines(block)
            self.optimize_compare_with_zero(block)
        return self.parsed

    def optimize_compare_with_zero(self, block: ParseResult.Block) -> None:
        # a conditional goto that compares a value to zero will be simplified
        # the comparison operator and rvalue (0) will be removed and the if-status changed accordingly
        for stmt in block.statements:
            if isinstance(stmt, ParseResult.CallStmt):
                cond = stmt.condition
                if cond and isinstance(cond.rvalue, (ParseResult.IntegerValue, ParseResult.FloatValue)) and cond.rvalue.value == 0:
                    simplified = False
                    if cond.ifstatus in ("true", "ne"):
                        if cond.comparison_op == "==":
                            # if_true something == 0   ->  if_not something
                            cond.ifstatus = "not"
                            cond.comparison_op, cond.rvalue = "", None
                            simplified = True
                        elif cond.comparison_op == "!=":
                            # if_true something != 0  -> if_true something
                            cond.comparison_op, cond.rvalue = "", None
                            simplified = True
                    elif cond.ifstatus in ("not", "eq"):
                        if cond.comparison_op == "==":
                            # if_not something == 0   ->  if_true something
                            cond.ifstatus = "true"
                            cond.comparison_op, cond.rvalue = "", None
                            simplified = True
                        elif cond.comparison_op == "!=":
                            # if_not something != 0  -> if_not something
                            cond.comparison_op, cond.rvalue = "", None
                            simplified = True
                    if simplified:
                        print("{:s}:{:d}: simplified comparison with zero".format(block.sourceref.file, stmt.lineno))

    def combine_assignments_into_multi(self, block: ParseResult.Block) -> None:
        # fold multiple consecutive assignments with the same rvalue into one multi-assignment
        statements = []   # type: List[ParseResult._AstNode]
        multi_assign_statement = None
        for stmt in block.statements:
            if isinstance(stmt, ParseResult.AssignmentStmt) and not isinstance(stmt, ParseResult.AugmentedAssignmentStmt):
                if multi_assign_statement and multi_assign_statement.right == stmt.right:
                    multi_assign_statement.leftvalues.extend(stmt.leftvalues)
                    print("{:s}:{:d}: joined with previous line into multi-assign statement".format(block.sourceref.file, stmt.lineno))
                else:
                    if multi_assign_statement:
                        statements.append(multi_assign_statement)
                    multi_assign_statement = stmt
            else:
                if multi_assign_statement:
                    statements.append(multi_assign_statement)
                    multi_assign_statement = None
                statements.append(stmt)
        if multi_assign_statement:
            statements.append(multi_assign_statement)
        block.statements = statements

    def optimize_multiassigns(self, block: ParseResult.Block) -> None:
        # optimize multi-assign statements.
        for stmt in block.statements:
            if isinstance(stmt, ParseResult.AssignmentStmt) and len(stmt.leftvalues) > 1:
                # remove duplicates
                lvalues = list(set(stmt.leftvalues))
                if len(lvalues) != len(stmt.leftvalues):
                    print("{:s}:{:d}: removed duplicate assignment targets".format(block.sourceref.file, stmt.lineno))
                # change order: first registers, then zp addresses, then non-zp addresses, then the rest (if any)
                stmt.leftvalues = list(sorted(lvalues, key=_value_sortkey))

    def remove_identity_assigns(self, block: ParseResult.Block) -> None:
        have_removed_stmts = False
        for index, stmt in enumerate(list(block.statements)):
            if isinstance(stmt, ParseResult.AssignmentStmt):
                stmt.remove_identity_lvalues(block.sourceref.file, stmt.lineno)
                if not stmt.leftvalues:
                    print("{:s}:{:d}: removed identity assignment statement".format(block.sourceref.file, stmt.lineno))
                    have_removed_stmts = True
                    block.statements[index] = None
        if have_removed_stmts:
            # remove the Nones
            block.statements = [s for s in block.statements if s is not None]

    def remove_unused_subroutines(self, block: ParseResult.Block) -> None:
        # some symbols are used by the emitted assembly code from the code generator,
        # and should never be removed or the assembler will fail
        never_remove = {"c64util.GIVUAYF", "c64.FREADUY", "c64.FTOMEMXY"}
        discarded = []
        for sub in list(block.symbols.iter_subroutines()):
            usages = self.parsed.subroutine_usage[(sub.blockname, sub.name)]
            if not usages and sub.blockname + '.' + sub.name not in never_remove:
                block.symbols.discard_sub(sub.name)
                discarded.append(sub.name)
        if discarded:
            print("{}: discarded unused subroutines from block '{:s}':  ".format(block.sourceref, block.name), end="")
            print(",  ".join(discarded))


def _value_sortkey(value: ParseResult.Value) -> int:
    if isinstance(value, ParseResult.RegisterValue):
        num = 0
        for char in value.register:
            num *= 100
            num += ord(char)
        return num
    elif isinstance(value, ParseResult.MemMappedValue):
        if value.address is None:
            return 99999999
        if value.address < 0x100:
            return 10000 + value.address
        else:
            return 20000 + value.address
    else:
        return 99999999
