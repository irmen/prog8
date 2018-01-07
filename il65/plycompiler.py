import os
import sys
import linecache
from typing import Optional, Generator, Tuple, Set
from .plyparser import parse_file, Module, Directive, Block, Subroutine, AstNode
from .parse import ParseError
from .symbols import SourceRef


class PlyParser:
    def __init__(self):
        self.parse_errors = 0
        self.parsing_import = False

    def parse_file(self, filename: str) -> Module:
        print("parsing:", filename)
        module = parse_file(filename)
        try:
            self.check_directives(module)
            self.remove_empty_blocks(module)
            self.process_imports(module)
        except ParseError as x:
            self.handle_parse_error(x)
        return module

    def remove_empty_blocks(self, module: Module) -> None:
        # remove blocks without name and without address, or that are empty
        for scope, parent in self.recurse_scopes(module):
            if isinstance(scope, (Subroutine, Block)):
                if not scope.scope:
                    continue
                if all(isinstance(n, Directive) for n in scope.scope.nodes):
                    empty = True
                    for n in scope.scope.nodes:
                        empty = empty and n.name not in {"asmbinary", "asminclude"}
                    if empty:
                        self.print_warning("ignoring empty block or subroutine", scope.sourceref)
                        assert isinstance(parent, (Block, Module))
                        parent.scope.nodes.remove(scope)
            if isinstance(scope, Block):
                if not scope.name and scope.address is None:
                    self.print_warning("ignoring block without name and address", scope.sourceref)
                    assert isinstance(parent, Module)
                    parent.scope.nodes.remove(scope)

    def check_directives(self, module: Module) -> None:
        for node, parent in self.recurse_scopes(module):
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

    def recurse_scopes(self, module: Module) -> Generator[Tuple[AstNode, AstNode], None, None]:
        # generator that recursively yields through the scopes (preorder traversal), yields (node, parent_node) tuples.
        yield module, None
        for block in list(module.scope.filter_nodes(Block)):
            yield block, module
            for subroutine in list(block.scope.filter_nodes(Subroutine)):
                yield subroutine, block

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
                    imported_module = self.import_file(filename)
                    imported_module.scope.parent_scope = module.scope
                    imported.append(imported_module)
        # append the imported module's contents (blocks) at the end of the current module
        for imported_module in imported:
            for block in imported_module.scope.filter_nodes(Block):
                module.scope.nodes.append(block)

    def import_file(self, filename: str) -> Module:
        sub_parser = PlyParser()
        return sub_parser.parse_file(filename)

    def find_import_file(self, modulename: str, sourcefile: str) -> Optional[str]:
        filename_at_source_location = os.path.join(os.path.split(sourcefile)[0], modulename)
        filename_at_libs_location = os.path.join(os.getcwd(), "lib", modulename)
        candidates = [modulename,
                      filename_at_source_location,
                      filename_at_libs_location,
                      modulename+".ill",
                      filename_at_source_location+".ill",
                      filename_at_libs_location+".ill"]
        for filename in candidates:
            if os.path.isfile(filename):
                return filename
        return None

    def print_warning(self, text: str, sourceref: SourceRef=None) -> None:
        if sourceref:
            self.print_bold("warning: {}: {:s}".format(sourceref, text))
        else:
            self.print_bold("warning: " + text)

    def print_bold(self, text: str) -> None:
        if sys.stdout.isatty():
            print("\x1b[1m" + text + "\x1b[0m", flush=True)
        else:
            print(text)

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
    plyparser = PlyParser()
    m = plyparser.parse_file(sys.argv[1])
    print(str(m)[:400], "...")
