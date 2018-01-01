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
from typing import Set, List, Tuple, Optional, Dict, Union, Generator
from .exprparse import ParseError, parse_expr_as_int, parse_expr_as_number, parse_expr_as_primitive,\
    parse_expr_as_string, parse_arguments, parse_expr_as_comparison
from .astdefs import *
from .symbols import SourceRef, SymbolTable, DataType, SymbolDefinition, SubroutineDef, LabelDef, \
    Zeropage, char_to_bytevalue, \
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
        self.blocks = []          # type: List[Block]
        self.subroutine_usage = defaultdict(set)    # type: Dict[Tuple[str, str], Set[str]]
        self.zeropage = Zeropage()
        self.preserve_registers = False

    def all_blocks(self) -> Generator[Block, None, None]:
        for block in self.blocks:
            yield block
            for sub in block.symbols.iter_subroutines(True):
                yield sub.sub_block

    def add_block(self, block: Block, position: Optional[int]=None) -> None:
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
    def __init__(self, filename: str, outputdir: str, existing_imports: Set[str], parsing_import: bool = False,
                 sourcelines: List[Tuple[int, str]] = None, ppsymbols: SymbolTable = None, sub_usage: Dict=None) -> None:
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
        self.cur_block = None  # type: Block
        self.root_scope = SymbolTable("<root>", None, None)
        self.root_scope.set_zeropage(self.result.zeropage)
        self.ppsymbols = ppsymbols  # symboltable from preprocess phase
        self.print_block_parsing = True
        self.existing_imports = existing_imports
        self.parse_errors = 0

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
            result = self.parse_file()
        except ParseError as x:
            self.handle_parse_error(x)
        except Exception as x:
            if sys.stderr.isatty():
                print("\x1b[1m", file=sys.stderr)
            print("\nERROR: internal parser error: ", x, file=sys.stderr)
            if self.cur_block:
                print("    file:", self.sourceref.file, "block:", self.cur_block.name, "line:", self.sourceref.line, file=sys.stderr)
            else:
                print("    file:", self.sourceref.file, file=sys.stderr)
            if sys.stderr.isatty():
                print("\x1b[0m", file=sys.stderr, end="", flush=True)
            raise
        if self.parse_errors:
            self.print_bold("\nNo output; there were {:d} errors in file {:s}\n".format(self.parse_errors, self.sourceref.file))
            raise SystemExit(1)
        return result

    def handle_parse_error(self, exc: ParseError) -> None:
        self.parse_errors += 1
        if sys.stderr.isatty():
            print("\x1b[1m", file=sys.stderr)
        if exc.sourcetext:
            print("\t" + exc.sourcetext, file=sys.stderr)
            if exc.sourceref.column:
                print("\t" + ' ' * exc.sourceref.column + '             ^', file=sys.stderr)
        if self.parsing_import:
            print("Error (in imported file):", str(exc), file=sys.stderr)
        else:
            print("Error:", str(exc), file=sys.stderr)
        if sys.stderr.isatty():
            print("\x1b[0m", file=sys.stderr, end="", flush=True)

    def parse_file(self) -> ParseResult:
        print("\nparsing", self.sourceref.file)
        self._parse_1()
        self._parse_import_file("il65lib")      # compiler support library is always imported.
        self._parse_2()
        return self.result

    def print_warning(self, text: str, sourceref: SourceRef=None) -> None:
        self.print_bold("warning: {}: {:s}".format(sourceref or self.sourceref, text))

    def print_bold(self, text: str) -> None:
        if sys.stdout.isatty():
            print("\x1b[1m" + text + "\x1b[0m", flush=True)
        else:
            print(text)

    def _parse_comments(self) -> None:
        while True:
            line = self.next_line().lstrip()
            if line.startswith(';'):
                self.cur_block.statements.append(Comment(line, self.sourceref))
                continue
            self.prev_line()
            break

    def _parse_1(self) -> None:
        self.cur_block = Block("<header>", self.sourceref, self.root_scope, self.result.preserve_registers)
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
            elif next_line.startswith(("%import ", "%import\t")):
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

    def _check_return_statement(self, block: Block, message: str) -> None:
        # find last statement that isn't a comment
        for stmt in reversed(block.statements):
            if isinstance(stmt, Comment):
                continue
            if isinstance(stmt, ReturnStmt) or isinstance(stmt, CallStmt) and stmt.is_goto:
                return
            if isinstance(stmt, InlineAsm):
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

    _immediate_floats = {}  # type: Dict[float, Tuple[str, str]]

    def _parse_2(self) -> None:
        # parsing pass 2 (not done during preprocessing!)
        self.cur_block = None
        self.sourceref.line = -1
        self.sourceref.column = 0

        def desugar_immediate_strings(stmt: _AstNode, containing_block: Block) -> None:
            if isinstance(stmt, CallStmt):
                for s in stmt.desugared_call_arguments:
                    self.sourceref = s.sourceref.copy()
                    s.desugar_immediate_string(containing_block)
                for s in stmt.desugared_output_assignments:
                    self.sourceref = s.sourceref.copy()
                    s.desugar_immediate_string(containing_block)
            if isinstance(stmt, AssignmentStmt):
                self.sourceref = stmt.sourceref.copy()
                stmt.desugar_immediate_string(containing_block)

        def desugar_immediate_floats(stmt: _AstNode, containing_block: Block) -> None:
            if isinstance(stmt, (InplaceIncrStmt, InplaceDecrStmt)):
                howmuch = stmt.value.value
                if howmuch is None:
                    assert stmt.value.name
                    return
                if howmuch in (0, 1) or type(howmuch) is int:
                    return      # 1 is special cased in the code generator
                rom_floats = {
                    1: "c64.FL_FONE",
                    .25: "c64.FL_FR4",
                    .5: "c64.FL_FHALF",
                    -.5: "c64.FL_NEGHLF",
                    10: "c64.FL_TENC",
                    -32768: "c64.FL_N32768",
                    1e9: "c64.FL_NZMIL",
                    math.pi: "c64.FL_PIVAL",
                    math.pi / 2: "c64.FL_PIHALF",
                    math.pi * 2: "c64.FL_TWOPI",
                    math.sqrt(2)/2.0: "c64.FL_SQRHLF",
                    math.sqrt(2): "c64.FL_SQRTWO",
                    math.log(2): "c64.FL_LOG2",
                    1.0 / math.log(2): "c64.FL_LOGEB2",
                }
                for fv, name in rom_floats.items():
                    if math.isclose(howmuch, fv, rel_tol=0, abs_tol=1e-9):
                        # use one of the constants available in ROM
                        stmt.value.name = name
                        return
                if howmuch in self._immediate_floats:
                    # reuse previously defined float constant
                    blockname, floatvar_name = self._immediate_floats[howmuch]
                    if blockname:
                        stmt.value.name = blockname + '.' + floatvar_name
                    else:
                        stmt.value.name = floatvar_name
                else:
                    # define new float variable to hold the incr/decr value
                    # note: not a constant, because we need the MFLT bytes
                    floatvar_name = "il65_float_{:d}".format(id(stmt))
                    containing_block.symbols.define_variable(floatvar_name, stmt.sourceref, DataType.FLOAT, value=howmuch)
                    self._immediate_floats[howmuch] = (containing_block.name, floatvar_name)
                    stmt.value.name = floatvar_name

        for block in self.result.blocks:
            self.cur_block = block
            self.sourceref = block.sourceref.copy()
            self.sourceref.column = 0
            for _, sub, stmt in block.all_statements():
                if isinstance(stmt, CallStmt):
                    self.sourceref = stmt.sourceref.copy()
                    self.desugar_call_arguments_and_outputs(stmt)
                desugar_immediate_strings(stmt, self.cur_block)
                desugar_immediate_floats(stmt, self.cur_block)

    def desugar_call_arguments_and_outputs(self, stmt: CallStmt) -> None:
        stmt.desugared_call_arguments.clear()
        stmt.desugared_output_assignments.clear()
        for name, value in stmt.arguments or []:
            assert name is not None, "all call arguments should have a name or be matched on a named parameter"
            assignment = self.parse_assignment(name, value)
            assignment.sourceref = stmt.sourceref.copy()
            if assignment.leftvalues[0].datatype != DataType.BYTE:
                if isinstance(assignment.right, IntegerValue) and assignment.right.constant:
                    # a call that doesn't expect a BYTE argument but gets one, converted from a 1-byte string most likely
                    if value.startswith("'") and value.endswith("'"):
                        self.print_warning("possible problematic string to byte conversion (use a .text var instead?)")
            if not assignment.is_identity():
                stmt.desugared_call_arguments.append(assignment)
        if all(not isinstance(v, RegisterValue) for r, v in stmt.outputvars or []):
            # if none of the output variables are registers, we can simply generate the assignments without issues
            for register, value in stmt.outputvars or []:
                rvalue = self.parse_expression(register)
                assignment = AssignmentStmt([value], rvalue, stmt.sourceref)
                stmt.desugared_output_assignments.append(assignment)
        else:
            result_reg_mapping = [(register, value.register, value) for register, value in stmt.outputvars or []
                                  if isinstance(value, RegisterValue)]
            if any(r[0] != r[1] for r in result_reg_mapping):
                # not all result parameter registers line up with the correct order of registers in the statement,
                # reshuffling call results is not supported yet.
                raise self.PError("result registers and/or their ordering is not the same as in the "
                                  "subroutine definition, this isn't supported yet")
            else:
                # no register alignment issues, just generate the assignments
                # note: do not remove the identity assignment here or the output register handling generates buggy code
                for register, value in stmt.outputvars or []:
                    rvalue = self.parse_expression(register)
                    assignment = AssignmentStmt([value], rvalue, stmt.sourceref)
                    stmt.desugared_output_assignments.append(assignment)

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
        preserve_specified = False
        while True:
            self._parse_comments()
            line = self.next_line()
            if line.startswith('%'):
                directive = line.split(maxsplit=1)[0][1:]
                if directive == "output":
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
                    continue
                elif directive == "zp":
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
                    continue
                elif directive == "address":
                    if self.result.start_address:
                        raise self.PError("multiple occurrences of 'address'")
                    _, _, arg = line.partition(" ")
                    try:
                        self.result.start_address = parse_expr_as_int(arg, None, None, self.sourceref)
                    except ParseError:
                        raise self.PError("invalid address")
                    if self.result.format == ProgramFormat.PRG and self.result.with_sys and self.result.start_address != 0x0801:
                        raise self.PError("cannot use non-default 'address' when output format includes basic SYS program")
                    continue
                elif directive == "preserve_registers":
                    if preserve_specified:
                        raise self.PError("can only specify preserve_registers option once")
                    preserve_specified = True
                    _, _, optionstr = line.partition(" ")
                    self.result.preserve_registers = optionstr in ("", "true", "yes")
                    continue
                elif directive == "import":
                    break    # the first import directive actually is not part of the header anymore
                else:
                    raise self.PError("invalid directive")
            break # no more directives, header parsing finished!
        self.prev_line()
        if not self.result.start_address:
            # set the proper default start address
            if self.result.format == ProgramFormat.PRG:
                self.result.start_address = 0x0801  # normal C-64 basic program start address
            elif self.result.format == ProgramFormat.RAW:
                self.result.start_address = 0xc000  # default start for raw assembly
        if self.result.format == ProgramFormat.PRG and self.result.with_sys and self.result.start_address != 0x0801:
            raise self.PError("cannot use non-default 'address' when output format includes basic SYS program")

    def parse_import(self) -> None:
        line = self.next_line()
        line = line.lstrip()
        if not line.startswith(("%import ", "%import\t")):
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
        self._parse_import_file(filename)

    def _parse_import_file(self, filename: str) -> None:
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
                if not self.check_import_okay(filename):
                    return
                self.print_import_progress("importing", filename)
                parser = self.create_import_parser(filename, self.outputdir)
                result = parser.parse()
                self.print_import_progress("\ncontinuing", self.sourceref.file)
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

    def print_import_progress(self, message: str, *args: str) -> None:
        print(message, *args)

    def create_import_parser(self, filename: str, outputdir: str) -> 'Parser':
        return Parser(filename, outputdir, self.existing_imports, True, ppsymbols=self.ppsymbols, sub_usage=self.result.subroutine_usage)

    def parse_block(self) -> Optional[Block]:
        # first line contains block header "~ [name] [addr]" followed by a '{'
        self._parse_comments()
        line = self.next_line()
        line = line.lstrip()
        if not line.startswith("~"):
            raise self.PError("expected '~' (block)")
        block_args = line[1:].split()
        arg = ""
        self.cur_block = Block("", self.sourceref.copy(), self.root_scope, self.result.preserve_registers)
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
                    self.cur_block = Block(arg, self.sourceref.copy(), self.root_scope, self.result.preserve_registers)
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
            try:
                go_on, resultblock = self._parse_block_statement(is_zp_block)
                if not go_on:
                    return resultblock
            except ParseError as x:
                self.handle_parse_error(x)

    def _parse_block_statement(self, is_zp_block: bool) -> Tuple[bool, Optional[Block]]:
        # parse the statements inside a block
        self._parse_comments()
        line = self.next_line()
        unstripped_line = line
        line = line.strip()
        if line.startswith('%'):
            directive, _, optionstr = line.partition(" ")
            directive = directive[1:]
            self.cur_block.preserve_registers = optionstr in ("", "true", "yes")
            if directive in ("asminclude", "asmbinary"):
                if is_zp_block:
                    raise self.PError("ZP block cannot contain assembler directives")
                self.cur_block.statements.append(self.parse_asminclude(line))
            elif directive == "asm":
                if is_zp_block:
                    raise self.PError("ZP block cannot contain code statements")
                self.prev_line()
                self.cur_block.statements.append(self.parse_asm())
            elif directive == "breakpoint":
                self.cur_block.statements.append(BreakpointStmt(self.sourceref))
                self.print_warning("breakpoint defined")
            elif directive == "preserve_registers":
                self.result.preserve_registers = optionstr in ("", "true", "yes")
            else:
                raise self.PError("invalid directive")
        elif line == "}":
            if is_zp_block and any(b.name == "ZP" for b in self.result.blocks):
                return False, None     # we already have the ZP block
            if self.cur_block.ignore:
                self.print_warning("ignoring block without name and address", self.cur_block.sourceref)
                return False, None
            return False, self.cur_block
        elif line.startswith(("var ", "var\t")):
            self.parse_var_def(line)
        elif line.startswith(("const ", "const\t")):
            self.parse_const_def(line)
        elif line.startswith(("memory ", "memory\t")):
            self.parse_memory_def(line, is_zp_block)
        elif line.startswith(("sub ", "sub\t")):
            if is_zp_block:
                raise self.PError("ZP block cannot contain subroutines")
            self.parse_subroutine_def(line)
        elif unstripped_line.startswith((" ", "\t")):
            if line.endswith("{"):
                raise self.PError("invalid statement")
            if is_zp_block:
                raise self.PError("ZP block cannot contain code statements")
            self.cur_block.statements.append(self.parse_statement(line))
        elif line:
            if is_zp_block:
                raise self.PError("ZP block cannot contain code labels")
            self.parse_label(line)
        else:
            raise self.PError("invalid statement in block")
        return True, None   # continue with more statements

    def parse_label(self, line: str) -> None:
        label_line = line.split(maxsplit=1)
        if str.isidentifier(label_line[0]):
            labelname = label_line[0]
            if labelname in self.cur_block.label_names:
                raise self.PError("label already defined")
            if labelname in self.cur_block.symbols:
                raise self.PError("symbol already defined")
            self.cur_block.symbols.define_label(labelname, self.sourceref)
            self.cur_block.statements.append(Label(labelname, self.sourceref))
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
        if not results:
            if resultlist == "?":
                # a single '?' in the result spec means: all 3 registers clobbered
                results = ['A?', 'X?', 'Y?']
            elif resultlist:
                raise self.PError("invalid return values spec")
        subroutine_block = None
        if code_decl:
            address = None
            # parse the subroutine code lines (until the closing '}')
            subroutine_block = Block(self.cur_block.name + "." + name, self.sourceref, self.cur_block.symbols,
                                     self.cur_block.preserve_registers)
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
                elif line.startswith(("%asm ", "%asm\t")):
                    self.prev_line()
                    subroutine_block.statements.append(self.parse_asm())
                elif unstripped_line.startswith((" ", "\t")):
                    if line.endswith("{"):
                        raise self.PError("invalid statement")
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

    def parse_statement(self, line: str) -> _AstNode:
        match = re.fullmatch(r"(?P<if>if(_[a-z]+)?)\s+(?P<cond>.+)?goto\s+(?P<subname>[\S]+?)\s*(\((?P<arguments>.*)\))?\s*", line)
        if match:
            # conditional goto
            groups = match.groupdict()
            subname = groups["subname"]
            if groups["if"] == "if" and not groups["cond"]:
                raise self.PError("need explicit if status when a condition is not present")
            condition = self.parse_if_condition(groups["if"], groups["cond"])
            return self.parse_call_or_goto(subname, groups["arguments"], None, None, True, condition=condition)
        match = re.fullmatch(r"goto\s+(?P<subname>[\S]+?)\s*(\((?P<arguments>.*)\))?\s*", line)
        if match:
            # goto
            groups = match.groupdict()
            subname = groups["subname"]
            return self.parse_call_or_goto(subname, groups["arguments"], None, None, True)
        match = re.fullmatch(r"(?P<outputs>[^\(]*\s*=)?\s*(?P<subname>[\S]+?)\s*(?P<preserve>!\s*[A-Z]*)?\s*(\((?P<arguments>.*)\))?\s*", line)
        if match:
            # subroutine call (not a goto) with possible output param assignment
            groups = match.groupdict()
            preserve = None
            preserve_str = groups["preserve"]
            if preserve_str and preserve_str.startswith('!'):
                preserve_str = preserve_str.replace(' ', '')
                if preserve_str == "!":
                    preserve = {'A', 'X', 'Y'}
                else:
                    preserve = set(preserve_str[1:])
                    for r in preserve:
                        if r not in REGISTER_BYTES:
                            raise self.PError("invalid register in call preservation list")
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
            if isinstance(what, IntegerValue):
                raise self.PError("cannot in/decrement a constant value")
            one_value = IntegerValue(1, self.sourceref)
            if incr:
                return InplaceIncrStmt(what, one_value, self.sourceref)
            return InplaceDecrStmt(what, one_value, self.sourceref)
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
                           preserve_regs: Set[str]=None, is_goto: bool=False, condition: IfCondition=None) -> CallStmt:
        if not is_goto:
            assert condition is None
        argumentstr = argumentstr.strip() if argumentstr else ""
        outputstr = outputstr.strip() if outputstr else ""
        arguments = None
        outputvars = None
        if argumentstr:
            arguments = parse_arguments(argumentstr, self.sourceref)
        target = None  # type: Value
        if targetstr[0] == '[' and targetstr[-1] == ']':
            # indirect call to address in register pair or memory location
            targetstr, target = self.parse_indirect_value(targetstr, True)
            if target.datatype != DataType.WORD:
                raise self.PError("invalid call target (should contain 16-bit)")
        else:
            target = self.parse_expression(targetstr)
        if not isinstance(target, (IntegerValue, MemMappedValue, IndirectValue)):
            raise self.PError("cannot call that type of symbol")
        if isinstance(target, IndirectValue) \
                and not isinstance(target.value, (IntegerValue, RegisterValue, MemMappedValue)):
            raise self.PError("cannot call that type of indirect symbol")
        address = target.address if isinstance(target, MemMappedValue) else None
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
        if isinstance(target, (type(None), Value)):
            # special case for the C-64 lib's print function, to be able to use it with a single character argument
            if target.name == "c64scr.print_string" and len(arguments) == 1 and isinstance(arguments[0], str):
                if arguments[0][1].startswith("'") and arguments[0][1].endswith("'"):
                    target = self.parse_expression("c64.CHROUT")
                    address = target.address
                    outputvars = None
                    _, newsymbol = self.lookup_with_ppsymbols("c64.CHROUT")
                    assert len(newsymbol.parameters) == 1
                    arguments = [(newsymbol.parameters[0][1], arguments[0][1])]
            if is_goto:
                return CallStmt(self.sourceref, target, address=address, arguments=arguments,
                                outputs=outputvars, is_goto=True, condition=condition)
            else:
                return CallStmt(self.sourceref, target, address=address, arguments=arguments,
                                outputs=outputvars, preserve_regs=preserve_regs)
        else:
            raise TypeError("target should be a Value", target)

    def parse_integer(self, text: str) -> int:
        text = text.strip()
        if text.startswith('$'):
            return int(text[1:], 16)
        if text.startswith('%'):
            return int(text[1:], 2)
        return int(text)

    def parse_assignment(self, *parts) -> AssignmentStmt:
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
                if isinstance(r_value, FloatValue):
                    truncated, value = self.coerce_value(self.sourceref, lv.datatype, r_value.value)
                    if truncated:
                        r_value = IntegerValue(int(value), self.sourceref, datatype=lv.datatype, name=r_value.name)
        return AssignmentStmt(l_values, r_value, self.sourceref)

    def parse_augmented_assignment(self, leftstr: str, operator: str, rightstr: str) \
            -> Union[AssignmentStmt, InplaceDecrStmt, InplaceIncrStmt]:
        # parses an augmented assignment (for instance: value += 3)
        if operator not in AugmentedAssignmentStmt.SUPPORTED_OPERATORS:
            raise self.PError("augmented assignment operator '{:s}' not supported".format(operator))
        l_value = self.parse_expression(leftstr)
        r_value = self.parse_expression(rightstr)
        if l_value.constant:
            raise self.PError("can't have a constant as assignment target, perhaps you wanted indirection [...] instead?")
        if l_value.datatype in (DataType.BYTE, DataType.WORD, DataType.MATRIX):
            # truncate the rvalue if needed
            if isinstance(r_value, FloatValue):
                truncated, value = self.coerce_value(self.sourceref, l_value.datatype, r_value.value)
                if truncated:
                    r_value = IntegerValue(int(value), self.sourceref, datatype=l_value.datatype, name=r_value.name)
        if operator in ("+=", "-="):
            # see if we can simplify this to inplace incr/decr statement (only int/float constant values)
            if r_value.constant:
                if not isinstance(r_value, (IntegerValue, FloatValue)):
                    raise self.PError("incr/decr requires constant int or float, not " + r_value.__class__.__name__)
                if operator == "+=":
                    if r_value.value > 0:
                        return InplaceIncrStmt(l_value, r_value, self.sourceref)
                    elif r_value.value < 0:
                        return InplaceDecrStmt(l_value, r_value.negative(), self.sourceref)
                else:
                    if r_value.value > 0:
                        return InplaceDecrStmt(l_value, r_value, self.sourceref)
                    elif r_value.value < 0:
                        return InplaceIncrStmt(l_value, r_value.negative(), self.sourceref)
        return AugmentedAssignmentStmt(l_value, operator, r_value, self.sourceref)

    def parse_return(self, line: str) -> ReturnStmt:
        parts = line.split(maxsplit=1)
        if parts[0] != "return":
            raise self.PError("invalid statement, return expected")
        a = x = y = None
        values = []  # type: List[str]
        if len(parts) > 1:
            values = parts[1].split(",")
        if len(values) == 0:
            return ReturnStmt(self.sourceref)
        else:
            a = self.parse_expression(values[0]) if values[0] else None
            if len(values) > 1:
                x = self.parse_expression(values[1]) if values[1] else None
                if len(values) > 2:
                    y = self.parse_expression(values[2]) if values[2] else None
                    if len(values) > 3:
                        raise self.PError("too many returnvalues")
        return ReturnStmt(self.sourceref, a, x, y)

    def parse_asm(self) -> InlineAsm:
        line = self.next_line()
        aline = line.split()
        if not len(aline) == 2 or aline[0] != "%asm" or aline[1] != "{":
            raise self.PError("invalid asm directive")
        asmlines = []   # type: List[str]
        while True:
            line = self.next_line()
            if line.strip() == "}":
                return InlineAsm(asmlines, self.sourceref)
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

    def parse_asminclude(self, line: str) -> InlineAsm:
        aline = line.split()
        if len(aline) < 2:
            raise self.PError("invalid asminclude or asmbinary directive")
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
        if aline[0] == "%asminclude":
            if len(aline) == 3:
                scopename = aline[2]
                lines = ['{:s}\t.binclude "{:s}"'.format(scopename, filename)]
            else:
                raise self.PError("invalid asminclude directive")
            return InlineAsm(lines, self.sourceref)
        elif aline[0] == "%asmbinary":
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
                raise self.PError("invalid asmbinary directive")
            return InlineAsm(lines, self.sourceref)
        else:
            raise self.PError("invalid statement")

    def parse_expression(self, text: str, is_indirect=False) -> Value:
        # parse an expression into whatever it is (primitive value, register, memory, register, etc)
        text = text.strip()
        if not text:
            raise self.PError("value expected")
        if text[0] == '#':
            if is_indirect:
                raise self.PError("using the address-of something in an indirect value makes no sense")
            # take the pointer (memory address) from the thing that follows this
            expression = self.parse_expression(text[1:])
            if isinstance(expression, StringValue):
                return expression
            elif isinstance(expression, MemMappedValue):
                return IntegerValue(expression.address, self.sourceref, datatype=DataType.WORD, name=expression.name)
            else:
                raise self.PError("cannot take the address of type " + expression.__class__.__name__)
        elif text[0] in "-.0123456789$%~":
            number = parse_expr_as_number(text, self.cur_block.symbols, self.ppsymbols, self.sourceref)
            try:
                if type(number) is int:
                    return IntegerValue(int(number), self.sourceref)
                elif type(number) is float:
                    return FloatValue(number, self.sourceref)
                else:
                    raise TypeError("invalid number type")
            except (ValueError, OverflowError) as ex:
                raise self.PError(str(ex))
        elif text in REGISTER_WORDS:
            return RegisterValue(text, DataType.WORD, self.sourceref)
        elif text in REGISTER_BYTES | REGISTER_SBITS:
            return RegisterValue(text, DataType.BYTE, self.sourceref)
        elif (text.startswith("'") and text.endswith("'")) or (text.startswith('"') and text.endswith('"')):
            strvalue = parse_expr_as_string(text, self.cur_block.symbols, self.ppsymbols, self.sourceref)
            if len(strvalue) == 1:
                petscii_code = char_to_bytevalue(strvalue)
                return IntegerValue(petscii_code, self.sourceref)
            return StringValue(strvalue, self.sourceref)
        elif text == "true":
            return IntegerValue(1, self.sourceref)
        elif text == "false":
            return IntegerValue(0, self.sourceref)
        elif self.is_identifier(text):
            symblock, sym = self.lookup_with_ppsymbols(text)
            if isinstance(sym, (VariableDef, ConstantDef)):
                constant = isinstance(sym, ConstantDef)
                if self.cur_block is symblock:
                    symbolname = sym.name
                else:
                    symbolname = "{:s}.{:s}".format(sym.blockname, sym.name)
                if isinstance(sym, VariableDef) and sym.register:
                    return RegisterValue(sym.register, sym.type, self.sourceref, name=symbolname)
                elif sym.type in (DataType.BYTE, DataType.WORD, DataType.FLOAT):
                    if isinstance(sym, ConstantDef):
                        if sym.type == DataType.FLOAT:
                            return FloatValue(sym.value, self.sourceref, sym.name)   # type: ignore
                        elif sym.type in (DataType.BYTE, DataType.WORD):
                            return IntegerValue(sym.value, self.sourceref, datatype=sym.type, name=sym.name)  # type: ignore
                        elif sym.type in STRING_DATATYPES:
                            return StringValue(sym.value, self.sourceref, sym.name, True)   # type: ignore
                        else:
                            raise TypeError("invalid const type", sym.type)
                    else:
                        return MemMappedValue(sym.address, sym.type, sym.length, self.sourceref, name=symbolname, constant=constant)
                elif sym.type in STRING_DATATYPES:
                    return StringValue(sym.value, self.sourceref, name=symbolname, constant=constant)      # type: ignore
                elif sym.type == DataType.MATRIX:
                    raise self.PError("cannot manipulate matrix directly, use one of the matrix procedures")
                elif sym.type == DataType.BYTEARRAY or sym.type == DataType.WORDARRAY:
                    raise self.PError("cannot manipulate array directly, use one of the array procedures")
                else:
                    raise self.PError("invalid symbol type")
            elif isinstance(sym, LabelDef):
                name = sym.name if symblock is self.cur_block else sym.blockname + '.' + sym.name
                return MemMappedValue(None, DataType.WORD, 1, self.sourceref, name, True)
            elif isinstance(sym, SubroutineDef):
                self.result.sub_used_by(sym, self.sourceref)
                name = sym.name if symblock is self.cur_block else sym.blockname + '.' + sym.name
                return MemMappedValue(sym.address, DataType.WORD, 1, self.sourceref, name, True)
            else:
                raise self.PError("invalid symbol type")
        elif text.startswith('[') and text.endswith(']'):
            return self.parse_indirect_value(text)[1]
        else:
            raise self.PError("invalid single value '" + text + "'")    # @todo understand complex expressions

    def parse_indirect_value(self, text: str, allow_mmapped_for_call: bool=False) -> Tuple[str, IndirectValue]:
        indirect = text[1:-1].strip()
        indirect2, sep, typestr = indirect.rpartition('.')
        type_modifier = None
        if sep:
            if typestr in ("byte", "word", "float"):
                type_modifier, type_len, _ = self.get_datatype(sep + typestr)
                indirect = indirect2
        expr = self.parse_expression(indirect, True)
        if not isinstance(expr, (IntegerValue, MemMappedValue, RegisterValue)):
            raise self.PError("only integers, memmapped vars, and registers can be used in an indirect value")
        if type_modifier is None:
            if isinstance(expr, (RegisterValue, MemMappedValue)):
                type_modifier = expr.datatype
            else:
                type_modifier = DataType.BYTE
        if isinstance(expr, IntegerValue):
            if type_modifier not in (DataType.BYTE, DataType.WORD, DataType.FLOAT):
                raise self.PError("invalid type modifier for the value's datatype")
        elif isinstance(expr, MemMappedValue):
            if allow_mmapped_for_call:
                if type_modifier and expr.datatype != type_modifier:
                    raise self.PError("invalid type modifier for the value's datatype, must be " + expr.datatype.name)
            else:
                raise self.PError("use variable directly instead of using indirect addressing")
        return indirect, IndirectValue(expr, type_modifier, self.sourceref)

    def is_identifier(self, name: str) -> bool:
        if name.isidentifier():
            return True
        blockname, sep, name = name.partition(".")
        if sep:
            return blockname.isidentifier() and name.isidentifier()
        return False

    def lookup_with_ppsymbols(self, dottedname: str) -> Tuple[Block, Union[SymbolDefinition, SymbolTable]]:
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
        if datatype in (DataType.BYTE, DataType.WORD, DataType.MATRIX) and isinstance(value, float):
            frac = math.modf(value)
            if frac != 0:
                self.print_warning("float value truncated ({} to datatype {})".format(value, datatype.name))
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

    def parse_if_condition(self, ifpart: str, conditionpart: str) -> IfCondition:
        if ifpart == "if":
            ifstatus = "true"
        else:
            ifstatus = ifpart[3:]
        if ifstatus not in IfCondition.IF_STATUSES:
            raise self.PError("invalid if form")
        if conditionpart:
            if ifstatus not in ("true", "not", "zero"):
                raise self.PError("can only have if[_true], if_not or if_zero when using a comparison expression")
            left, operator, right = parse_expr_as_comparison(conditionpart, self.sourceref)
            leftv = self.parse_expression(left)
            if not operator and isinstance(leftv, (IntegerValue, FloatValue, StringValue)):
                raise self.PError("condition is a constant value")
            if isinstance(leftv, RegisterValue):
                if leftv.register in {"SC", "SZ", "SI"}:
                    raise self.PError("cannot use a status bit register explicitly in a condition")
            if operator:
                rightv = self.parse_expression(right)
            else:
                rightv = None
            if leftv == rightv:
                raise self.PError("left and right values in comparison are identical")
            result = IfCondition(ifstatus, leftv, operator, rightv, self.sourceref)
        else:
            result = IfCondition(ifstatus, None, "", None, self.sourceref)
        if result.make_if_true():
            self.print_warning("if_not condition inverted to if")
        return result

    def check_import_okay(self, filename: str) -> bool:
        if filename == self.sourceref.file and not filename.endswith("il65lib.ill"):
            raise self.PError("can't import itself")
        if filename in self.existing_imports:
            return False
        self.existing_imports.add(filename)
        return True


