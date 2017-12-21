"""
Intermediate Language for 6502/6510 microprocessors
This is the preprocessing parser of the IL65 code, that only generates a symbol table.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

from typing import List, Tuple
from .parse import Parser, ParseResult, SymbolTable, SymbolDefinition


class PreprocessingParser(Parser):
    def __init__(self, filename: str) -> None:
        super().__init__(filename, "", parsing_import=True)

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

    def load_source(self, filename: str) -> List[Tuple[int, str]]:
        lines = super().load_source(filename)
        # can do some additional source-level preprocessing here
        return lines

    def parse_file(self) -> ParseResult:
        print("\npreprocessing", self.sourceref.file)
        self._parse_1()
        return self.result

    def parse_asminclude(self, line: str) -> ParseResult.InlineAsm:
        return ParseResult.InlineAsm(self.sourceref.line, [])

    def parse_statement(self, line: str) -> ParseResult._Stmt:
        return None     # type: ignore

    def parse_var_def(self, line: str) -> None:
        super().parse_var_def(line)

    def parse_const_def(self, line: str) -> None:
        super().parse_const_def(line)

    def parse_memory_def(self, line: str, is_zeropage: bool=False) -> None:
        super().parse_memory_def(line, is_zeropage)

    def parse_label(self, line: str) -> None:
        super().parse_label(line)

    def parse_subx_def(self, line: str) -> None:
        super().parse_subx_def(line)

    def create_import_parser(self, filename: str, outputdir: str) -> 'Parser':
        return PreprocessingParser(filename)
