"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the code generator for assignment statements.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from typing import Callable
from ..plyparse import Scope, Assignment, AugAssignment, Register, LiteralValue, SymbolName, VarDef, Dereference
from . import CodeError, preserving_registers, to_hex, Context
from ..datatypes import REGISTER_BYTES, VarType, DataType
from ..compile import Zeropage


def generate_assignment(ctx: Context) -> None:
    assert isinstance(ctx.stmt, Assignment)
    assert not isinstance(ctx.stmt.right, Assignment), "assignment should have been flattened"
    ctx.out("\v\t\t\t; " + ctx.stmt.lineref)
    ctx.out("\v; @todo assignment: {} = {}".format(ctx.stmt.left.nodes, ctx.stmt.right))
    # @todo assignment


def generate_aug_assignment(ctx: Context) -> None:
    # for instance: value += 3  (value = 0-255 for now)
    # left: Register, SymbolName, or Dereference. right: Expression/LiteralValue
    out = ctx.out
    stmt = ctx.stmt
    assert isinstance(stmt, AugAssignment)
    out("\v\t\t\t; " + stmt.lineref)
    lvalue = stmt.left
    rvalue = stmt.right
    if isinstance(lvalue, Register):
        if isinstance(rvalue, LiteralValue):
            if type(rvalue.value) is int:
                if 0 <= rvalue.value <= 255:
                    if stmt.operator not in ("<<=", ">>=") or rvalue.value != 0:
                        _generate_aug_reg_int(out, lvalue, stmt.operator, rvalue.value, "", ctx.scope)
                else:
                    raise CodeError("aug. assignment value must be 0..255", rvalue)
            else:
                raise CodeError("constant integer literal or variable required for now", rvalue)   # XXX
        elif isinstance(rvalue, SymbolName):
            symdef = ctx.scope.lookup(rvalue.name)
            if isinstance(symdef, VarDef):
                if symdef.vartype == VarType.CONST:
                    if symdef.datatype.isinteger() and 0 <= symdef.value.const_value() <= 255:  # type: ignore
                        _generate_aug_reg_int(out, lvalue, stmt.operator, symdef.value.const_value(), "", ctx.scope)   # type: ignore
                    else:
                        raise CodeError("aug. assignment value must be integer 0..255", rvalue)
                elif symdef.datatype == DataType.BYTE:
                    _generate_aug_reg_int(out, lvalue, stmt.operator, 0, symdef.name, ctx.scope)
                else:
                    raise CodeError("variable must be of type byte for now", rvalue)   # XXX
            else:
                raise CodeError("can only use variable name as symbol for aug assign rvalue", rvalue)
        elif isinstance(rvalue, Register):
            if lvalue.datatype == DataType.BYTE and rvalue.datatype == DataType.WORD:
                raise CodeError("cannot assign a combined 16-bit register to a single 8-bit register", rvalue)
            _generate_aug_reg_reg(out, lvalue, stmt.operator, rvalue, ctx.scope)
        elif isinstance(rvalue, Dereference):
            if isinstance(rvalue.operand, (LiteralValue, SymbolName)):
                if isinstance(rvalue.operand, LiteralValue):
                    what = to_hex(rvalue.operand.value)
                else:
                    symdef = rvalue.my_scope().lookup(rvalue.operand.name)
                    if isinstance(symdef, VarDef) and symdef.vartype == VarType.MEMORY:
                        what = to_hex(symdef.value.value)  # type: ignore
                    else:
                        what = rvalue.operand.name
                out("\vpha\n\vtya\n\vpha")   # save A, Y on stack
                out("\vlda  " + what)
                out("\vsta  il65_lib.SCRATCH_ZPWORD1")
                out("\vlda  {:s}+1".format(what))
                out("\vsta  il65_lib.SCRATCH_ZPWORD1+1")
                out("\vldy  #0")
                out("\vlda  (il65_lib.SCRATCH_ZPWORD1), y")
                a_reg = Register(name="A", sourceref=stmt.sourceref)        # type: ignore
                _generate_aug_reg_reg(out, lvalue, stmt.operator, a_reg, ctx.scope)
                out("\vst{:s}  il65_lib.SCRATCH_ZP1".format(lvalue.name.lower()))
                out("\vpla\n\vtay\n\vpla")  # restore A, Y from stack
                out("\vld{:s}  il65_lib.SCRATCH_ZP1".format(lvalue.name.lower()))
            elif isinstance(rvalue.operand, Register):
                assert rvalue.operand.datatype == DataType.WORD
                if rvalue.datatype != DataType.BYTE:
                    raise CodeError("aug. assignment value must be a byte, 0..255", rvalue)
                reg = rvalue.operand.name
                out("\vst{:s}  il65_lib.SCRATCH_ZPWORD1".format(reg[0].lower()))
                out("\vst{:s}  il65_lib.SCRATCH_ZPWORD1+1".format(reg[1].lower()))
                out("\vpha\n\vtya\n\vpha")   # save A, Y on stack
                out("\vldy  #0")
                out("\vlda  (il65_lib.SCRATCH_ZPWORD1), y")
                a_reg = Register(name="A", sourceref=stmt.sourceref)        # type: ignore
                _generate_aug_reg_reg(out, lvalue, stmt.operator, a_reg, ctx.scope)
                if lvalue.name != 'X':
                    out("\vst{:s}  il65_lib.SCRATCH_ZP1".format(lvalue.name.lower()))
                out("\vpla\n\vtay\n\vpla")  # restore A, Y from stack
                if lvalue.name != 'X':
                    out("\vld{:s}  il65_lib.SCRATCH_ZP1".format(lvalue.name.lower()))
            else:
                raise CodeError("invalid dereference operand type", rvalue)
        else:
            raise CodeError("invalid rvalue type", rvalue)
    else:
        raise CodeError("aug. assignment only implemented for registers for now", stmt.sourceref)  # XXX


