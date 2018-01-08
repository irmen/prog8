"""
Programming Language for 6502/6510 microprocessors
This is the preprocessing parser of the IL65 code, that only generates a symbol table.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from typing import List, Tuple, Set
from .parse import Parser, ParseResult, SymbolTable, SymbolDefinition
from .symbols import SourceRef, AstNode, InlineAsm


class PreprocessingParser(Parser):
    def __init__(self, filename: str, existing_imports: Set[str], parsing_import: bool=False) -> None:
        super().__init__(filename, "", existing_imports=existing_imports, parsing_import=parsing_import)
        self.print_block_parsing = False

    def preprocess(self) -> Tuple[List[Tuple[int, str]], SymbolTable]:
        def cleanup_table(symbols: SymbolTable):
            symbols.owning_block = None   # not needed here
            for name, symbol in list(symbols.symbols.items()):
                if isinstance(symbol, SymbolTable):
                    cleanup_table(symbol)
                elif not isinstance(symbol, SymbolDefinition):
                    del symbols.symbols[name]
        self.parse()
        cleanup_table(self.root_scope)
        return self.lines, self.root_scope

    def print_warning(self, text: str, sourceref: SourceRef=None) -> None:
        pass

    def load_source(self, filename: str) -> List[Tuple[int, str]]:
        lines = super().load_source(filename)
        # can do some additional source-level preprocessing here
        return lines

    def parse_file(self) -> ParseResult:
        print("preprocessing", self.sourceref.file)
        self._parse_1()
        return self.result

    def parse_asminclude(self, line: str) -> InlineAsm:
        return InlineAsm([], self.sourceref)

    def parse_statement(self, line: str) -> AstNode:
        return None

    def parse_var_def(self, line: str) -> None:
        super().parse_var_def(line)

    def parse_const_def(self, line: str) -> None:
        super().parse_const_def(line)

    def parse_memory_def(self, line: str, is_zeropage: bool=False) -> None:
        super().parse_memory_def(line, is_zeropage)

    def parse_label(self, labelname: str, rest: str) -> None:
        super().parse_label(labelname, rest)

    def parse_subroutine_def(self, line: str) -> None:
        super().parse_subroutine_def(line)

    def create_import_parser(self, filename: str, outputdir: str) -> Parser:
        return PreprocessingParser(filename, parsing_import=True, existing_imports=self.existing_imports)

    def print_import_progress(self, message: str, *args: str) -> None:
        pass
