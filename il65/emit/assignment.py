"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the code generator for assignment statements.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from typing import Callable
from ..plyparse import AstNode, Scope, VarDef, Dereference, Register, TargetRegisters,\
    LiteralValue, Assignment, AugAssignment
from ..datatypes import DataType
from ..plyparse import SymbolName


def datatype_of(assignmenttarget: AstNode, scope: Scope) -> DataType:
    if isinstance(assignmenttarget, (VarDef, Dereference, Register)):
        return assignmenttarget.datatype
    elif isinstance(assignmenttarget, SymbolName):
        symdef = scope[assignmenttarget.name]
        if isinstance(symdef, VarDef):
            return symdef.datatype
    elif isinstance(assignmenttarget, TargetRegisters):
        if len(assignmenttarget.registers) == 1:
            return datatype_of(assignmenttarget.registers[0], scope)
    raise TypeError("cannot determine datatype", assignmenttarget)


def generate_assignment(out: Callable, stmt: Assignment) -> None:
    assert stmt.right is not None
    rvalue = stmt.right
    if isinstance(stmt.right, LiteralValue):
        rvalue = stmt.right.value
    # @todo


def generate_aug_assignment(out: Callable, stmt: AugAssignment) -> None:
    assert stmt.right is not None
    pass  # @todo
