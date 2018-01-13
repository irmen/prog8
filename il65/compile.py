"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the compiler of the IL65 code, that prepares the parse tree for code generation.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import re
import os
import sys
import linecache
from typing import Optional, Tuple, Set, Dict, List, Any, no_type_check
import attr
from .plyparse import parse_file, ParseError, Module, Directive, Block, Subroutine, Scope, VarDef, LiteralValue, \
    SubCall, Goto, Return, Assignment, InlineAssembly, Register, Expression, ProgramFormat, ZpOptions,\
    SymbolName, Dereference, AddressOf
from .plylex import SourceRef, print_bold
from .optimize import optimize
from .datatypes import DataType, VarType, STRING_DATATYPES


class CompileError(Exception):
    pass


class PlyParser:
    def __init__(self, parsing_import: bool=False) -> None:
        self.parse_errors = 0
        self.parsing_import = parsing_import

    def parse_file(self, filename: str) -> Module:
        print("parsing:", filename)
        module = None
        try:
            module = parse_file(filename, self.lexer_error)
            self.check_directives(module)
            self.process_imports(module)
            self.create_multiassigns(module)
            self.check_and_merge_zeropages(module)
            self.process_all_expressions(module)
            if not self.parsing_import:
                # these shall only be done on the main module after all imports have been done:
                self.apply_directive_options(module)
                self.determine_subroutine_usage(module)
                # XXX merge zero page from imported modules??? do we still have to do that?
                self.allocate_zeropage_vars(module)
        except ParseError as x:
            self.handle_parse_error(x)
        if self.parse_errors:
            print_bold("\nNo output; there were {:d} errors.\n".format(self.parse_errors))
            raise SystemExit(1)
        return module

    def lexer_error(self, sourceref: SourceRef, fmtstring: str, *args: str) -> None:
        self.parse_errors += 1
        print_bold("ERROR: {}: {}".format(sourceref, fmtstring.format(*args)))

    def check_and_merge_zeropages(self, module: Module) -> None:
        # merge all ZP blocks into one
        zeropage = None
        for block in list(module.scope.filter_nodes(Block)):
            if block.name == "ZP":
                if zeropage:
                    # merge other ZP block into first ZP block
                    for node in block.nodes:
                        if isinstance(node, Directive):
                            zeropage.scope.add_node(node, 0)
                        elif isinstance(node, VarDef):
                            zeropage.scope.add_node(node)
                        else:
                            raise ParseError("only variables and directives allowed in zeropage block", node.sourceref)
                else:
                    zeropage = block
                module.scope.remove_node(block)
        if zeropage:
            # add the zero page again, as the very first block
            module.scope.add_node(zeropage, 0)

    def allocate_zeropage_vars(self, module: Module) -> None:
        # allocate zeropage variables to the available free zp addresses
        if not module.scope.nodes:
            return
        zpnode = module.scope.nodes[0]
        if zpnode.name != "ZP":
            return
        zeropage = Zeropage(module.zp_options)
        for vardef in zpnode.scope.filter_nodes(VarDef):
            try:
                vardef.zp_address = zeropage.allocate(vardef)
            except CompileError as x:
                raise ParseError(str(x), vardef.sourceref)

    @no_type_check
    def process_all_expressions(self, module: Module) -> None:
        # process/simplify all expressions (constant folding etc)
        encountered_blocks = set()
        for block, parent in module.all_scopes():
            parentname = (parent.name + ".") if parent else ""
            blockname = parentname + block.name
            if blockname in encountered_blocks:
                raise ValueError("block names not unique:", blockname)
            encountered_blocks.add(blockname)
            for node in block.nodes:
                try:
                    node.process_expressions(block.scope)
                except ParseError:
                    raise
                except Exception as x:
                    self.handle_internal_error(x, "process_expressions of node {} in block {}".format(node, block.name))

    def create_multiassigns(self, module: Module) -> None:
        # create multi-assign statements from nested assignments (A=B=C=5),
        # and optimize TargetRegisters down to single Register if it's just one register.
        def reduce_right(assign: Assignment) -> Assignment:
            if isinstance(assign.right, Assignment):
                right = reduce_right(assign.right)
                assign.left.extend(right.left)
                assign.right = right.right
            return assign

        for block, parent in module.all_scopes():
            for node in block.nodes:        # type: ignore
                if isinstance(node, Assignment):
                    if isinstance(node.right, Assignment):
                        multi = reduce_right(node)
                        assert multi is node and len(multi.left) > 1 and not isinstance(multi.right, Assignment)
                    node.simplify_targetregisters()

    def apply_directive_options(self, module: Module) -> None:
        def set_save_registers(scope: Scope, save_dir: Directive) -> None:
            if not scope:
                return
            if len(save_dir.args) > 1:
                raise ParseError("expected zero or one directive argument", save_dir.sourceref)
            if save_dir.args:
                if save_dir.args[0] in ("yes", "true", True):
                    scope.save_registers = True
                elif save_dir.args[0] in ("no", "false", False):
                    scope.save_registers = False
                else:
                    raise ParseError("invalid directive args", save_dir.sourceref)
            else:
                scope.save_registers = True

        for block, parent in module.all_scopes():
            if isinstance(block, Module):
                # process the module's directives
                for directive in block.scope.filter_nodes(Directive):
                    if directive.name == "output":
                        if len(directive.args) != 1 or not isinstance(directive.args[0], str):
                            raise ParseError("expected one str directive argument", directive.sourceref)
                        if directive.args[0] == "raw":
                            block.format = ProgramFormat.RAW
                            block.address = 0xc000
                        elif directive.args[0] == "prg":
                            block.format = ProgramFormat.PRG
                            block.address = 0xc000
                        elif directive.args[0] == "basic":
                            block.format = ProgramFormat.BASIC
                            block.address = 0x0801
                        else:
                            raise ParseError("invalid directive args", directive.sourceref)
                    elif directive.name == "address":
                        if len(directive.args) != 1 or not isinstance(directive.args[0], int):
                            raise ParseError("expected one integer directive argument", directive.sourceref)
                        if block.format == ProgramFormat.BASIC:
                            raise ParseError("basic cannot have a custom load address", directive.sourceref)
                        block.address = directive.args[0]
                        attr.validate(block)
                    elif directive.name in "import":
                        pass   # is processed earlier
                    elif directive.name == "zp":
                        if len(directive.args) not in (1, 2) or set(directive.args) - {"clobber", "restore"}:
                            raise ParseError("invalid directive args", directive.sourceref)
                        if "clobber" in directive.args and "restore" in directive.args:
                            module.zp_options = ZpOptions.CLOBBER_RESTORE
                        elif "clobber" in directive.args:
                            module.zp_options = ZpOptions.CLOBBER
                        elif "restore" in directive.args:
                            raise ParseError("invalid directive args", directive.sourceref)
                    elif directive.name == "saveregisters":
                        set_save_registers(block.scope, directive)
                    else:
                        raise NotImplementedError(directive.name)
            elif isinstance(block, Block):
                # process the block's directives
                for directive in block.scope.filter_nodes(Directive):
                    if directive.name == "saveregisters":
                        set_save_registers(block.scope, directive)
                    elif directive.name in ("breakpoint", "asmbinary", "asminclude"):
                        continue
                    else:
                        raise NotImplementedError(directive.name)
            elif isinstance(block, Subroutine):
                if block.scope:
                    # process the sub's directives
                    for directive in block.scope.filter_nodes(Directive):
                        if directive.name == "saveregisters":
                            set_save_registers(block.scope, directive)
                        elif directive.name in ("breakpoint", "asmbinary", "asminclude"):
                            continue
                        else:
                            raise NotImplementedError(directive.name)

    @no_type_check
    def determine_subroutine_usage(self, module: Module) -> None:
        module.subroutine_usage.clear()
        for block, parent in module.all_scopes():
            for node in block.nodes:
                if isinstance(node, InlineAssembly):
                    self._get_subroutine_usages_from_asm(module.subroutine_usage, node, block.scope)
                elif isinstance(node, SubCall):
                    self._get_subroutine_usages_from_subcall(module.subroutine_usage, node, block.scope)
                elif isinstance(node, Goto):
                    self._get_subroutine_usages_from_goto(module.subroutine_usage, node, block.scope)
                elif isinstance(node, Return):
                    self._get_subroutine_usages_from_return(module.subroutine_usage, node, block.scope)
                elif isinstance(node, Assignment):
                    self._get_subroutine_usages_from_assignment(module.subroutine_usage, node, block.scope)

    def _get_subroutine_usages_from_subcall(self, usages: Dict[Tuple[str, str], Set[str]],
                                            subcall: SubCall, parent_scope: Scope) -> None:
        # node.target (relevant if its a symbolname -- a str), node.arguments (list of CallArgument)
        #   CallArgument.value = expression.
        if isinstance(subcall.target.target, str):
            try:
                scopename, name = subcall.target.target.split('.')
            except ValueError:
                scopename = parent_scope.name
                name = subcall.target.target
            usages[(scopename, name)].add(str(subcall.sourceref))
        for arg in subcall.arguments:
            self._get_subroutine_usages_from_expression(usages, arg.value, parent_scope)

    def _get_subroutine_usages_from_expression(self, usages: Dict[Tuple[str, str], Set[str]],
                                               expr: Any, parent_scope: Scope) -> None:
        if expr is None or isinstance(expr, (int, str, float, bool, Register)):
            return
        elif isinstance(expr, SubCall):
            self._get_subroutine_usages_from_subcall(usages, expr, parent_scope)
        elif isinstance(expr, Expression):
            self._get_subroutine_usages_from_expression(usages, expr.left, parent_scope)
            self._get_subroutine_usages_from_expression(usages, expr.right, parent_scope)
        elif isinstance(expr, LiteralValue):
            return
        elif isinstance(expr, Dereference):
            return self._get_subroutine_usages_from_expression(usages, expr.location, parent_scope)
        elif isinstance(expr, AddressOf):
            return self._get_subroutine_usages_from_expression(usages, expr.name, parent_scope)
        elif isinstance(expr, SymbolName):
            try:
                symbol = parent_scope[expr.name]
                if isinstance(symbol, Subroutine):
                    usages[(parent_scope.name, expr.name)].add(str(expr.sourceref))
            except LookupError:
                pass
        else:
            raise TypeError("unknown expr type to scan for sub usages", expr, expr.sourceref)

    def _get_subroutine_usages_from_goto(self, usages: Dict[Tuple[str, str], Set[str]],
                                         goto: Goto, parent_scope: Scope) -> None:
        # node.target (relevant if its a symbolname -- a str), node.condition (expression)
        if isinstance(goto.target.target, str):
            try:
                symbol = parent_scope[goto.target.target]
            except LookupError:
                return
            if isinstance(symbol, Subroutine):
                usages[(parent_scope.name, symbol.name)].add(str(goto.sourceref))
        self._get_subroutine_usages_from_expression(usages, goto.condition, parent_scope)

    def _get_subroutine_usages_from_return(self, usages: Dict[Tuple[str, str], Set[str]],
                                           returnnode: Return, parent_scope: Scope) -> None:
        # node.value_A (expression), value_X (expression), value_Y (expression)
        self._get_subroutine_usages_from_expression(usages, returnnode.value_A, parent_scope)
        self._get_subroutine_usages_from_expression(usages, returnnode.value_X, parent_scope)
        self._get_subroutine_usages_from_expression(usages, returnnode.value_Y, parent_scope)

    def _get_subroutine_usages_from_assignment(self, usages: Dict[Tuple[str, str], Set[str]],
                                               assignment: Assignment, parent_scope: Scope) -> None:
        # node.right (expression, or another Assignment)
        if isinstance(assignment.right, Assignment):
            self._get_subroutine_usages_from_assignment(usages, assignment.right, parent_scope)
        else:
            self._get_subroutine_usages_from_expression(usages, assignment.right, parent_scope)

    def _get_subroutine_usages_from_asm(self, usages: Dict[Tuple[str, str], Set[str]],
                                        asmnode: InlineAssembly, parent_scope: Scope) -> None:
        # asm can refer to other symbols as well, track subroutine usage
        for line in asmnode.assembly.splitlines():
            splits = line.split(maxsplit=1)
            if len(splits) == 2:
                for match in re.finditer(r"(?P<symbol>[a-zA-Z_$][a-zA-Z0-9_\.]+)", splits[1]):
                    name = match.group("symbol")
                    if name[0] == '$' or "." not in name:
                        continue
                    try:
                        symbol = parent_scope[name]
                    except LookupError:
                        pass
                    else:
                        if isinstance(symbol, Subroutine):
                            namespace, name = name.rsplit(".", maxsplit=2)
                            usages[(namespace, name)].add(str(asmnode.sourceref))

    def check_directives(self, module: Module) -> None:
        for node, parent in module.all_scopes():
            if isinstance(node, Module):
                # check module-level directives
                imports = set()  # type: Set[str]
                for directive in node.scope.filter_nodes(Directive):
                    if directive.name not in {"output", "zp", "address", "import", "saveregisters"}:
                        raise ParseError("invalid directive in module", directive.sourceref)
                    if directive.name == "import":
                        if imports & set(directive.args):
                            raise ParseError("duplicate import", directive.sourceref)
                        imports |= set(directive.args)
            if isinstance(node, (Block, Subroutine)):
                # check block and subroutine-level directives
                first_node = True
                if not node.scope:
                    continue
                for sub_node in node.scope.nodes:
                    if isinstance(sub_node, Directive):
                        if sub_node.name not in {"asmbinary", "asminclude", "breakpoint", "saveregisters"}:
                            raise ParseError("invalid directive in " + node.__class__.__name__.lower(), sub_node.sourceref)
                        if sub_node.name == "saveregisters" and not first_node:
                            raise ParseError("saveregisters directive must be the first", sub_node.sourceref)
                    first_node = False

    def process_imports(self, module: Module) -> None:
        # (recursively) imports the modules
        imported = []
        for directive in module.scope.filter_nodes(Directive):
            if directive.name == "import":
                if len(directive.args) < 1:
                    raise ParseError("missing argument(s) for import directive", directive.sourceref)
                for arg in directive.args:
                    filename = self.find_import_file(arg, directive.sourceref.file)
                    if not filename:
                        raise ParseError("imported file not found", directive.sourceref)
                    imported_module, import_parse_errors = self.import_file(filename)
                    imported_module.scope.parent_scope = module.scope
                    imported.append(imported_module)
                    self.parse_errors += import_parse_errors
        if not self.parsing_import:
            # compiler support library is always imported (in main parser)
            filename = self.find_import_file("il65lib", module.sourceref.file)
            if filename:
                imported_module, import_parse_errors = self.import_file(filename)
                imported_module.scope.parent_scope = module.scope
                imported.append(imported_module)
                self.parse_errors += import_parse_errors
            else:
                raise FileNotFoundError("missing il65lib")
        # append the imported module's contents (blocks) at the end of the current module
        for imported_module in imported:
            for block in imported_module.scope.filter_nodes(Block):
                module.scope.add_node(block)

    def import_file(self, filename: str) -> Tuple[Module, int]:
        sub_parser = PlyParser(parsing_import=True)
        return sub_parser.parse_file(filename), sub_parser.parse_errors

    def find_import_file(self, modulename: str, sourcefile: str) -> Optional[str]:
        candidates = [modulename+".ill", modulename]
        filename_at_source_location = os.path.join(os.path.split(sourcefile)[0], modulename)
        if filename_at_source_location not in candidates:
            candidates.append(filename_at_source_location+".ill")
            candidates.append(filename_at_source_location)
        filename_at_libs_location = os.path.join(os.path.split(__file__)[0], "lib", modulename)
        if filename_at_libs_location not in candidates:
            candidates.append(filename_at_libs_location+".ill")
            candidates.append(filename_at_libs_location)
        for filename in candidates:
            if os.path.isfile(filename):
                return filename
        return None

    def handle_parse_error(self, exc: ParseError) -> None:
        self.parse_errors += 1
        if sys.stderr.isatty():
            print("\x1b[1m", file=sys.stderr)
        if self.parsing_import:
            print("Error (in imported file):", str(exc), file=sys.stderr)
        else:
            print("Error:", str(exc), file=sys.stderr)
        sourcetext = linecache.getline(exc.sourceref.file, exc.sourceref.line).rstrip()
        if sourcetext:
            print("  " + sourcetext.expandtabs(1), file=sys.stderr)
            if exc.sourceref.column:
                print(' ' * (1+exc.sourceref.column) + '^', file=sys.stderr)
        if sys.stderr.isatty():
            print("\x1b[0m", file=sys.stderr, end="", flush=True)

    def handle_internal_error(self, exc: Exception, msg: str="") -> None:
        if sys.stderr.isatty():
            print("\x1b[1m", file=sys.stderr)
        print("\nERROR: internal parser error: ", exc, file=sys.stderr)
        if msg:
            print("    Message:", msg, end="\n\n")
        if sys.stderr.isatty():
            print("\x1b[0m", file=sys.stderr, end="", flush=True)
        raise exc