def _generate_aug_reg_int(out: Callable, lvalue: Register, operator: str, rvalue: int, rname: str, scope: Scope) -> None:
    if rname:
        right_str = rname
    else:
        # an immediate value is provided in rvalue
        right_str = "#" + to_hex(rvalue)
    if operator == "+=":
        _gen_aug_plus_reg_int(lvalue, out, right_str, scope)
    elif operator == "-=":
        _gen_aug_minus_reg_int(lvalue, out, right_str, scope)
    elif operator == "&=":
        _gen_aug_and_reg_int(lvalue, out, right_str, scope)
    elif operator == "|=":
        _gen_aug_or_reg_int(lvalue, out, right_str, scope)
    elif operator == "^=":
        _gen_aug_xor_reg_int(lvalue, out, right_str, scope)
    elif operator == ">>=":
        _gen_aug_shiftright_reg_int(lvalue, out, rname, rvalue, scope)
    elif operator == "<<=":
        _gen_aug_shiftleft_reg_int(lvalue, out, rname, rvalue, scope)
    else:
        raise ValueError("invalid operator", operator)


def _generate_aug_reg_reg(out: Callable, lvalue: Register, operator: str, rvalue: Register, scope: Scope) -> None:
    if operator == "+=":
        _gen_aug_plus_reg_reg(lvalue, out, rvalue, scope)
    elif operator == "-=":
        _gen_aug_minus_reg_reg(lvalue, out, rvalue, scope)
    elif operator == "&=":
        _gen_aug_and_reg_reg(lvalue, out, rvalue, scope)
    elif operator == "|=":
        _gen_aug_or_reg_reg(lvalue, out, rvalue, scope)
    elif operator == "^=":
        _gen_aug_xor_reg_reg(lvalue, out, rvalue, scope)
    elif operator == ">>=":
        _gen_aug_shiftright_reg_reg(lvalue, out, rvalue, scope)
    elif operator == "<<=":
        _gen_aug_shiftleft_reg_reg(lvalue, out, rvalue, scope)
    else:
        raise ValueError("invalid operator", operator)


def _gen_aug_shiftleft_reg_int(lvalue: Register, out: Callable, rname: str, rvalue: int, scope: Scope) -> None:
    if rname:
        assert lvalue.name in REGISTER_BYTES, "only single registers for now"  # @todo <<=.word
        if lvalue.name == "A":
            preserve_regs = {'X'}
        elif lvalue.name == "X":
            preserve_regs = {'A'}
            out("\vtxa")
        elif lvalue.name == "Y":
            preserve_regs = {'A', 'X'}
            out("\vtya")
        with preserving_registers(preserve_regs, scope, out):
            out("\vldx  " + rname)
            out("\vjsr  il65_lib.asl_A_by_X")
            # out("\vbeq  +")
            # out("-\vasl  a")
            # out("\vdex")
            # out("\vbne  -")
            # put A back into target register
            if lvalue.name == "X":
                out("\vtax")
            elif lvalue.name == "Y":
                out("\vtay")
    else:
        def shifts_A(times: int) -> None:
            if times >= 8:
                out("\vlda  #0")
            else:
                for _ in range(min(8, times)):
                    out("\vasl  a")

        if lvalue.name == "A":
            shifts_A(rvalue)
        elif lvalue.name == "X":
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                shifts_A(rvalue)
                out("\vtax")
        elif lvalue.name == "Y":
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                shifts_A(rvalue)
                out("\vtay")
        else:
            raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo <<=.word


