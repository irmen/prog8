"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the tinyvm stack based program generator.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

# @todo

import os
import datetime
import pickle
from typing import BinaryIO, List, Tuple, Dict, Optional
from ..shared import CodeGenerationError, sanitycheck
from ...plyparse import Module, Block, Scope, VarDef, Expression, LiteralValue
from ...datatypes import VarType, DataType
import tinyvm.core
import tinyvm.program


class AssemblyGenerator:
    def __init__(self, module: Module, enable_floats: bool) -> None:
        self.module = module
        self.floats_enabled = enable_floats

    def generate(self, filename: str) -> None:
        with open(filename+".pickle", "wb") as stream:
            self._generate(stream)

    def _generate(self, out: BinaryIO) -> None:
        sanitycheck(self.module)
        program = self.header()
        program.blocks = self.blocks(self.module.nodes[0], None)        # type: ignore
        pickle.dump(program, out, pickle.HIGHEST_PROTOCOL)

    def header(self) -> tinyvm.program.Program:
        return tinyvm.program.Program([])

    def blocks(self, scope: Scope, parentblock_vm: Optional[tinyvm.program.Block]) -> List[tinyvm.program.Block]:
        blocks = []
        for node in scope.nodes:
            if isinstance(node, Block):
                variables = self.make_vars(node)
                labels, instructions = self.make_instructions(node)
                vmblock = tinyvm.program.Block(node.name, parentblock_vm, variables, instructions, labels)
                print(vmblock)
                blocks.append(vmblock)
                vmblock.blocks = self.blocks(node.nodes[0], vmblock)        # type: ignore
        return blocks

    def make_vars(self, block: Block) -> List[tinyvm.program.Variable]:
        variables = []
        for vardef in block.all_nodes(VarDef):
            assert isinstance(vardef, VarDef)
            dtype = self.translate_datatype(vardef.datatype)
            value = self.translate_value(vardef.value, dtype)
            if vardef.vartype == VarType.CONST:
                const = True
            elif vardef.vartype == VarType.VAR:
                const = False
            else:
                raise CodeGenerationError("unsupported vartype", vardef.vartype)
            variables.append(tinyvm.program.Variable(vardef.name, dtype, value, const))
        return variables

    def make_instructions(self, block: Block) -> Tuple[Dict[str, tinyvm.program.Instruction], List[tinyvm.program.Instruction]]:
        # returns a dict with the labels (named instruction pointers),
        # and a list of the program instructions.
        return {}, []

    def translate_datatype(self, datatype: DataType) -> tinyvm.core.DataType:
        table = {
            DataType.BYTE: tinyvm.core.DataType.BYTE,
            DataType.WORD: tinyvm.core.DataType.WORD,
            DataType.FLOAT: tinyvm.core.DataType.FLOAT,
            DataType.BYTEARRAY: tinyvm.core.DataType.ARRAY_BYTE,
            DataType.WORDARRAY: tinyvm.core.DataType.ARRAY_WORD,
            DataType.MATRIX: tinyvm.core.DataType.MATRIX_BYTE
        }
        dt = table.get(datatype, None)
        if dt:
            return dt
        raise CodeGenerationError("unsupported datatype", datatype)

    def translate_value(self, expr: Expression, dtypehint: Optional[tinyvm.core.DataType]) -> tinyvm.program.Value:
        if isinstance(expr, LiteralValue):
            dtype = dtypehint or tinyvm.core.DataType.guess_datatype_for(expr.value)
            return tinyvm.program.Value(dtype, expr.value)
        else:
            raise CodeGenerationError("cannot yet generate value for expression node", expr)

