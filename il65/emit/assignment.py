"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the code generator for assignment statements.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from typing import Callable
from ..plyparse import Scope, Assignment, AugAssignment, Register, LiteralValue, SymbolName, VarDef
from . import CodeError, preserving_registers, to_hex
from ..datatypes import REGISTER_BYTES, REGISTER_WORDS, VarType, DataType
from ..compile import Zeropage


def generate_assignment(out: Callable, stmt: Assignment, scope: Scope) -> None:
    out("\v\t\t\t; " + stmt.lineref)
    out("\v; @todo assignment")
    # @todo assignment


def generate_aug_assignment(out: Callable, stmt: AugAssignment, scope: Scope) -> None:
    # for instance: value += 3  (value = 0-255 for now)
    # left: Register, SymbolName, or Dereference. right: Expression/LiteralValue
    out("\v\t\t\t; " + stmt.lineref)
    lvalue = stmt.left
    rvalue = stmt.right
    if isinstance(lvalue, Register):
        if isinstance(rvalue, LiteralValue):
            if type(rvalue.value) is int:
                if 0 <= rvalue.value <= 255:
                    _generate_aug_reg_constant_int(out, lvalue, stmt.operator, rvalue.value, "", scope)
                else:
                    raise CodeError("assignment value must be 0..255", rvalue)
            else:
                raise CodeError("constant integer literal or variable required for now", rvalue)   # XXX
        elif isinstance(rvalue, SymbolName):
            symdef = scope.lookup(rvalue.name)
            if isinstance(symdef, VarDef) and symdef.vartype == VarType.CONST and symdef.datatype.isinteger():
                if 0 <= symdef.value.const_num_val() <= 255:
                    _generate_aug_reg_constant_int(out, lvalue, stmt.operator, 0, symdef.name, scope)
                else:
                    raise CodeError("assignment value must be 0..255", rvalue)
            else:
                raise CodeError("constant integer literal or variable required for now", rvalue)   # XXX
        elif isinstance(rvalue, Register):
            # @todo check value range (single register; 0-255)   @todo support combined registers
            _generate_aug_reg_reg(out, lvalue, stmt.operator, rvalue, scope)
        else:
            # @todo Register += symbolname / dereference  , _generate_aug_reg_mem?
            raise CodeError("invalid rvalue for augmented assignment on register", rvalue)
    else:
        raise CodeError("augmented assignment only implemented for registers for now", stmt.sourceref)  # XXX


def _generate_aug_reg_constant_int(out: Callable, lvalue: Register, operator: str, rvalue: int, rname: str, scope: Scope) -> None:
    r_str = rname or to_hex(rvalue)
    if operator == "+=":
        if lvalue.name == "A":
            out("\vclc")
            out("\vadc  #" + r_str)
        elif lvalue.name == "X":
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                out("\vclc")
                out("\vadc  #" + r_str)
                out("\vtax")
        elif lvalue.name == "Y":
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                out("\vclc")
                out("\vadc  #" + r_str)
                out("\vtay")
        else:
            raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo +=.word
    elif operator == "-=":
        if lvalue.name == "A":
            out("\vsec")
            out("\vsbc  #" + r_str)
        elif lvalue.name == "X":
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                out("\vsec")
                out("\vsbc  #" + r_str)
                out("\vtax")
        elif lvalue.name == "Y":
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                out("\vsec")
                out("\vsbc  #" + r_str)
                out("\vtay")
        else:
            raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo -=.word
    elif operator == "&=":
        if lvalue.name == "A":
            out("\vand  #" + r_str)
        elif lvalue.name == "X":
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                out("\vand  #" + r_str)
                out("\vtax")
        elif lvalue.name == "Y":
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                out("\vand  #" + r_str)
                out("\vtay")
        else:
            raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo &=.word
    elif operator == "|=":
        if lvalue.name == "A":
            out("\vora  #" + r_str)
        elif lvalue.name == "X":
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                out("\vora  #" + r_str)
                out("\vtax")
        elif lvalue.name == "Y":
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                out("\vora  #" + r_str)
                out("\vtay")
        else:
            raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo |=.word
    elif operator == "^=":
        if lvalue.name == "A":
            out("\veor  #" + r_str)
        elif lvalue.name == "X":
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                out("\veor  #" + r_str)
                out("\vtax")
        elif lvalue.name == "Y":
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                out("\veor  #" + r_str)
                out("\vtay")
        else:
            raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo ^=.word
    elif operator == ">>=":
        if rvalue.value > 0:
            def shifts_A(times: int) -> None:
                if times >= 8:
                    out("\vlda  #0")
                else:
                    for _ in range(min(8, times)):
                        out("\vlsr  a")
            if lvalue.name == "A":
                shifts_A(rvalue.value)
            elif lvalue.name == "X":
                with preserving_registers({'A'}, scope, out):
                    out("\vtxa")
                    shifts_A(rvalue.value)
                    out("\vtax")
            elif lvalue.name == "Y":
                with preserving_registers({'A'}, scope, out):
                    out("\vtya")
                    shifts_A(rvalue.value)
                    out("\vtay")
            else:
                raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo >>=.word
    elif operator == "<<=":
        if rvalue.value > 0:
            def shifts_A(times: int) -> None:
                if times >= 8:
                    out("\vlda  #0")
                else:
                    for _ in range(min(8, times)):
                        out("\vasl  a")
            if lvalue.name == "A":
                shifts_A(rvalue.value)
            elif lvalue.name == "X":
                with preserving_registers({'A'}, scope, out):
                    out("\vtxa")
                    shifts_A(rvalue.value)
                    out("\vtax")
            elif lvalue.name == "Y":
                with preserving_registers({'A'}, scope, out):
                    out("\vtya")
                    shifts_A(rvalue.value)
                    out("\vtay")
            else:
                raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo <<=.word