def _gen_aug_shiftright_reg_int(lvalue: Register, out: Callable, rname: str, rvalue: int, scope: Scope) -> None:
    if rname:
        assert lvalue.name in REGISTER_BYTES, "only single registers for now"  # @todo >>=.word
        if lvalue.name == "A":
            preserve_regs = {'X'}
        elif lvalue.name == "X":
            preserve_regs = {'A'}
            out("\vtxa")
        elif lvalue.name == "Y":
            preserve_regs = {'A', 'X'}
            out("\vtya")
        with preserving_registers(preserve_regs, scope, out):
            out("\vldx  " + rname)
            out("\vjsr  il65_lib.lsr_A_by_X")
            # put A back into target register
            if lvalue.name == "X":
                out("\vtax")
            if lvalue.name == "Y":
                out("\vtay")
    else:
        def shifts_A(times: int) -> None:
            if times >= 8:
                out("\vlda  #0")
            else:
                for _ in range(min(8, times)):
                    out("\vlsr  a")

        if lvalue.name == "A":
            shifts_A(rvalue)
        elif lvalue.name == "X":
            with preserving_registers({'A'}, scope, out):
                out("\vtxa")
                shifts_A(rvalue)
                out("\vtax")
        elif lvalue.name == "Y":
            with preserving_registers({'A'}, scope, out):
                out("\vtya")
                shifts_A(rvalue)
                out("\vtay")
        else:
            raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo >>=.word


def _gen_aug_xor_reg_int(lvalue: Register, out: Callable, right_str: str, scope: Scope) -> None:
    if lvalue.name == "A":
        out("\veor  " + right_str)
    elif lvalue.name == "X":
        with preserving_registers({'A'}, scope, out):
            out("\vtxa")
            out("\veor  " + right_str)
            out("\vtax")
    elif lvalue.name == "Y":
        with preserving_registers({'A'}, scope, out):
            out("\vtya")
            out("\veor  " + right_str)
            out("\vtay")
    else:
        raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo ^=.word


def _gen_aug_or_reg_int(lvalue: Register, out: Callable, right_str: str, scope: Scope) -> None:
    if lvalue.name == "A":
        out("\vora  " + right_str)
    elif lvalue.name == "X":
        with preserving_registers({'A'}, scope, out):
            out("\vtxa")
            out("\vora  " + right_str)
            out("\vtax")
    elif lvalue.name == "Y":
        with preserving_registers({'A'}, scope, out):
            out("\vtya")
            out("\vora  " + right_str)
            out("\vtay")
    else:
        raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo |=.word


def _gen_aug_and_reg_int(lvalue: Register, out: Callable, right_str: str, scope: Scope) -> None:
    if lvalue.name == "A":
        out("\vand  " + right_str)
    elif lvalue.name == "X":
        with preserving_registers({'A'}, scope, out):
            out("\vtxa")
            out("\vand  " + right_str)
            out("\vtax")
    elif lvalue.name == "Y":
        with preserving_registers({'A'}, scope, out):
            out("\vtya")
            out("\vand  " + right_str)
            out("\vtay")
    else:
        raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo &=.word


def _gen_aug_minus_reg_int(lvalue: Register, out: Callable, right_str: str, scope: Scope) -> None:
    if lvalue.name == "A":
        out("\vsec")
        out("\vsbc  " + right_str)
    elif lvalue.name == "X":
        with preserving_registers({'A'}, scope, out):
            out("\vtxa")
            out("\vsec")
            out("\vsbc  " + right_str)
            out("\vtax")
    elif lvalue.name == "Y":
        with preserving_registers({'A'}, scope, out):
            out("\vtya")
            out("\vsec")
            out("\vsbc  " + right_str)
            out("\vtay")
    else:
        raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo -=.word


def _gen_aug_plus_reg_int(lvalue: Register, out: Callable, right_str: str, scope: Scope) -> None:
    if lvalue.name == "A":
        out("\vclc")
        out("\vadc  " + right_str)
    elif lvalue.name == "X":
        with preserving_registers({'A'}, scope, out):
            out("\vtxa")
            out("\vclc")
            out("\vadc  " + right_str)
            out("\vtax")
    elif lvalue.name == "Y":
        with preserving_registers({'A'}, scope, out):
            out("\vtya")
            out("\vclc")
            out("\vadc  " + right_str)
            out("\vtay")
    else:
        raise CodeError("unsupported register for aug assign", str(lvalue))  # @todo +=.word


def _gen_aug_shiftleft_reg_reg(lvalue: Register, out: Callable, rvalue: Register, scope: Scope) -> None:
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


def _gen_aug_shiftright_reg_reg(lvalue: Register, out: Callable, rvalue: Register, scope: Scope) -> None:
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


def _gen_aug_xor_reg_reg(lvalue: Register, out: Callable, rvalue: Register, scope: Scope) -> None:
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


def _gen_aug_or_reg_reg(lvalue: Register, out: Callable, rvalue: Register, scope: Scope) -> None:
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


def _gen_aug_and_reg_reg(lvalue: Register, out: Callable, rvalue: Register, scope: Scope) -> None:
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


def _gen_aug_minus_reg_reg(lvalue: Register, out: Callable, rvalue: Register, scope: Scope) -> None:
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


def _gen_aug_plus_reg_reg(lvalue: Register, out: Callable, rvalue: Register, scope: Scope) -> None:
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
