"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the 6502 assembly code generator (directly from the parse tree)

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import contextlib
import attr
from typing import Set, Callable
from ...plyparse import Scope, AstNode
from ...compile import Zeropage


@attr.s(repr=False, cmp=False)
class Context:
    out = attr.ib(type=Callable)
    stmt = attr.ib(type=AstNode)
    scope = attr.ib(type=Scope)
    floats_enabled = attr.ib(type=bool)


@contextlib.contextmanager
def preserving_registers(registers: Set[str], scope: Scope, out: Callable, loads_a_within: bool=False, force_preserve: bool=False):
    # this sometimes clobbers a ZP scratch register and is therefore NOT safe to use in interrupts
    # see http://6502.org/tutorials/register_preservation.html
    if not scope.save_registers and not force_preserve:
        yield
        return
    if registers == {'A'}:
        out("\t\tpha")
        yield
        out("\t\tpla")
    elif registers:
        if not loads_a_within:
            out("\t\tsta  ${:02x}".format(Zeropage.SCRATCH_B2))
        if 'A' in registers:
            out("\t\tpha")
        if 'X' in registers:
            out("\t\ttxa\n\t\tpha")
        if 'Y' in registers:
            out("\t\ttya\n\t\tpha")
        if not loads_a_within:
            out("\t\tlda  ${:02x}".format(Zeropage.SCRATCH_B2))
        yield
        if 'X' in registers and 'Y' in registers:
            if 'A' not in registers:
                out("\t\tsta  ${:02x}".format(Zeropage.SCRATCH_B2))
                out("\t\tpla\n\t\ttay")
                out("\t\tpla\n\t\ttax")
                out("\t\tlda  ${:02x}".format(Zeropage.SCRATCH_B2))
            else:
                out("\t\tpla\n\t\ttay")
                out("\t\tpla\n\t\ttax")
        else:
            if 'Y' in registers:
                if 'A' not in registers:
                    out("\t\tsta  ${:02x}".format(Zeropage.SCRATCH_B2))
                    out("\t\tpla\n\t\ttay")
                    out("\t\tlda  ${:02x}".format(Zeropage.SCRATCH_B2))
                else:
                    out("\t\tpla\n\t\ttay")
            if 'X' in registers:
                if 'A' not in registers:
                    out("\t\tsta  ${:02x}".format(Zeropage.SCRATCH_B2))
                    out("\t\tpla\n\t\ttax")
                    out("\t\tlda  ${:02x}".format(Zeropage.SCRATCH_B2))
                else:
                    out("\t\tpla\n\t\ttax")
        if 'A' in registers:
            out("\t\tpla")
    else:
        yield