class Zeropage:
    SCRATCH_B1 = 0x02
    SCRATCH_B2 = 0x03
    SCRATCH_W1 = 0xfb     # $fb/$fc
    SCRATCH_W2 = 0xfd     # $fd/$fe

    def __init__(self, options: ZpOptions) -> None:
        self.free = []  # type: List[int]
        self.allocations = {}   # type: Dict[int, Tuple[str, DataType]]
        if options in (ZpOptions.CLOBBER_RESTORE, ZpOptions.CLOBBER):
            # clobber the zp, more free storage, yay!
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

    def allocate(self, vardef: VarDef) -> int:
        assert not vardef.name or vardef.name not in {a[0] for a in self.allocations.values()}, "var name is not unique"
        assert vardef.vartype == VarType.VAR, "can only allocate var"

        def sequential_free(location: int) -> bool:
            return all(location + i in self.free for i in range(size))

        def lone_byte(location: int) -> bool:
            return (location-1) not in self.free and (location+1) not in self.free and location in self.free

        def make_allocation(location: int) -> int:
            for loc in range(location, location + size):
                self.free.remove(loc)
            self.allocations[location] = (vardef.name or "<unnamed>", vardef.datatype)
            return location

        if vardef.datatype == DataType.BYTE:
            size = 1
        elif vardef.datatype == DataType.WORD:
            size = 2
        elif vardef.datatype == DataType.FLOAT:
            print_bold("warning: {}: allocating a large datatype in zeropage".format(vardef.sourceref))
            size = 5
        elif vardef.datatype == DataType.BYTEARRAY:
            print_bold("warning: {}: allocating a large datatype in zeropage".format(vardef.sourceref))
            size = vardef.size[0]
        elif vardef.datatype == DataType.WORDARRAY:
            print_bold("warning: {}: allocating a large datatype in zeropage".format(vardef.sourceref))
            size = vardef.size[0] * 2
        elif vardef.datatype == DataType.MATRIX:
            print_bold("warning: {}: allocating a large datatype in zeropage".format(vardef.sourceref))
            size = vardef.size[0] * vardef.size[1]
        elif vardef.datatype in STRING_DATATYPES:
            print_bold("warning: {}: allocating a large datatype in zeropage".format(vardef.sourceref))
            size = vardef.size[0]
        else:
            raise CompileError("cannot put datatype {:s} in ZP".format(vardef.datatype.name))
        if len(self.free) > 0:
            if size == 1:
                for candidate in range(min(self.free), max(self.free)+1):
                    if lone_byte(candidate):
                        return make_allocation(candidate)
                return make_allocation(self.free[0])
            for candidate in range(min(self.free), max(self.free)+1):
                if sequential_free(candidate):
                    return make_allocation(candidate)
        raise CompileError("ERROR: no more free space in ZP to allocate {:d} sequential bytes".format(size))

    def available(self) -> int:
        return len(self.free)


if __name__ == "__main__":
    description = "Compiler for IL65 language, code name 'Sick'"
    print("\n" + description + "\n")
    plyparser = PlyParser()
    m = plyparser.parse_file(sys.argv[1])
    optimize(m)
    print()