def _generate_aug_reg_reg(out: Callable, lvalue: Register, operator: str, rvalue: Register, scope: Scope) -> None:
    if operator == "+=":
        if rvalue.name not in REGISTER_BYTES:
            raise CodeError("unsupported rvalue register for aug assign", str(rvalue))  # @todo +=.word
        if lvalue.name == "A":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            out("\vclc")
            out("\vadc  " + to_hex(Zeropage.SCRATCH_B1))
        elif lvalue.name == "X":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                out("\vclc")
                out("\vadc  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtax")
        elif lvalue.name == "Y":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                out("\vclc")
                out("\vadc  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtay")
        else:
            raise CodeError("unsupported lvalue register for aug assign", str(lvalue))  # @todo +=.word
    elif operator == "-=":
        if rvalue.name not in REGISTER_BYTES:
            raise CodeError("unsupported rvalue register for aug assign", str(rvalue))  # @todo -=.word
        if lvalue.name == "A":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            out("\vsec")
            out("\vsbc  " + to_hex(Zeropage.SCRATCH_B1))
        elif lvalue.name == "X":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                out("\vsec")
                out("\vsbc  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtax")
        elif lvalue.name == "Y":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                out("\vsec")
                out("\vsbc  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtay")
        else:
            raise CodeError("unsupported lvalue register for aug assign", str(lvalue))  # @todo -=.word
    elif operator == "&=":
        if rvalue.name not in REGISTER_BYTES:
            raise CodeError("unsupported rvalue register for aug assign", str(rvalue))  # @todo &=.word
        if lvalue.name == "A":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            out("\vand  " + to_hex(Zeropage.SCRATCH_B1))
        elif lvalue.name == "X":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                out("\vand  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtax")
        elif lvalue.name == "Y":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                out("\vand  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtay")
        else:
            raise CodeError("unsupported lvalue register for aug assign", str(lvalue))  # @todo &=.word
    elif operator == "|=":
        if rvalue.name not in REGISTER_BYTES:
            raise CodeError("unsupported rvalue register for aug assign", str(rvalue))  # @todo |=.word
        if lvalue.name == "A":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            out("\vora  " + to_hex(Zeropage.SCRATCH_B1))
        elif lvalue.name == "X":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                out("\vora  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtax")
        elif lvalue.name == "Y":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                out("\vora  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtay")
        else:
            raise CodeError("unsupported lvalue register for aug assign", str(lvalue))  # @todo |=.word
    elif operator == "^=":
        if rvalue.name not in REGISTER_BYTES:
            raise CodeError("unsupported rvalue register for aug assign", str(rvalue))  # @todo ^=.word
        if lvalue.name == "A":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            out("\veor  " + to_hex(Zeropage.SCRATCH_B1))
        elif lvalue.name == "X":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                out("\veor  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtax")
        elif lvalue.name == "Y":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                out("\veor  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtay")
        else:
            raise CodeError("unsupported lvalue register for aug assign", str(lvalue))  # @todo ^=.word
    elif operator == ">>=":
        if rvalue.name not in REGISTER_BYTES:
            raise CodeError("unsupported rvalue register for aug assign", str(rvalue))  # @todo >>=.word
        if lvalue.name == "A":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            out("\vlsr  " + to_hex(Zeropage.SCRATCH_B1))
        elif lvalue.name == "X":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                out("\vlsr  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtax")
        elif lvalue.name == "Y":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                out("\vlsr  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtay")
        else:
            raise CodeError("unsupported lvalue register for aug assign", str(lvalue))  # @todo >>=.word
    elif operator == "<<=":
        if rvalue.name not in REGISTER_BYTES:
            raise CodeError("unsupported rvalue register for aug assign", str(rvalue))  # @todo <<=.word
        if lvalue.name == "A":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            out("\vasl  " + to_hex(Zeropage.SCRATCH_B1))
        elif lvalue.name == "X":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                out("\vasl  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtax")
        elif lvalue.name == "Y":
            out("\vst{:s}  {:s}".format(rvalue.name.lower(), to_hex(Zeropage.SCRATCH_B1)))
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                out("\vasl  " + to_hex(Zeropage.SCRATCH_B1))
                out("\vtay")
        else:
            raise CodeError("unsupported lvalue register for aug assign", str(lvalue))  # @todo <<=.word
