"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
Shared logic for the code generators.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""


import math
from ..datatypes import FLOAT_MAX_POSITIVE, FLOAT_MAX_NEGATIVE
from ..plyparse import Module, Label, Block, Directive, VarDef
from ..plylex import print_bold


class CodeGenerationError(Exception):
    pass


def sanitycheck(module: Module) -> None:
    for label in module.all_nodes(Label):
        if label.name == "start" and label.my_scope().name == "main":      # type: ignore
            break
    else:
        print_bold("ERROR: program entry point is missing ('start' label in 'main' block)\n")
        raise SystemExit(1)
    all_blocknames = [b.name for b in module.all_nodes(Block)]        # type: ignore
    unique_blocknames = set(all_blocknames)
    if len(all_blocknames) != len(unique_blocknames):
        for name in unique_blocknames:
            all_blocknames.remove(name)
        raise CodeGenerationError("there are duplicate block names", all_blocknames)
    zpblock = module.zeropage()
    if zpblock:
        # ZP block contains no code?
        for stmt in zpblock.scope.nodes:
            if not isinstance(stmt, (Directive, VarDef)):
                raise CodeGenerationError("ZP block can only contain directive and var")


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
