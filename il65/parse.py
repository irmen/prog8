"""
Intermediate Language for 6502/6510 microprocessors
This is the parser of the IL65 code, that generates a parse tree.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

import math
import re
import os
import shutil
import enum
from typing import Set, List, Tuple, Optional, Any, Dict, Union
from .astparse import ParseError, parse_expr_as_int, parse_expr_as_number, parse_expr_as_primitive,\
    parse_expr_as_string
from .symbols import SourceRef, SymbolTable, DataType, SymbolDefinition, SubroutineDef, LabelDef, \
    zeropage, check_value_in_range, char_to_bytevalue, \
    PrimitiveType, VariableDef, ConstantDef, SymbolError, STRING_DATATYPES, \
    REGISTER_SYMBOLS, REGISTER_WORDS, REGISTER_BYTES, RESERVED_NAMES


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

    class Block:
        _unnamed_block_labels = {}  # type: Dict[ParseResult.Block, str]

        def __init__(self, name: str, sourceref: SourceRef, parent_scope: SymbolTable) -> None:
            self.sourceref = sourceref.copy()
            self.address = 0
            self.name = name
            self.statements = []    # type: List[ParseResult._AstNode]
            self.symbols = SymbolTable(name, parent_scope, self)

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
            try:
                scope, result = self.symbols.lookup(dottedname)
                return scope.owning_block, result
            except (SymbolError, LookupError):
                return None, None

        def flatten_statement_list(self) -> None:
            if all(isinstance(stmt, ParseResult._AstNode) for stmt in self.statements):
                # this is the common case
                return
            statements = []
            for stmt in self.statements:
                if isinstance(stmt, (tuple, list)):
                    statements.extend(stmt)
                else:
                    assert isinstance(stmt, ParseResult._AstNode)
                    statements.append(stmt)
            self.statements = statements

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
        def __init__(self, value: 'ParseResult.Value', type_modifier: DataType) -> None:
            assert type_modifier
            super().__init__(type_modifier, value.name, False)
            self.value = value

        def __str__(self):
            return "<IndirectValue {} itype={} name={}>".format(self.value, self.datatype, self.name)

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
                return other.datatype == self.datatype and other.value == self.value and other.name == self.name

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
                return other.datatype == self.datatype and other.value == self.value and other.name == self.name

        def __str__(self):
            return "<FloatValue {} name={}>".format(self.value, self.name)

    class StringValue(Value):
        def __init__(self, value: str, name: str=None, constant: bool=False) -> None:
            super().__init__(DataType.STRING, name, constant)
            self.value = value

        def __hash__(self):
            return hash((self.datatype, self.value, self.name))

        def __eq__(self, other: Any) -> bool:
            if not isinstance(other, ParseResult.StringValue):
                return NotImplemented
            elif self is other:
                return True
            else:
                return other.datatype == self.datatype and other.value == self.value and other.name == self.name

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
                return other.datatype == self.datatype and other.register == self.register and other.name == self.name

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
            if self.register == "SC":
                if isinstance(other, ParseResult.IntegerValue) and other.value in (0, 1):
                    return True, ""
                return False, "can only assign an integer constant value of 0 or 1 to SC"
            if self.constant:
                return False, "cannot assign to a constant"
            if isinstance(other, ParseResult.RegisterValue) and len(self.register) < len(other.register):
                return False, "register size mismatch"
            if isinstance(other, ParseResult.StringValue) and self.register in REGISTER_BYTES:
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
            return hash((self.datatype, self.address, self.length, self.name))

        def __eq__(self, other: Any) -> bool:
            if not isinstance(other, ParseResult.MemMappedValue):
                return NotImplemented
            elif self is other:
                return True
            else:
                return other.datatype == self.datatype and other.address == self.address and \
                       other.length == self.length and other.name == self.name

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
        pass

    class Comment(_AstNode):
        def __init__(self, text: str) -> None:
            self.text = text

    class Label(_AstNode):
        def __init__(self, name: str, lineno: int) -> None:
            self.name = name
            self.lineno = lineno

    class AssignmentStmt(_AstNode):
        def __init__(self, leftvalues: List['ParseResult.Value'], right: 'ParseResult.Value', lineno: int) -> None:
            self.leftvalues = leftvalues
            self.right = right
            self.lineno = lineno

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

    class ReturnStmt(_AstNode):
        def __init__(self, a: Optional['ParseResult.Value']=None,
                     x: Optional['ParseResult.Value']=None,
                     y: Optional['ParseResult.Value']=None) -> None:
            self.a = a
            self.x = x
            self.y = y

    class IncrDecrStmt(_AstNode):
        def __init__(self, what: 'ParseResult.Value', howmuch: int) -> None:
            self.what = what
            self.howmuch = howmuch

    class CallStmt(_AstNode):
        def __init__(self, lineno: int, target: Optional['ParseResult.Value']=None, *,
                     address: Optional[int]=None, arguments: List[Tuple[str, Any]]=None,
                     is_goto: bool=False, preserve_regs: bool=True) -> None:
            self.lineno = lineno
            self.target = target
            self.address = address
            self.arguments = arguments
            self.is_goto = is_goto
            self.preserve_regs = preserve_regs

        def desugar_call_arguments(self, parser: 'Parser') -> List['ParseResult._AstNode']:
            if not self.arguments:
                return [self]
            statements = []     # type: List[ParseResult._AstNode]
            for name, value in self.arguments:
                assert name is not None, "all call arguments should have a name or be matched on a named parameter"
                assignment = parser.parse_assignment("{:s}={:s}".format(name, value))
                assignment.lineno = self.lineno
                statements.append(assignment)
            statements.append(self)
            return statements

    class InlineAsm(_AstNode):
        def __init__(self, lineno: int, asmlines: List[str]) -> None:
            self.lineno = lineno
            self.asmlines = asmlines

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


class Parser:
    def __init__(self, filename: str, outputdir: str, sourcelines: List[Tuple[int, str]]=None,
                 parsing_import: bool=False, ppsymbols: SymbolTable=None) -> None:
        self.result = ParseResult(filename)
        self.sourceref = SourceRef(filename, -1, 0)
        if sourcelines:
            self.lines = sourcelines
        else:
            self.lines = self.load_source(filename)
        self.outputdir = outputdir
        self.parsing_import = parsing_import     # are we parsing a import file?
        self.cur_lineidx = -1
        self.cur_block = None  # type: ParseResult.Block
        self.root_scope = SymbolTable("<root>", None, None)
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
            print()
            if x.sourcetext:
                print("\tsource text: '{:s}'".format(x.sourcetext))
                if x.sourceref.column:
                    print("\t" + ' '*x.sourceref.column + '             ^')
            if self.parsing_import:
                print("Error (in imported file):", str(x))
            else:
                print("Error:", str(x))
            raise   # XXX temporary solution to get stack trace info in the event of parse errors
        except Exception as x:
            print("\nERROR: internal parser error: ", x)
            print("    file:", self.sourceref.file, "block:", self.cur_block.name, "line:", self.sourceref.line)
            raise   # XXX temporary solution to get stack trace info in the event of parse errors

    def parse_file(self) -> ParseResult:
        print("\nparsing", self.sourceref.file)
        self._parse_1()
        self._parse_2()
        return self.result

    def print_warning(self, text: str) -> None:
        print(text)

    def _parse_comments(self) -> None:
        while True:
            line = self.next_line().lstrip()
            if line.startswith(';'):
                self.cur_block.statements.append(ParseResult.Comment(line))
                continue
            self.prev_line()
            break

    def _parse_1(self) -> None:
        self.cur_block = ParseResult.Block("<header>", self.sourceref, self.root_scope)
        self.result.add_block(self.cur_block)
        self.parse_header()
        zeropage.configure(self.result.clobberzp)
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
            for block in self.result.blocks:
                if block.name == "main":
                    if "start" not in block.label_names:
                        self.sourceref.line = block.sourceref.line
                        self.sourceref.column = 0
                        raise self.PError("The 'main' block should contain the program entry point 'start'")
                    if not any(s for s in block.statements if isinstance(s, ParseResult.ReturnStmt)):
                        self.print_warning("warning: {}: The 'main' block is lacking a return statement.".format(block.sourceref))
                    break
            else:
                raise self.PError("A block named 'main' should be defined for the program's entry point 'start'")

    def _parse_2(self) -> None:
        # parsing pass 2
        self.cur_block = None
        self.sourceref.line = -1
        self.sourceref.column = 0
        for block in self.result.blocks:
            self.cur_block = block
            # create parameter loads for calls
            for index, stmt in enumerate(list(block.statements)):
                if isinstance(stmt, ParseResult.CallStmt):
                    self.sourceref.line = stmt.lineno
                    self.sourceref.column = 0
                    statements = stmt.desugar_call_arguments(self)
                    if len(statements) == 1:
                        block.statements[index] = statements[0]
                    else:
                        block.statements[index] = statements    # type: ignore
            block.flatten_statement_list()
            # desugar immediate string value assignments
            for index, stmt in enumerate(list(block.statements)):
                if isinstance(stmt, ParseResult.AssignmentStmt):
                    self.sourceref.line = stmt.lineno
                    self.sourceref.column = 0
                    stmt.desugar_immediate_string(self)

    def next_line(self) -> str:
        self.cur_lineidx += 1
        try:
            self.sourceref.line, line = self.lines[self.cur_lineidx]
            self.sourceref.column = 0
            return line
        except IndexError:
            return ""

    def prev_line(self) -> str:
        self.cur_lineidx -= 1
        self.sourceref.line, line = self.lines[self.cur_lineidx]
        return line

    def peek_next_line(self) -> str:
        if (self.cur_lineidx + 1) < len(self.lines):
            return self.lines[self.cur_lineidx + 1][1]
        return ""

    def PError(self, message: str, lineno: int=0, column: int=0) -> ParseError:
        sourceline = ""
        if lineno:
            for num, text in self.lines:
                if num == lineno:
                    sourceline = text.strip()
                    break
        else:
            lineno = self.sourceref.line
            column = self.sourceref.column
            self.cur_lineidx = min(self.cur_lineidx, len(self.lines) - 1)
            if self.cur_lineidx:
                sourceline = self.lines[self.cur_lineidx][1].strip()
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
        while True:
            self._parse_comments()
            line = self.next_line()
            if line.startswith("output"):
                if output_specified:
                    raise self.PError("multiple occurrences of 'output'")
                output_specified = True
                _, _, arg = line.partition(" ")
                arg = arg.lstrip()
                self.result.with_sys = False
                self.result.format = ProgramFormat.RAW
                if arg == "raw":
                    pass
                elif arg == "prg":
                    self.result.format = ProgramFormat.PRG
                elif arg.replace(' ', '') == "prg,sys":
                    self.result.with_sys = True
                    self.result.format = ProgramFormat.PRG
                else:
                    raise self.PError("invalid output format")
            elif line.startswith("clobberzp"):
                if self.result.clobberzp:
                    raise self.PError("multiple occurrences of 'clobberzp'")
                self.result.clobberzp = True
                _, _, arg = line.partition(" ")
                arg = arg.lstrip()
                if arg == "restore":
                    self.result.restorezp = True
                elif arg == "":
                    pass
                else:
                    raise self.PError("invalid arg for clobberzp")
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
        return Parser(filename, outputdir, parsing_import=True)

    def parse_block(self) -> ParseResult.Block:
        # first line contains block header "~ [name] [addr]" followed by a '{'
        self._parse_comments()
        line = self.next_line()
        block_def_lineno = self.sourceref.line
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
        while True:
            self._parse_comments()
            line = self.next_line()
            unstripped_line = line
            line = line.strip()
            if line == "}":
                if is_zp_block and any(b.name == "ZP" for b in self.result.blocks):
                    return None     # we already have the ZP block
                if not self.cur_block.name and not self.cur_block.address:
                    self.print_warning("warning: {}: Ignoring block without name and address.".format(self.cur_block.sourceref))
                    return None
                return self.cur_block
            if line.startswith(("var ", "var\t")):
                self.parse_var_def(line)
            elif line.startswith(("const ", "const\t")):
                self.parse_const_def(line)
            elif line.startswith(("memory ", "memory\t")):
                self.parse_memory_def(line, is_zp_block)
            elif line.startswith(("subx ", "subx\t")):
                if is_zp_block:
                    raise self.PError("ZP block cannot contain subroutines")
                self.parse_subx_def(line)
            elif line.startswith(("asminclude ", "asminclude\t", "asmbinary ", "asmbinary\t")):
                if is_zp_block:
                    raise self.PError("ZP block cannot contain assembler directives")
                self.cur_block.statements.append(self.parse_asminclude(line))
            elif line.startswith(("asm ", "asm\t")):
                if is_zp_block:
                    raise self.PError("ZP block cannot contain code statements")
                self.prev_line()
                self.cur_block.statements.append(self.parse_asm())
            elif unstripped_line.startswith((" ", "\t")):
                if is_zp_block:
                    raise self.PError("ZP block cannot contain code statements")
                self.cur_block.statements.append(self.parse_statement(line))
            elif line:
                if is_zp_block:
                    raise self.PError("ZP block cannot contain code labels")
                self.parse_label(line)
            else:
                raise self.PError("missing } to close block from line " + str(self.cur_block.sourceref.line))

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
            raise self.PError("address must lie in zeropage $00-$ff")
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

    def parse_subx_def(self, line: str) -> None:
        match = re.match(r"^subx\s+(?P<name>\w+)\s+"
                         r"\((?P<parameters>[\w\s:,]*)\)"
                         r"\s*->\s*"
                         r"\((?P<results>[\w\s?,]*)\)\s*"
                         r"(?P<decltype>\s+=\s+(?P<address>\S*)|{)\s*$", line)
        if not match:
            raise self.PError("invalid subx declaration")
        code_decl = match.group("decltype") == "{"
        name, parameterlist, resultlist, address_str = \
            match.group("name"), match.group("parameters"), match.group("results"), match.group("address")
        parameters = [(match.group("name"), match.group("target"))
                      for match in re.finditer(r"(?:(?:(?P<name>[\w]+)\s*:\s*)?(?P<target>[\w]+))(?:,|$)", parameterlist)]
        for _, regs in parameters:
            if regs not in REGISTER_SYMBOLS:
                raise self.PError("invalid register(s) in parameter or return values")
        all_paramnames = [p[0] for p in parameters if p[0]]
        if len(all_paramnames) != len(set(all_paramnames)):
            raise self.PError("duplicates in parameter names")
        results = {match.group("name") for match in re.finditer(r"\s*(?P<name>(?:\w+)\??)\s*(?:,|$)", resultlist)}
        subroutine_block = None
        if code_decl:
            address = None
            # parse the subroutine code lines (until the closing '}')
            subroutine_block = ParseResult.Block(name, self.sourceref, self.cur_block.symbols)
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
                if line.startswith(("subx ", "subx\t")):
                    raise self.PError("cannot nest subroutines")
                elif line.startswith(("asm ", "asm\t")):
                    self.prev_line()
                    subroutine_block.statements.append(self.parse_asm())
                elif unstripped_line.startswith((" ", "\t")):
                    subroutine_block.statements.append(self.parse_statement(line))
                elif line:
                    self.parse_label(line)
                else:
                    raise self.PError("missing } to close subroutine from line " + str(subroutine_block.sourceref.line))
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
        match = re.match(r"(?P<goto>goto\s+)?(?P<subname>[\S]+?)\s*(?P<fcall>[!]?)\s*(\((?P<arguments>.*)\))?\s*$", line)
        if match:
            # subroutine or goto call
            is_goto = bool(match.group("goto"))
            preserve = not bool(match.group("fcall"))
            subname = match.group("subname")
            arguments = match.group("arguments")
            if is_goto:
                return self.parse_call_or_goto(subname, arguments, preserve, True)
            elif arguments or match.group(4):
                return self.parse_call_or_goto(subname, arguments, preserve, False)
        if line == "return" or line.startswith(("return ", "return\t")):
            return self.parse_return(line)
        elif line.endswith(("++", "--")):
            incr = line.endswith("++")
            what = self.parse_expression(line[:-2].rstrip())
            if isinstance(what, ParseResult.IntegerValue):
                raise self.PError("cannot in/decrement a constant value")
            return ParseResult.IncrDecrStmt(what, 1 if incr else -1)
        else:
            # perhaps it is an assignment statment
            lhs, sep, rhs = line.partition("=")
            if sep:
                return self.parse_assignment(line)
            raise self.PError("invalid statement")

    def parse_call_or_goto(self, targetstr: str, argumentstr: str, preserve_regs=True, is_goto=False) -> ParseResult.CallStmt:
        argumentstr = argumentstr.strip() if argumentstr else ""
        arguments = None
        if argumentstr:
            arguments = []
            for part in argumentstr.split(','):
                pname, sep, pvalue = part.partition('=')
                pname = pname.strip()
                pvalue = pvalue.strip()
                if sep:
                    arguments.append((pname, pvalue))
                else:
                    arguments.append((None, pname))
        target = None  # type: ParseResult.Value
        if targetstr[0] == '[' and targetstr[-1] == ']':
            # indirect call to address in register pair or memory location
            targetstr, target = self.parse_indirect_value(targetstr)
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
        _, symbol = self.cur_block.lookup(targetstr)
        if isinstance(symbol, SubroutineDef):
            # verify subroutine arguments
            if len(arguments or []) != len(symbol.parameters):
                raise self.PError("invalid number of arguments ({:d}, expected {:d})"
                                  .format(len(arguments or []), len(symbol.parameters)))
            args_with_pnames = []
            for i, (argname, value) in enumerate(arguments or []):
                pname, preg = symbol.parameters[i]
                if argname:
                    if argname != preg:
                        raise self.PError("parameter mismatch ({:s}, expected {:s})".format(argname, preg))
                else:
                    argname = preg
                args_with_pnames.append((argname, value))
            arguments = args_with_pnames
        else:
            if arguments:
                raise self.PError("call cannot take any arguments here, use a subroutine for that")
        if arguments:
            # verify that all arguments have gotten a name
            if any(not a[0] for a in arguments):
                raise self.PError("all call arguments should have a name or be matched on a named parameter")
        if isinstance(target, (type(None), ParseResult.Value)):
            if is_goto:
                return ParseResult.CallStmt(self.sourceref.line, target, address=address, arguments=arguments, is_goto=True)
            else:
                return ParseResult.CallStmt(self.sourceref.line, target, address=address, arguments=arguments, preserve_regs=preserve_regs)
        else:
            raise TypeError("target should be a Value", target)

    def parse_integer(self, text: str) -> int:
        text = text.strip()
        if text.startswith('$'):
            return int(text[1:], 16)
        if text.startswith('%'):
            return int(text[1:], 2)
        return int(text)

    def parse_assignment(self, line: str) -> ParseResult.AssignmentStmt:
        # parses assigning a value to one or more targets
        parts = line.split("=")
        rhs = parts.pop()
        l_values = [self.parse_expression(part) for part in parts]
        if any(isinstance(lv, ParseResult.IntegerValue) for lv in l_values):
            raise self.PError("can't have a constant as assignment target, did you mean [name] instead?")
        r_value = self.parse_expression(rhs)
        for lv in l_values:
            assignable, reason = lv.assignable_from(r_value)
            if not assignable:
                raise self.PError("cannot assign {0} to {1}; {2}".format(r_value, lv, reason))
            if lv.datatype in (DataType.BYTE, DataType.WORD, DataType.MATRIX):
                if isinstance(r_value, ParseResult.FloatValue):
                    truncated, value = self.coerce_value(self.sourceref, lv.datatype, r_value.value)
                    if truncated:
                        r_value = ParseResult.IntegerValue(int(value), datatype=lv.datatype, name=r_value.name)
        return ParseResult.AssignmentStmt(l_values, r_value, self.sourceref.line)

    def parse_return(self, line: str) -> ParseResult.ReturnStmt:
        parts = line.split(maxsplit=1)
        if parts[0] != "return":
            raise self.PError("invalid statement, return expected")
        a = x = y = None
        values = []  # type: List[str]
        if len(parts) > 1:
            values = parts[1].split(",")
        if len(values) == 0:
            return ParseResult.ReturnStmt()
        else:
            a = self.parse_expression(values[0]) if values[0] else None
            if len(values) > 1:
                x = self.parse_expression(values[1]) if values[1] else None
                if len(values) > 2:
                    y = self.parse_expression(values[2]) if values[2] else None
                    if len(values) > 3:
                        raise self.PError("too many returnvalues")
        return ParseResult.ReturnStmt(a, x, y)

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
                return ParseResult.InlineAsm(lineno, asmlines)
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
            return ParseResult.InlineAsm(self.sourceref.line, lines)
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
            return ParseResult.InlineAsm(self.sourceref.line, lines)
        else:
            raise self.PError("invalid statement")

    def parse_expression(self, text: str, is_indirect=False) -> ParseResult.Value:
        # parse an expression into whatever it is (primitive value, register, memory, register, etc)
        # @todo only numeric expressions supported for now
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
        elif text[0] in "-.0123456789$%":
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
        elif text in REGISTER_BYTES:
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
            symblock, sym = self.lookup(text)
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
                name = sym.name if symblock is self.cur_block else sym.blockname + '.' + sym.name
                return ParseResult.MemMappedValue(sym.address, DataType.WORD, 1, name, True)
            else:
                raise self.PError("invalid symbol type")
        elif text.startswith('[') and text.endswith(']'):
            return self.parse_indirect_value(text)[1]
        else:
            raise self.PError("invalid value '" + text + "'")

    def parse_indirect_value(self, text: str) -> Tuple[str, ParseResult.IndirectValue]:
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
            if type_modifier and expr.datatype != type_modifier:
                raise self.PError("invalid type modifier for the value's datatype, must be " + expr.datatype.name)
        return indirect, ParseResult.IndirectValue(expr, type_modifier)

    def is_identifier(self, name: str) -> bool:
        if name.isidentifier():
            return True
        blockname, sep, name = name.partition(".")
        if sep:
            return blockname.isidentifier() and name.isidentifier()
        return False

    def lookup(self, dottedname: str) -> Tuple[ParseResult.Block, Union[SymbolDefinition, SymbolTable]]:
        symblock, sym = self.cur_block.lookup(dottedname)
        if sym is None:
            # symbol is not (yet) known in current block, see if the ppsymbols know about it
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
                self.print_warning("warning: {}: Float value truncated.".format(sourceref))
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


class Optimizer:
    def __init__(self, parseresult: ParseResult) -> None:
        self.parsed = parseresult

    def optimize(self) -> ParseResult:
        print("\noptimizing parse tree")
        for block in self.parsed.blocks:
            self.combine_assignments_into_multi(block)
            self.optimize_multiassigns(block)
        return self.parsed

    def combine_assignments_into_multi(self, block: ParseResult.Block) -> None:
        # fold multiple consecutive assignments with the same rvalue into one multi-assignment
        statements = []   # type: List[ParseResult._AstNode]
        multi_assign_statement = None
        for stmt in block.statements:
            if isinstance(stmt, ParseResult.AssignmentStmt):
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
                stmt.leftvalues = list(sorted(lvalues, key=value_sortkey))


def value_sortkey(value: ParseResult.Value) -> int:
    if isinstance(value, ParseResult.RegisterValue):
        num = 0
        for char in value.register:
            num *= 100
            num += ord(char)
        return num
    elif isinstance(value, ParseResult.MemMappedValue):
        if value.address < 0x100:
            return 10000 + value.address
        else:
            return 20000 + value.address
    else:
        return 99999999


if __name__ == "__main__":
    p = Parser("readme.txt", outputdir="output")
    p.cur_block = ParseResult.Block("test", SourceRef("testfile", 1), None)
    p.parse_subx_def("subx  SUBNAME   (A, test2:XY, X) -> (A?, X) = $c000")
    sub = list(p.cur_block.symbols.iter_subroutines())[0]
    import pprint
    pprint.pprint(vars(sub))
