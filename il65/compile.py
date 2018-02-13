"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the compiler of the IL65 code, that prepares the parse tree for code generation.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import re
import os
import sys
import linecache
from typing import no_type_check, Set, List, Dict, Tuple, Optional, Any
import attr
from .datatypes import DataType, VarType
from .plylex import SourceRef, print_bold
from .constantfold import ConstantFold
from .plyparse import *


class CompileError(Exception):
    pass


class PlyParser:
    def __init__(self, *, enable_floats: bool=False, imported_module: bool=False) -> None:
        self.parse_errors = 0
        self.imported_module = imported_module
        self.floats_enabled = enable_floats

    def parse_file(self, filename: str) -> Module:
        print("parsing:", filename)
        module = None
        try:
            module = parse_file(filename, self.lexer_error)
            self.check_directives_and_const_defs(module)
            self.apply_directive_options(module)
            module.scope.define_builtin_functions()
            self.process_imports(module)
            self.check_and_merge_zeropages(module)
            self.create_multiassigns(module)
            if not self.imported_module:
                # the following shall only be done on the main module after all imports have been done:
                self.check_all_symbolnames(module)
                self.determine_subroutine_usage(module)
                self.all_parents_connected(module)
                cf = ConstantFold(module)
                cf.fold_constants()
                self.semantic_check(module)
                self.coerce_values(module)
                self.check_floats_enabled(module)
                self.allocate_zeropage_vars(module)
        except ParseError as x:
            self.handle_parse_error(x)
        if self.parse_errors:
            print_bold("\nNo output; there were {:d} errors.\n".format(self.parse_errors))
            raise SystemExit(1)
        return module

    def lexer_error(self, sourceref: SourceRef, fmtstring: str, *args: str) -> None:
        self.parse_errors += 1
        self.print_error_sourceline(sourceref)
        print_bold("ERROR: {}: {}".format(sourceref, fmtstring.format(*args)))

    def _check_last_statement_is_return(self, last_stmt: AstNode) -> None:
        if isinstance(last_stmt, (Subroutine, Return, Goto)):
            return
        if isinstance(last_stmt, Directive) and last_stmt.name == "noreturn":
            return
        if isinstance(last_stmt, InlineAssembly):
            for line in reversed(last_stmt.assembly.splitlines()):
                line = line.strip()
                if line.startswith(";"):
                    continue
                if "jmp " in line or "jmp\t" in line or "rts" in line or "rti" in line:
                    return
        raise ParseError("last statement in a block/subroutine must be a return or goto, "
                         "(or %noreturn directive to silence this error)", last_stmt.sourceref)

    def check_floats_enabled(self, module: Module) -> None:
        if self.floats_enabled:
            return
        for node in module.all_nodes():
            if isinstance(node, LiteralValue):
                if type(node.value) is float:
                    raise ParseError("floating point numbers not enabled via option", node.sourceref)
            elif isinstance(node, VarDef):
                if node.datatype == DataType.FLOAT:
                    raise ParseError("floating point numbers not enabled via option", node.sourceref)

    def coerce_values(self, module: Module) -> None:
        for node in module.all_nodes():
            try:
                # note: not processing regular assignments, because they can contain multiple targets of different datatype.
                # this has to be dealt with anyway later, so we don't bother dealing with it here for just a special case.
                if isinstance(node, AugAssignment):
                    if node.right.is_compile_constant():
                        _, node.right = coerce_constant_value(datatype_of(node.left, node.my_scope()), node.right, node.right.sourceref)
                elif isinstance(node, Goto):
                    if node.condition is not None and node.condition.is_compile_constant():
                        _, node.nodes[1] = coerce_constant_value(DataType.WORD, node.nodes[1], node.nodes[1].sourceref)   # type: ignore
                elif isinstance(node, Return):
                    if node.value_A is not None and node.value_A.is_compile_constant():
                        _, node.nodes[0] = coerce_constant_value(DataType.BYTE, node.nodes[0], node.nodes[0].sourceref)   # type: ignore
                    if node.value_X is not None and node.value_X.is_compile_constant():
                        _, node.nodes[1] = coerce_constant_value(DataType.BYTE, node.nodes[1], node.nodes[1].sourceref)   # type: ignore
                    if node.value_Y is not None and node.value_Y.is_compile_constant():
                        _, node.nodes[2] = coerce_constant_value(DataType.BYTE, node.nodes[2], node.nodes[2].sourceref)   # type: ignore
                elif isinstance(node, VarDef):
                    if node.value is not None:
                        if node.value.is_compile_constant():
                            _, node.value = coerce_constant_value(datatype_of(node, node.my_scope()), node.value, node.value.sourceref)
            except OverflowError as x:
                raise ParseError(str(x), node.sourceref)

    def all_parents_connected(self, module: Module) -> None:
        # check that all parents are connected in all nodes
        def check(node: AstNode, expected_parent: AstNode) -> None:
            if node.parent is not expected_parent:
                print("\nINTERNAL ERROR: parent node invalid of node", node, node.sourceref)
                print("  current parent:", node.parent)
                print("  expected parent:", expected_parent, expected_parent.sourceref)
                raise CompileError("parent node invalid, see message above")
            for child_node in node.nodes:
                if isinstance(child_node, AstNode):
                    check(child_node, node)
                else:
                    raise TypeError("invalid child node type", child_node, " in ", node, " sref", node.sourceref)
        check(module, None)

    def semantic_check(self, module: Module) -> None:
        # perform semantic analysis / checks on the syntactic parse tree we have so far
        # (note: symbol names have already been checked to exist when we start this)
        previous_stmt = None
        encountered_block_names = set()  # type: Set[str]
        encountered_blocks = set()  # type: Set[Block]
        for node in module.all_nodes():
            if isinstance(node, Block):
                if node in encountered_blocks:
                    raise CompileError("parse tree malformed; block duplicated", node, node.name, node.sourceref)
                parentname = (node.parent.name + ".") if node.parent else ""
                blockname = parentname + node.name
                if blockname in encountered_block_names:
                    raise CompileError("block names not unique:", blockname)
                encountered_block_names.add(blockname)
                encountered_blocks.add(node)
            if isinstance(node, Scope):
                if node.nodes and isinstance(node.parent, (Block, Subroutine)):
                    if isinstance(node.parent, Block) and node.parent.name != "ZP":
                        self._check_last_statement_is_return(node.nodes[-1])
            elif isinstance(node, SubCall):
                if isinstance(node.target, SymbolName):
                    subdef = node.my_scope().lookup(node.target.name)
                    if isinstance(subdef, Subroutine):
                        self.check_subroutine_arguments(node, subdef)
            elif isinstance(node, Subroutine):
                # the previous statement (if any) must be a Goto or Return
                if not isinstance(previous_stmt, (Scope, Goto, Return, VarDef, Subroutine)):
                    raise ParseError("statement preceding subroutine must be a goto or return or another subroutine", node.sourceref)
            elif isinstance(node, IncrDecr):
                if isinstance(node.target, SymbolName):
                    symdef = node.my_scope().lookup(node.target.name)
                    if isinstance(symdef, VarDef) and symdef.vartype == VarType.CONST:
                        raise ParseError("cannot modify a constant", node.sourceref)
            elif isinstance(node, Assignment):
                scope = node.my_scope()
                for target in node.left.nodes:
                    if isinstance(target, SymbolName):
                        symdef = scope.lookup(target.name)
                        if isinstance(symdef, VarDef) and symdef.vartype == VarType.CONST:
                            raise ParseError("cannot modify a constant", target.sourceref)
            elif isinstance(node, AugAssignment):
                # the assignment target must not be a constant
                if isinstance(node.left, SymbolName):
                    symdef = node.my_scope().lookup(node.left.name)
                    if isinstance(symdef, VarDef):
                        if symdef.vartype == VarType.CONST:
                            raise ParseError("cannot modify a constant", node.sourceref)
                        elif symdef.datatype not in {DataType.BYTE, DataType.WORD, DataType.FLOAT}:
                            raise ParseError("cannot modify that datatype ({:s}) in this way"
                                             .format(symdef.datatype.name.lower()), node.sourceref)
                # check for divide by (constant) zero
                if node.operator in ("/=", "//="):
                    if isinstance(node.right, LiteralValue) and node.right.value == 0:
                        raise ParseError("division by zero", node.right.sourceref)
            elif isinstance(node, VarDef):
                if node.value is not None and not node.value.is_compile_constant():
                    raise ParseError("variable initialization value should be a compile-time constant", node.value.sourceref)
            elif isinstance(node, Dereference):
                if isinstance(node.operand, Register) and node.operand.datatype == DataType.BYTE:
                    raise ParseError("can't dereference a single register; use a register pair", node.operand.sourceref)
            previous_stmt = node

    def check_subroutine_arguments(self, call: SubCall, subdef: Subroutine) -> None:
        if len(call.arguments.nodes) != len(subdef.param_spec):
            raise ParseError("invalid number of arguments ({:d}, required: {:d})"
                             .format(len(call.arguments.nodes), len(subdef.param_spec)), call.sourceref)
        for arg, param in zip(call.arguments.nodes, subdef.param_spec):
            if arg.name:
                if not param[0]:
                    raise ParseError("parameter is unnamed but name was used", arg.sourceref)
                if arg.name != param[0]:
                    raise ParseError("parameter name mismatch", arg.sourceref)

    @no_type_check
    def check_and_merge_zeropages(self, module: Module) -> None:
        # merge all ZP blocks into one
        for zeropage in module.all_nodes(Block):
            if zeropage.name == "ZP":
                break
        else:
            return
        for block in list(module.all_nodes(Block)):
            if block is not zeropage and block.name == "ZP":
                # merge other ZP block into first ZP block
                for node in block.scope.nodes:
                    if isinstance(node, Directive):
                        zeropage.scope.add_node(node, 0)
                    elif isinstance(node, VarDef):
                        zeropage.scope.add_node(node)
                    else:
                        raise ParseError("only variables and directives allowed in zeropage block", node.sourceref)
                block.parent.remove_node(block)
        block.parent._populate_symboltable(zeropage)  # re-add the 'ZP' symbol

    def allocate_zeropage_vars(self, module: Module) -> None:
        # allocate zeropage variables to the available free zp addresses
        if not module.scope.nodes:
            return
        zpnode = module.zeropage()
        if zpnode is None:
            return
        zeropage = Zeropage(module.zp_options, self.floats_enabled)
        for vardef in zpnode.all_nodes(VarDef):
            if vardef.datatype.isstring():
                raise ParseError("cannot put strings in the zeropage", vardef.sourceref)
            try:
                if vardef.vartype == VarType.VAR:
                    vardef.zp_address = zeropage.allocate(vardef)
            except CompileError as x:
                raise ParseError(str(x), vardef.sourceref)

    def check_all_symbolnames(self, module: Module) -> None:
        for node in module.all_nodes(SymbolName):
            check_symbol_definition(node.name, node.my_scope(), node.sourceref)     # type: ignore

    @no_type_check
    def create_multiassigns(self, module: Module) -> None:
        # create multi-assign statements from nested assignments (A=B=C=5),
        def reduce_right(assign: Assignment) -> Assignment:
            if isinstance(assign.right, Assignment):
                right = reduce_right(assign.right)
                for rn in right.left.nodes:
                    rn.parent = assign.left
                assign.left.nodes.extend(right.left.nodes)
                assign.right = right.right
                assign.right.parent = assign
            return assign

        for node in module.all_nodes(Assignment):
            if isinstance(node.right, Assignment):
                multi = reduce_right(node)
                assert multi is node and len(multi.left.nodes) > 1 and not isinstance(multi.right, Assignment)

    @no_type_check
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

        for directive in module.all_nodes(Directive):
            node = directive.my_scope().parent
            if isinstance(node, Module):
                # process the module's directives
                if directive.name == "output":
                    if len(directive.args) != 1 or not isinstance(directive.args[0], str):
                        raise ParseError("expected one str directive argument", directive.sourceref)
                    if directive.args[0] == "raw":
                        node.format = ProgramFormat.RAW
                        node.address = 0xc000
                    elif directive.args[0] == "prg":
                        node.format = ProgramFormat.PRG
                        node.address = 0xc000
                    elif directive.args[0] == "basic":
                        node.format = ProgramFormat.BASIC
                        node.address = 0x0801
                    elif directive.args[0] == "enable_floats":
                        self.floats_enabled = module.floats_enabled = True
                    else:
                        raise ParseError("invalid directive args", directive.sourceref)
                elif directive.name == "address":
                    if len(directive.args) != 1 or type(directive.args[0]) is not int:
                        raise ParseError("expected one integer directive argument", directive.sourceref)
                    if node.format == ProgramFormat.BASIC:
                        raise ParseError("basic cannot have a custom load address", directive.sourceref)
                    node.address = directive.args[0]
                    attr.validate(node)
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
                    set_save_registers(directive.my_scope(), directive)
                else:
                    raise NotImplementedError(directive.name)
            elif isinstance(node, Block):
                # process the block's directives
                if directive.name == "saveregisters":
                    set_save_registers(directive.my_scope(), directive)
                elif directive.name in ("breakpoint", "asmbinary", "asminclude", "noreturn"):
                    continue
                else:
                    raise NotImplementedError(directive.name)
            elif isinstance(node, Subroutine):
                # process the sub's directives
                if directive.name == "saveregisters":
                    set_save_registers(directive.my_scope(), directive)
                elif directive.name in ("breakpoint", "asmbinary", "asminclude", "noreturn"):
                    continue
                else:
                    raise NotImplementedError(directive.name)

    @no_type_check
    def determine_subroutine_usage(self, module: Module) -> None:
        module.subroutine_usage.clear()
        for node in module.all_nodes():
            if isinstance(node, InlineAssembly):
                self._get_subroutine_usages_from_asm(module.subroutine_usage, node, node.my_scope())
            elif isinstance(node, SubCall):
                self._get_subroutine_usages_from_subcall(module.subroutine_usage, node, node.my_scope())
            elif isinstance(node, Goto):
                self._get_subroutine_usages_from_goto(module.subroutine_usage, node, node.my_scope())
            elif isinstance(node, Return):
                self._get_subroutine_usages_from_return(module.subroutine_usage, node, node.my_scope())
            elif isinstance(node, Assignment):
                self._get_subroutine_usages_from_assignment(module.subroutine_usage, node, node.my_scope())
        # print("----------SUBROUTINES IN USE-------------")
        # import pprint
        # pprint.pprint(module.subroutine_usage)
        # print("----------/SUBROUTINES IN USE-------------")

    def _get_subroutine_usages_from_subcall(self, usages: Dict[Tuple[str, str], Set[str]],
                                            subcall: SubCall, parent_scope: Scope) -> None:
        if isinstance(subcall.target, SymbolName):
            usages[(parent_scope.name, subcall.target.name)].add(str(subcall.sourceref))
        for arg in subcall.arguments.nodes:
            self._get_subroutine_usages_from_expression(usages, arg.value, parent_scope)

    def _get_subroutine_usages_from_expression(self, usages: Dict[Tuple[str, str], Set[str]],
                                               expr: Any, parent_scope: Scope) -> None:
        if expr is None or isinstance(expr, (int, str, float, bool, Register)):
            return
        elif isinstance(expr, SubCall):
            self._get_subroutine_usages_from_subcall(usages, expr, parent_scope)
        elif isinstance(expr, ExpressionWithOperator):
            self._get_subroutine_usages_from_expression(usages, expr.left, parent_scope)
            self._get_subroutine_usages_from_expression(usages, expr.right, parent_scope)
        elif isinstance(expr, (LiteralValue, AddressOf)):
            return
        elif isinstance(expr, Dereference):
            return self._get_subroutine_usages_from_expression(usages, expr.operand, parent_scope)
        elif isinstance(expr, SymbolName):
            try:
                symbol = parent_scope.lookup(expr.name)
                if isinstance(symbol, Subroutine):
                    usages[(parent_scope.name, expr.name)].add(str(expr.sourceref))
            except UndefinedSymbolError:
                pass
        else:
            raise TypeError("unknown expr type to scan for sub usages", expr, expr.sourceref)

    @no_type_check
    def _get_subroutine_usages_from_goto(self, usages: Dict[Tuple[str, str], Set[str]],
                                         goto: Goto, parent_scope: Scope) -> None:
        if isinstance(goto.target, SymbolName):
            usages[(parent_scope.name, goto.target.name)].add(str(goto.sourceref))
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
                    if name[0] == '$':
                        continue
                    try:
                        symbol = parent_scope.lookup(name)
                    except UndefinedSymbolError:
                        pass
                    else:
                        if isinstance(symbol, Subroutine):
                            if symbol.scope:
                                namespace = symbol.scope.parent_scope.name
                            else:
                                namespace, name = name.rsplit(".", maxsplit=2)
                            usages[(namespace, symbol.name)].add(str(asmnode.sourceref))

    def check_directives_and_const_defs(self, module: Module) -> None:
        imports = set()  # type: Set[str]
        for node in module.all_nodes():
            if isinstance(node, VarDef):
                if node.value is None and node.vartype == VarType.CONST:
                    raise ParseError("const should be initialized with a compile-time constant value", node.sourceref)
            elif isinstance(node, Directive):
                assert isinstance(node.parent, Scope)
                if node.parent.level == "module":
                    if node.name not in {"output", "zp", "address", "import", "saveregisters", "noreturn"}:
                        raise ParseError("invalid directive in module", node.sourceref)
                    if node.name == "import":
                        if imports & set(node.args):
                            raise ParseError("duplicate import", node.sourceref)
                        imports |= set(node.args)
                else:
                    if node.name not in {"asmbinary", "asminclude", "breakpoint", "saveregisters", "noreturn"}:
                        raise ParseError("invalid directive in " + node.parent.__class__.__name__.lower(), node.sourceref)
                    if node.name == "saveregisters":
                        # it should be the first node in the scope
                        if node.parent.nodes[0] is not node:
                            raise ParseError("saveregisters directive must be first in this scope", node.sourceref)

    def process_imports(self, module: Module) -> None:
        # (recursively) imports the modules
        imported = []
        for directive in module.all_nodes(Directive):
            if directive.name == "import":  # type: ignore
                if len(directive.args) < 1:     # type: ignore
                    raise ParseError("missing argument(s) for import directive", directive.sourceref)
                for arg in directive.args:      # type: ignore
                    filename = self.find_import_file(arg, directive.sourceref.file)
                    if not filename:
                        raise ParseError("imported file not found", directive.sourceref)
                    imported_module, import_parse_errors = self.import_file(filename)
                    imported.append(imported_module)
                    self.parse_errors += import_parse_errors
        if not self.imported_module:
            # compiler support library is always imported (in main parser)
            filename = self.find_import_file("il65lib", module.sourceref.file)
            if filename:
                imported_module, import_parse_errors = self.import_file(filename)
                imported.append(imported_module)
                self.parse_errors += import_parse_errors
            else:
                raise FileNotFoundError("missing il65lib")
        if sum(m.floats_enabled for m in imported):
            self.floats_enabled = module.floats_enabled = True
        # append the imported module's contents (blocks) at the end of the current module
        for block in (node for imported_module in imported
                      for node in imported_module.scope.nodes
                      if isinstance(node, Block)):
            module.scope.add_node(block)

    def import_file(self, filename: str) -> Tuple[Module, int]:
        sub_parser = PlyParser(imported_module=True)
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
        out = sys.stdout
        if out.isatty():
            print("\x1b[1m", file=out)
        if self.imported_module:
            print("Error (in imported file):", str(exc), file=out)
        else:
            print("Error:", str(exc), file=out)
        self.print_error_sourceline(exc.sourceref)
        if out.isatty():
            print("\x1b[0m", file=out, end="", flush=True)
        raise exc   # XXX temporary to see where the error occurred

    def print_error_sourceline(self, sref: SourceRef) -> None:
        if not sref:
            return
        sourcetext = linecache.getline(sref.file, sref.line).rstrip()
        if sourcetext:
            print("  " + sourcetext.expandtabs(8))
            if sref.column:
                print(' ' * (1+sref.column) + '^')


class Zeropage:
    SCRATCH_B1 = 0x02
    SCRATCH_B2 = 0x03
    SCRATCH_W1 = 0xfb     # $fb/$fc
    SCRATCH_W2 = 0xfd     # $fd/$fe

    def __init__(self, options: ZpOptions, enable_floats: bool) -> None:
        self.floats_enabled = enable_floats
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
            if not self.floats_enabled:
                raise TypeError("floating point numbers not enabled via option")
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
        elif vardef.datatype.isstring():
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
        raise CompileError("ERROR: no free space in ZP to allocate {:d} sequential bytes".format(size))

    def available(self) -> int:
        return len(self.free)
