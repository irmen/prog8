"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the code generator for gotos and subroutine calls.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from ..plyparse import Goto, SubCall
from . import Context


def generate_goto(ctx: Context) -> None:
    stmt = ctx.stmt
    assert isinstance(stmt, Goto)
    pass  # @todo


def generate_subcall(ctx: Context) -> None:
    stmt = ctx.stmt
    assert isinstance(stmt, SubCall)
    pass  # @todo
