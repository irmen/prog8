"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the code generator for the in-place incr and decr instructions.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from typing import Callable
from ..plyparse import Scope, AstNode, Register, IncrDecr, TargetRegisters, SymbolName, Dereference
from ..datatypes import DataType, REGISTER_BYTES
from . import CodeError, to_hex, preserving_registers


def datatype_of(node: AstNode, scope: Scope) -> DataType:
    if isinstance(node, (Dereference, Register)):
        return node.datatype
    if isinstance(node, SymbolName):
        symdef = scope[node.name]

    raise TypeError("cannot determine datatype", node)


def generate_incrdecr(out: Callable, stmt: IncrDecr) -> None:
    assert isinstance(stmt.howmuch, (int, float)) and stmt.howmuch >= 0
    assert stmt.operator in ("++", "--")
    target = stmt.target
    if isinstance(target, TargetRegisters):
        if len(target.registers) != 1:
            raise CodeError("incr/decr can operate on one register at a time only")
        target = target[0]
    # target = Register/SymbolName/Dereference
    if stmt.howmuch > 255:
        if isinstance(stmt.target, TargetRegisters)
    if stmt.what.datatype != DataType.FLOAT and not stmt.value.name and stmt.value.value > 0xff:
        raise CodeError("only supports integer incr/decr by up to 255 for now")   # XXX
    howmuch = stmt.value.value
    value_str = stmt.value.name or str(howmuch)
    if isinstance(stmt.what, RegisterValue):
        reg = stmt.what.register
        # note: these operations below are all checked to be ok
        if stmt.operator == "++":
            if reg == 'A':
                # a += 1..255
                out("\t\tclc")
                out("\t\tadc  #" + value_str)
            elif reg in REGISTER_BYTES:
                if howmuch == 1:
                    # x/y += 1
                    out("\t\tin{:s}".format(reg.lower()))
                else:
                    # x/y += 2..255
                    with preserving_registers({'A'}):
                        out("\t\tt{:s}a".format(reg.lower()))
                        out("\t\tclc")
                        out("\t\tadc  #" + value_str)
                        out("\t\tta{:s}".format(reg.lower()))
            elif reg == "AX":
                # AX += 1..255
                out("\t\tclc")
                out("\t\tadc  #" + value_str)
                out("\t\tbcc  +")
                out("\t\tinx")
                out("+")
            elif reg == "AY":
                # AY += 1..255
                out("\t\tclc")
                out("\t\tadc  # " + value_str)
                out("\t\tbcc  +")
                out("\t\tiny")
                out("+")
            elif reg == "XY":
                if howmuch == 1:
                    # XY += 1
                    out("\t\tinx")
                    out("\t\tbne  +")
                    out("\t\tiny")
                    out("+")
                else:
                    # XY += 2..255
                    with preserving_registers({'A'}):
                        out("\t\ttxa")
                        out("\t\tclc")
                        out("\t\tadc  #" + value_str)
                        out("\t\ttax")
                        out("\t\tbcc  +")
                        out("\t\tiny")
                        out("+")
            else:
                raise CodeError("invalid incr register: " + reg)
        else:
            if reg == 'A':
                # a -= 1..255
                out("\t\tsec")
                out("\t\tsbc  #" + value_str)
            elif reg in REGISTER_BYTES:
                if howmuch == 1:
                    # x/y -= 1
                    out("\t\tde{:s}".format(reg.lower()))
                else:
                    # x/y -= 2..255
                    with preserving_registers({'A'}):
                        out("\t\tt{:s}a".format(reg.lower()))
                        out("\t\tsec")
                        out("\t\tsbc  #" + value_str)
                        out("\t\tta{:s}".format(reg.lower()))
            elif reg == "AX":
                # AX -= 1..255
                out("\t\tsec")
                out("\t\tsbc  #" + value_str)
                out("\t\tbcs  +")
                out("\t\tdex")
                out("+")
            elif reg == "AY":
                # AY -= 1..255
                out("\t\tsec")
                out("\t\tsbc  #" + value_str)
                out("\t\tbcs  +")
                out("\t\tdey")
                out("+")
            elif reg == "XY":
                if howmuch == 1:
                    # XY -= 1
                    out("\t\tcpx  #0")
                    out("\t\tbne  +")
                    out("\t\tdey")
                    out("+\t\tdex")
                else:
                    # XY -= 2..255
                    with preserving_registers({'A'}):
                        out("\t\ttxa")
                        out("\t\tsec")
                        out("\t\tsbc  #" + value_str)
                        out("\t\ttax")
                        out("\t\tbcs  +")
                        out("\t\tdey")
                        out("+")
            else:
                raise CodeError("invalid decr register: " + reg)
    elif isinstance(stmt.what, (MemMappedValue, IndirectValue)):
        what = stmt.what
        if isinstance(what, IndirectValue):
            if isinstance(what.value, IntegerValue):
                what_str = what.value.name or to_hex(what.value.value)
            else:
                raise CodeError("invalid incr indirect type", what.value)
        else:
            what_str = what.name or to_hex(what.address)
        if what.datatype == DataType.BYTE:
            if howmuch == 1:
                out("\t\t{:s}  {:s}".format("inc" if stmt.operator == "++" else "dec", what_str))
            else:
                with preserving_registers({'A'}):
                    out("\t\tlda  " + what_str)
                    if stmt.operator == "++":
                        out("\t\tclc")
                        out("\t\tadc  #" + value_str)
                    else:
                        out("\t\tsec")
                        out("\t\tsbc  #" + value_str)
                    out("\t\tsta  " + what_str)
        elif what.datatype == DataType.WORD:
            if howmuch == 1:
                # mem.word +=/-= 1
                if stmt.operator == "++":
                    out("\t\tinc  " + what_str)
                    out("\t\tbne  +")
                    out("\t\tinc  {:s}+1".format(what_str))
                    out("+")
                else:
                    with preserving_registers({'A'}):
                        out("\t\tlda  " + what_str)
                        out("\t\tbne  +")
                        out("\t\tdec  {:s}+1".format(what_str))
                        out("+\t\tdec  " + what_str)
            else:
                # mem.word +=/-= 2..255
                if stmt.operator == "++":
                    with preserving_registers({'A'}):
                        out("\t\tclc")
                        out("\t\tlda  " + what_str)
                        out("\t\tadc  #" + value_str)
                        out("\t\tsta  " + what_str)
                        out("\t\tbcc  +")
                        out("\t\tinc  {:s}+1".format(what_str))
                        out("+")
                else:
                    with preserving_registers({'A'}):
                        out("\t\tsec")
                        out("\t\tlda  " + what_str)
                        out("\t\tsbc  #" + value_str)
                        out("\t\tsta  " + what_str)
                        out("\t\tbcs  +")
                        out("\t\tdec  {:s}+1".format(what_str))
                        out("+")
        elif what.datatype == DataType.FLOAT:
            if howmuch == 1.0:
                # special case for +/-1
                with preserving_registers({'A', 'X', 'Y'}, loads_a_within=True):
                    out("\t\tldx  #<" + what_str)
                    out("\t\tldy  #>" + what_str)
                    if stmt.operator == "++":
                        out("\t\tjsr  c64flt.float_add_one")
                    else:
                        out("\t\tjsr  c64flt.float_sub_one")
            elif stmt.value.name:
                with preserving_registers({'A', 'X', 'Y'}, loads_a_within=True):
                    out("\t\tlda  #<" + stmt.value.name)
                    out("\t\tsta  c64.SCRATCH_ZPWORD1")
                    out("\t\tlda  #>" + stmt.value.name)
                    out("\t\tsta  c64.SCRATCH_ZPWORD1+1")
                    out("\t\tldx  #<" + what_str)
                    out("\t\tldy  #>" + what_str)
                    if stmt.operator == "++":
                        out("\t\tjsr  c64flt.float_add_SW1_to_XY")
                    else:
                        out("\t\tjsr  c64flt.float_sub_SW1_from_XY")
            else:
                raise CodeError("incr/decr missing float constant definition")
        else:
            raise CodeError("cannot in/decrement memory of type " + str(what.datatype), howmuch)
    else:
        raise CodeError("cannot in/decrement " + str(stmt.what))
