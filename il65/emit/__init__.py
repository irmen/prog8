"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the assembly code generator (from the parse tree)

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import contextlib
import math
import attr
from typing import Set, Callable
from ..datatypes import FLOAT_MAX_POSITIVE, FLOAT_MAX_NEGATIVE
from ..plyparse import Scope, AstNode
from ..compile import Zeropage


class CodeError(Exception):
    pass


@attr.s(repr=False, cmp=False)
class Context:
    out = attr.ib(type=Callable)
    stmt = attr.ib(type=AstNode)
    scope = attr.ib(type=Scope)
    floats_enabled = attr.ib(type=bool)


def to_hex(number: int) -> str:
    # 0..15 -> "0".."15"
    # 16..255 -> "$10".."$ff"
    # 256..65536 -> "$0100".."$ffff"
    assert type(number) is int
    if number is None:
        raise ValueError("number")
    if 0 <= number < 16:
        return str(number)
    if 0 <= number < 0x100:
        return "${:02x}".format(number)
    if 0 <= number < 0x10000:
        return "${:04x}".format(number)
    raise OverflowError(number)


def to_mflpt5(number: float) -> bytearray:
    # algorithm here https://sourceforge.net/p/acme-crossass/code-0/62/tree/trunk/ACME_Lib/cbm/mflpt.a
    number = float(number)
    if number < FLOAT_MAX_NEGATIVE or number > FLOAT_MAX_POSITIVE:
        raise OverflowError("floating point number out of 5-byte mflpt range", number)
    if number == 0.0:
        return bytearray([0, 0, 0, 0, 0])
    if number < 0.0:
        sign = 0x80000000
        number = -number
    else:
        sign = 0x00000000
    mant, exp = math.frexp(number)
    exp += 128
    if exp < 1:
        # underflow, use zero instead
        return bytearray([0, 0, 0, 0, 0])
    if exp > 255:
        raise OverflowError("floating point number out of 5-byte mflpt range", number)
    mant = sign | int(mant * 0x100000000) & 0x7fffffff
    return bytearray([exp]) + int.to_bytes(mant, 4, "big")


def mflpt5_to_float(mflpt: bytearray) -> float:
    # algorithm here https://sourceforge.net/p/acme-crossass/code-0/62/tree/trunk/ACME_Lib/cbm/mflpt.a
    if mflpt == bytearray([0, 0, 0, 0, 0]):
        return 0.0
    exp = mflpt[0] - 128
    sign = mflpt[1] & 0x80
    number = 0x80000000 | int.from_bytes(mflpt[1:], "big")
    number = float(number) * 2**exp / 0x100000000
    return -number if sign else number


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


def scoped_name(node_with_name: AstNode, current_scope: Scope) -> str:
    node_scope = node_with_name.my_scope()
    return node_with_name.name if node_scope is current_scope else node_scope.name + "." + node_with_name.name
