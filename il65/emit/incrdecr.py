"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the code generator for the in-place incr and decr instructions.
Incrementing or decrementing variables by 1 (or another constant amount)
is quite frequent and this generates assembly code tweaked for this case.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from typing import Callable
from ..plyparse import Scope, VarType, VarDef, Register, IncrDecr, SymbolName, Dereference, datatype_of
from ..datatypes import DataType, REGISTER_BYTES
from . import CodeError, preserving_registers


def generate_incrdecr(out: Callable, stmt: IncrDecr, scope: Scope) -> None:
    assert isinstance(stmt.howmuch, (int, float)) and stmt.howmuch >= 0
    assert stmt.operator in ("++", "--")
    target = stmt.target        # one of Register/SymbolName/Dereference
    if isinstance(target, SymbolName):
        symdef = scope[target.name]
        if isinstance(symdef, VarDef):
            target = symdef
        else:
            raise CodeError("cannot incr/decr this", symdef)
    if stmt.howmuch > 255:
        if datatype_of(target, scope) != DataType.FLOAT:
            raise CodeError("only supports integer incr/decr by up to 255 for now")
    howmuch_str = str(stmt.howmuch)

    if isinstance(target, Register):
        reg = target.name
        # note: these operations below are all checked to be ok
        if stmt.operator == "++":
            if reg == 'A':
                # a += 1..255
                out("\vclc")
                out("\vadc  #" + howmuch_str)
            elif reg in REGISTER_BYTES:
                if stmt.howmuch == 1:
                    # x/y += 1
                    out("\vin{:s}".format(reg.lower()))
                else:
                    # x/y += 2..255
                    with preserving_registers({'A'}, scope, out):
                        out("\vt{:s}a".format(reg.lower()))
                        out("\vclc")
                        out("\vadc  #" + howmuch_str)
                        out("\vta{:s}".format(reg.lower()))
            elif reg == "AX":
                # AX += 1..255
                out("\vclc")
                out("\vadc  #" + howmuch_str)
                out("\vbcc  +")
                out("\vinx")
                out("+")
            elif reg == "AY":
                # AY += 1..255
                out("\vclc")
                out("\vadc  # " + howmuch_str)
                out("\vbcc  +")
                out("\viny")
                out("+")
            elif reg == "XY":
                if stmt.howmuch == 1:
                    # XY += 1
                    out("\vinx")
                    out("\vbne  +")
                    out("\viny")
                    out("+")
                else:
                    # XY += 2..255
                    with preserving_registers({'A'}, scope, out):
                        out("\vtxa")
                        out("\vclc")
                        out("\vadc  #" + howmuch_str)
                        out("\vtax")
                        out("\vbcc  +")
                        out("\viny")
                        out("+")
            else:
                raise CodeError("invalid incr register: " + reg)
        else:
            if reg == 'A':
                # a -= 1..255
                out("\vsec")
                out("\vsbc  #" + howmuch_str)
            elif reg in REGISTER_BYTES:
                if stmt.howmuch == 1:
                    # x/y -= 1
                    out("\vde{:s}".format(reg.lower()))
                else:
                    # x/y -= 2..255
                    with preserving_registers({'A'}, scope, out):
                        out("\vt{:s}a".format(reg.lower()))
                        out("\vsec")
                        out("\vsbc  #" + howmuch_str)
                        out("\vta{:s}".format(reg.lower()))
            elif reg == "AX":
                # AX -= 1..255
                out("\vsec")
                out("\vsbc  #" + howmuch_str)
                out("\vbcs  +")
                out("\vdex")
                out("+")
            elif reg == "AY":
                # AY -= 1..255
                out("\vsec")
                out("\vsbc  #" + howmuch_str)
                out("\vbcs  +")
                out("\vdey")
                out("+")
            elif reg == "XY":
                if stmt.howmuch == 1:
                    # XY -= 1
                    out("\vcpx  #0")
                    out("\vbne  +")
                    out("\vdey")
                    out("+\t\tdex")
                else:
                    # XY -= 2..255
                    with preserving_registers({'A'}, scope, out):
                        out("\vtxa")
                        out("\vsec")
                        out("\vsbc  #" + howmuch_str)
                        out("\vtax")
                        out("\vbcs  +")
                        out("\vdey")
                        out("+")
            else:
                raise CodeError("invalid decr register: " + reg)

    elif isinstance(target, VarDef):
        if target.vartype == VarType.CONST:
            raise CodeError("cannot modify a constant", target)
        what_str = target.name
        if target.datatype == DataType.BYTE:
            if stmt.howmuch == 1:
                out("\v{:s}  {:s}".format("inc" if stmt.operator == "++" else "dec", what_str))
            else:
                with preserving_registers({'A'}, scope, out):
                    out("\vlda  " + what_str)
                    if stmt.operator == "++":
                        out("\vclc")
                        out("\vadc  #" + howmuch_str)
                    else:
                        out("\vsec")
                        out("\vsbc  #" + howmuch_str)
                    out("\vsta  " + what_str)
        elif target.datatype == DataType.WORD:
            if stmt.howmuch == 1:
                # mem.word +=/-= 1
                if stmt.operator == "++":
                    out("\vinc  " + what_str)
                    out("\vbne  +")
                    out("\vinc  {:s}+1".format(what_str))
                    out("+")
                else:
                    with preserving_registers({'A'}, scope, out):
                        out("\vlda  " + what_str)
                        out("\vbne  +")
                        out("\vdec  {:s}+1".format(what_str))
                        out("+\t\tdec  " + what_str)
            else:
                # mem.word +=/-= 2..255
                if stmt.operator == "++":
                    with preserving_registers({'A'}, scope, out):
                        out("\vclc")
                        out("\vlda  " + what_str)
                        out("\vadc  #" + howmuch_str)
                        out("\vsta  " + what_str)
                        out("\vbcc  +")
                        out("\vinc  {:s}+1".format(what_str))
                        out("+")
                else:
                    with preserving_registers({'A'}, scope, out):
                        out("\vsec")
                        out("\vlda  " + what_str)
                        out("\vsbc  #" + howmuch_str)
                        out("\vsta  " + what_str)
                        out("\vbcs  +")
                        out("\vdec  {:s}+1".format(what_str))
                        out("+")
        elif target.datatype == DataType.FLOAT:
            if stmt.howmuch == 1.0:
                # special case for +/-1
                with preserving_registers({'A', 'X', 'Y'}, scope, out, loads_a_within=True):
                    out("\vldx  #<" + what_str)
                    out("\vldy  #>" + what_str)
                    if stmt.operator == "++":
                        out("\vjsr  c64flt.float_add_one")
                    else:
                        out("\vjsr  c64flt.float_sub_one")
            elif NOTYETIMPLEMENTED:     # XXX  for the  float += otherfloat cases
                with preserving_registers({'A', 'X', 'Y'}, scope, out, loads_a_within=True):
                    out("\vlda  #<" + stmt.value.name)
                    out("\vsta  c64.SCRATCH_ZPWORD1")
                    out("\vlda  #>" + stmt.value.name)
                    out("\vsta  c64.SCRATCH_ZPWORD1+1")
                    out("\vldx  #<" + what_str)
                    out("\vldy  #>" + what_str)
                    if stmt.operator == "++":
                        out("\vjsr  c64flt.float_add_SW1_to_XY")
                    else:
                        out("\vjsr  c64flt.float_sub_SW1_from_XY")
            else:
                raise CodeError("incr/decr missing float constant definition")
        else:
            raise CodeError("cannot in/decrement memory of type " + str(target.datatype), stmt.howmuch)

    else:
        raise CodeError("cannot in/decrement", target)