class Optimizer:
    def __init__(self, parseresult: ParseResult) -> None:
        self.parsed = parseresult

    def optimize(self) -> ParseResult:
        print("\noptimizing parse tree")
        for block in self.parsed.all_blocks():
            self.remove_augmentedassign_incrdecr_nops(block)
            self.remove_identity_assigns(block)
            self.combine_assignments_into_multi(block)
            self.optimize_multiassigns(block)
            self.remove_unused_subroutines(block)
            self.optimize_compare_with_zero(block)
        return self.parsed

    def remove_augmentedassign_incrdecr_nops(self, block: Block) -> None:
        have_removed_stmts = False
        for index, stmt in enumerate(list(block.statements)):
            if isinstance(stmt, AugmentedAssignmentStmt):
                if isinstance(stmt.right, (IntegerValue, FloatValue)):
                    if stmt.right.value == 0 and stmt.operator in ("+=", "-=", "|=", "<<=", ">>=", "^="):
                        print("{}: removed statement that has no effect".format(stmt.sourceref))
                        have_removed_stmts = True
                        block.statements[index] = None
                    if stmt.right.value >= 8 and stmt.operator in ("<<=", ">>="):
                        print("{}: shifting that many times always results in zero".format(stmt.sourceref))
                        new_stmt = AssignmentStmt(stmt.leftvalues, IntegerValue(0, stmt.sourceref), stmt.sourceref)
                        block.statements[index] = new_stmt
        if have_removed_stmts:
            # remove the Nones
            block.statements = [s for s in block.statements if s is not None]

    def optimize_compare_with_zero(self, block: Block) -> None:
        # a conditional goto that compares a value to zero will be simplified
        # the comparison operator and rvalue (0) will be removed and the if-status changed accordingly
        for stmt in block.statements:
            if isinstance(stmt, CallStmt):
                cond = stmt.condition
                if cond and isinstance(cond.rvalue, (IntegerValue, FloatValue)) and cond.rvalue.value == 0:
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
                        print("{}: simplified comparison with zero".format(stmt.sourceref))

    def combine_assignments_into_multi(self, block: Block) -> None:
        # fold multiple consecutive assignments with the same rvalue into one multi-assignment
        statements = []   # type: List[_AstNode]
        multi_assign_statement = None
        for stmt in block.statements:
            if isinstance(stmt, AssignmentStmt) and not isinstance(stmt, AugmentedAssignmentStmt):
                if multi_assign_statement and multi_assign_statement.right == stmt.right:
                    multi_assign_statement.leftvalues.extend(stmt.leftvalues)
                    print("{}: joined with previous line into multi-assign statement".format(stmt.sourceref))
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

    def optimize_multiassigns(self, block: Block) -> None:
        # optimize multi-assign statements.
        for stmt in block.statements:
            if isinstance(stmt, AssignmentStmt) and len(stmt.leftvalues) > 1:
                # remove duplicates
                lvalues = list(set(stmt.leftvalues))
                if len(lvalues) != len(stmt.leftvalues):
                    print("{}: removed duplicate assignment targets".format(stmt.sourceref))
                # change order: first registers, then zp addresses, then non-zp addresses, then the rest (if any)
                stmt.leftvalues = list(sorted(lvalues, key=_value_sortkey))

    def remove_identity_assigns(self, block: Block) -> None:
        have_removed_stmts = False
        for index, stmt in enumerate(list(block.statements)):
            if isinstance(stmt, AssignmentStmt):
                stmt.remove_identity_lvalues()
                if not stmt.leftvalues:
                    print("{}: removed identity assignment statement".format(stmt.sourceref))
                    have_removed_stmts = True
                    block.statements[index] = None
        if have_removed_stmts:
            # remove the Nones
            block.statements = [s for s in block.statements if s is not None]

    def remove_unused_subroutines(self, block: Block) -> None:
        # some symbols are used by the emitted assembly code from the code generator,
        # and should never be removed or the assembler will fail
        never_remove = {"c64.FREADUY", "c64.FTOMEMXY", "c64.FADD", "c64.FSUB",
                        "c64flt.GIVUAYF", "c64flt.copy_mflt", "c64flt.float_add_one", "c64flt.float_sub_one",
                        "c64flt.float_add_SW1_to_XY", "c64flt.float_sub_SW1_from_XY"}
        discarded = []
        for sub in list(block.symbols.iter_subroutines()):
            usages = self.parsed.subroutine_usage[(sub.blockname, sub.name)]
            if not usages and sub.blockname + '.' + sub.name not in never_remove:
                block.symbols.discard_sub(sub.name)
                discarded.append(sub.name)
        if discarded:
            print("{}: discarded {:d} unused subroutines from block '{:s}'".format(block.sourceref, len(discarded), block.name))


def _value_sortkey(value: Value) -> int:
    if isinstance(value, RegisterValue):
        num = 0
        for char in value.register:
            num *= 100
            num += ord(char)
        return num
    elif isinstance(value, MemMappedValue):
        if value.address is None:
            return 99999999
        if value.address < 0x100:
            return 10000 + value.address
        else:
            return 20000 + value.address
    else:
        return 99999999
