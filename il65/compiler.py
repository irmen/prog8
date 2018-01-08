"""
Programming Language for 6502/6510 microprocessors
This is the compiler of the IL65 code, that prepares the parse tree for code generation.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

import re
import os
import sys
import linecache
from typing import Optional, Tuple, Set, Dict, Any, List
from .plyparser import parse_file, Module, Directive, Block, Subroutine, Scope, \
    SubCall, Goto, Return, Assignment, InlineAssembly, Register, Expression, TargetRegisters
from .plylexer import SourceRef, print_bold
from .optimizer import optimize


class ParseError(Exception):
    def __init__(self, message: str, sourcetext: Optional[str], sourceref: SourceRef) -> None:
        self.sourceref = sourceref
        self.msg = message
        self.sourcetext = sourcetext

    def __str__(self):
        return "{} {:s}".format(self.sourceref, self.msg)


class PlyParser:
    def __init__(self, parsing_import: bool=False) -> None:
        self.parse_errors = 0
        self.parsing_import = parsing_import

    def parse_file(self, filename: str) -> Module:
        print("parsing:", filename)
        module = parse_file(filename, self.lexer_error)
        try:
            self.check_directives(module)
            self.process_imports(module)
            self.create_multiassigns(module)
            if not self.parsing_import:
                self.determine_subroutine_usage(module)
        except ParseError as x:
            self.handle_parse_error(x)
        if self.parse_errors:
            print_bold("\nNo output; there were {:d} errors.\n".format(self.parse_errors))
            raise SystemExit(1)
        return module

    def lexer_error(self, sourceref: SourceRef, fmtstring: str, *args: str) -> None:
        self.parse_errors += 1
        print_bold("ERROR: {}: {}".format(sourceref, fmtstring.format(*args)))

    def create_multiassigns(self, module: Module) -> None:
        # create multi-assign statements from nested assignments (A=B=C=5),
        # and optimize TargetRegisters down to single Register if it's just one register.
        def simplify_targetregisters(targets: List[Any]) -> List[Any]:
            new_targets = []
            for t in targets:
                if isinstance(t, TargetRegisters) and len(t.registers) == 1:
                    t = t.registers[0]
                new_targets.append(t)
            return new_targets

        def reduce_right(assign: Assignment) -> Assignment:
            if isinstance(assign.right, Assignment):
                right = reduce_right(assign.right)
                targets = simplify_targetregisters(right.left)
                assign.left.extend(targets)
                assign.right = right.right
            return assign

        for mnode, parent in module.all_scopes():
            if mnode.scope:
                for node in mnode.scope.nodes:
                    if isinstance(node, Assignment):
                        node.left = simplify_targetregisters(node.left)
                        if isinstance(node.right, Assignment):
                            multi = reduce_right(node)
                            assert multi is node and len(multi.left) > 1 and not isinstance(multi.right, Assignment)

    def determine_subroutine_usage(self, module: Module) -> None:
        module.subroutine_usage.clear()
        for mnode, parent in module.all_scopes():
            if mnode.scope:
                for node in mnode.scope.nodes:
                    if isinstance(node, InlineAssembly):
                        self._parse_asm_for_subroutine_usage(module.subroutine_usage, node, mnode.scope)
                    elif isinstance(node, SubCall):
                        self._parse_subcall_for_subroutine_usages(module.subroutine_usage, node, mnode.scope)
                    elif isinstance(node, Goto):
                        self._parse_goto_for_subroutine_usages(module.subroutine_usage, node, mnode.scope)
                    elif isinstance(node, Return):
                        self._parse_return_for_subroutine_usages(module.subroutine_usage, node, mnode.scope)
                    elif isinstance(node, Assignment):
                        self._parse_assignment_for_subroutine_usages(module.subroutine_usage, node, mnode.scope)

    def _parse_subcall_for_subroutine_usages(self, usages: Dict[Tuple[str, str], Set[str]],
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
            self._parse_expression_for_subroutine_usages(usages, arg.value, parent_scope)

    def _parse_expression_for_subroutine_usages(self, usages: Dict[Tuple[str, str], Set[str]],
                                                expr: Any, parent_scope: Scope) -> None:
        if expr is None or isinstance(expr, (int, str, float, bool, Register)):
            return
        elif isinstance(expr, SubCall):
            self._parse_subcall_for_subroutine_usages(usages, expr, parent_scope)
        elif isinstance(expr, Expression):
            self._parse_expression_for_subroutine_usages(usages, expr.left, parent_scope)
            self._parse_expression_for_subroutine_usages(usages, expr.right, parent_scope)
        else:
            print("@todo parse expression for subroutine usage:", expr)    # @todo

    def _parse_goto_for_subroutine_usages(self, usages: Dict[Tuple[str, str], Set[str]],
                                          goto: Goto, parent_scope: Scope) -> None:
        # node.target (relevant if its a symbolname -- a str), node.condition (expression)
        if isinstance(goto.target.target, str):
            try:
                symbol = parent_scope[goto.target.target]
            except LookupError:
                return
            if isinstance(symbol, Subroutine):
                usages[(parent_scope.name, symbol.name)].add(str(goto.sourceref))
        self._parse_expression_for_subroutine_usages(usages, goto.condition, parent_scope)

    def _parse_return_for_subroutine_usages(self, usages: Dict[Tuple[str, str], Set[str]],
                                            returnnode: Return, parent_scope: Scope) -> None:
        # node.value_A (expression), value_X (expression), value_Y (expression)
        self._parse_expression_for_subroutine_usages(usages, returnnode.value_A, parent_scope)
        self._parse_expression_for_subroutine_usages(usages, returnnode.value_X, parent_scope)
        self._parse_expression_for_subroutine_usages(usages, returnnode.value_Y, parent_scope)

    def _parse_assignment_for_subroutine_usages(self, usages: Dict[Tuple[str, str], Set[str]],
                                                assignment: Assignment, parent_scope: Scope) -> None:
        # node.right (expression, or another Assignment)
        if isinstance(assignment.right, Assignment):
            self._parse_assignment_for_subroutine_usages(usages, assignment.right, parent_scope)
        else:
            self._parse_expression_for_subroutine_usages(usages, assignment.right, parent_scope)

    def _parse_asm_for_subroutine_usage(self, usages: Dict[Tuple[str, str], Set[str]],
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
                        symbol = parent_scope[name]
                    except LookupError:
                        pass
                    else:
                        if isinstance(symbol, Subroutine):
                            usages[(parent_scope.name, symbol.name)].add(str(asmnode.sourceref))

    def check_directives(self, module: Module) -> None:
        for node, parent in module.all_scopes():
            if isinstance(node, Module):
                # check module-level directives
                imports = set()  # type: Set[str]
                for directive in node.scope.filter_nodes(Directive):
                    if directive.name not in {"output", "zp", "address", "import", "saveregisters"}:
                        raise ParseError("invalid directive in module", None, directive.sourceref)
                    if directive.name == "import":
                        if imports & set(directive.args):
                            raise ParseError("duplicate import", None, directive.sourceref)
                        imports |= set(directive.args)
            if isinstance(node, (Block, Subroutine)):
                # check block and subroutine-level directives
                first_node = True
                if not node.scope:
                    continue
                for sub_node in node.scope.nodes:
                    if isinstance(sub_node, Directive):
                        if sub_node.name not in {"asmbinary", "asminclude", "breakpoint", "saveregisters"}:
                            raise ParseError("invalid directive in " + node.__class__.__name__.lower(), None, sub_node.sourceref)
                        if sub_node.name == "saveregisters" and not first_node:
                            raise ParseError("saveregisters directive should be the first", None, sub_node.sourceref)
                    first_node = False

    def process_imports(self, module: Module) -> None:
        # (recursively) imports the modules
        imported = []
        for directive in module.scope.filter_nodes(Directive):
            if directive.name == "import":
                if len(directive.args) < 1:
                    raise ParseError("missing argument(s) for import directive", None, directive.sourceref)
                for arg in directive.args:
                    filename = self.find_import_file(arg, directive.sourceref.file)
                    if not filename:
                        raise ParseError("imported file not found", None, directive.sourceref)
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
                module.scope.nodes.append(block)

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
        if exc.sourcetext is None:
            exc.sourcetext = linecache.getline(exc.sourceref.file, exc.sourceref.line).rstrip()
        if exc.sourcetext:
            # remove leading whitespace
            stripped = exc.sourcetext.lstrip()
            num_spaces = len(exc.sourcetext) - len(stripped)
            stripped = stripped.rstrip()
            print("  " + stripped, file=sys.stderr)
            if exc.sourceref.column:
                print("  " + ' ' * (exc.sourceref.column - num_spaces) + '^', file=sys.stderr)
        if sys.stderr.isatty():
            print("\x1b[0m", file=sys.stderr, end="", flush=True)


if __name__ == "__main__":
    description = "Compiler for IL65 language, code name 'Sick'"
    print("\n" + description)
    plyparser = PlyParser()
    m = plyparser.parse_file(sys.argv[1])
    optimize(m)
    print()
