"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the code generator for the in-place incr and decr instructions.
Incrementing or decrementing variables by a small value 0..255 (for integers)
is quite frequent and this generates assembly code tweaked for this case.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from ..plyparse import VarType, VarDef, Register, IncrDecr, SymbolName, Dereference, LiteralValue, datatype_of
from ..datatypes import DataType, REGISTER_BYTES
from . import CodeError, preserving_registers, to_hex, Context


def generate_incrdecr(ctx: Context) -> None:
    out = ctx.out
    stmt = ctx.stmt
    scope = ctx.scope
    assert isinstance(stmt, IncrDecr)
    assert isinstance(stmt.howmuch, (int, float)) and stmt.howmuch >= 0
    assert stmt.operator in ("++", "--")
    if stmt.howmuch == 0:
        return
    target = stmt.target        # one of Register/SymbolName/Dereference, or a VarDef
    if isinstance(target, SymbolName):
        symdef = scope.lookup(target.name)
        if isinstance(symdef, VarDef):
            target = symdef     # type: ignore
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
            if not ctx.floats_enabled:
                raise CodeError("floating point numbers not enabled via option")
            if stmt.howmuch == 1.0:
                # special case for +/-1
                with preserving_registers({'A', 'X', 'Y'}, scope, out, loads_a_within=True):
                    out("\vldx  #<" + what_str)
                    out("\vldy  #>" + what_str)
                    if stmt.operator == "++":
                        out("\vjsr  c64flt.float_add_one")
                    else:
                        out("\vjsr  c64flt.float_sub_one")
            elif stmt.howmuch != 0:
                float_name = scope.define_float_constant(stmt.howmuch)
                with preserving_registers({'A', 'X', 'Y'}, scope, out, loads_a_within=True):
                    out("\vlda  #<" + float_name)
                    out("\vsta  c64.SCRATCH_ZPWORD1")
                    out("\vlda  #>" + float_name)
                    out("\vsta  c64.SCRATCH_ZPWORD1+1")
                    out("\vldx  #<" + what_str)
                    out("\vldy  #>" + what_str)
                    if stmt.operator == "++":
                        out("\vjsr  c64flt.float_add_SW1_to_XY")
                    else:
                        out("\vjsr  c64flt.float_sub_SW1_from_XY")
        else:
            raise CodeError("cannot in/decrement memory of type " + str(target.datatype), stmt.howmuch)

    elif isinstance(target, Dereference):
        if isinstance(target.operand, (LiteralValue, SymbolName)):
            if isinstance(target.operand, LiteralValue):
                what = to_hex(target.operand.value)
            else:
                symdef = target.my_scope().lookup(target.operand.name)
                if isinstance(symdef, VarDef) and symdef.vartype == VarType.MEMORY:
                    what = to_hex(symdef.value.value)   # type: ignore
                else:
                    what = target.operand.name
            if stmt.howmuch == 1:
                if target.datatype == DataType.FLOAT:
                    if not ctx.floats_enabled:
                        raise CodeError("floating point numbers not enabled via option")
                    with preserving_registers({'A', 'X', 'Y'}, scope, out, loads_a_within=True):
                        out("\vldx  " + what)
                        out("\vldy  {:s}+1".format(what))
                        if stmt.operator == "++":
                            out("\vjsr  c64flt.float_add_one")
                        else:
                            out("\vjsr  c64flt.float_sub_one")
                else:
                    with preserving_registers({'A', 'Y'}, scope, out, loads_a_within=True):
                        out("\vlda  " + what)
                        out("\vldy  {:s}+1".format(what))
                        if target.datatype == DataType.BYTE:
                            out("\vclc" if stmt.operator == "++" else "\vsec")
                            out("\vjsr  il65_lib.incrdecr_deref_byte_reg_AY")
                        elif target.datatype == DataType.WORD:
                            out("\vclc" if stmt.operator == "++" else "\vsec")
                            out("\vjsr  il65_lib.incrdecr_deref_word_reg_AY")
                        else:
                            raise CodeError("cannot inc/decrement dereferenced literal of type " + str(target.datatype), stmt)
            else:
                raise CodeError("can't inc/dec this by something else as 1 right now", stmt)  # XXX
        elif isinstance(target.operand, Register):
            assert target.operand.datatype == DataType.WORD
            reg = target.operand.name
            if stmt.howmuch == 1:
                out("\vclc" if stmt.operator == "++" else "\vsec")
                if target.datatype == DataType.BYTE:
                    with preserving_registers({'A', 'Y'}, scope, out, loads_a_within=True):
                        out("\vjsr  il65_lib.incrdecr_deref_byte_reg_" + reg)
                else:
                    with preserving_registers({'A', 'Y'}, scope, out, loads_a_within=True):
                        out("\vjsr  il65_lib.incrdecr_deref_word_reg_" + reg)
            else:
                raise CodeError("can't inc/dec this by something else as 1 right now", stmt)  # XXX
        else:
            raise TypeError("invalid dereference operand type", target)

    else:
        raise CodeError("cannot inc/decrement", target)      # @todo support more
