"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the code generator for variable declarations and initialization.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from collections import defaultdict
from typing import Dict, List, Callable, Any, no_type_check
from ..plyparse import Block, VarType, VarDef, LiteralValue, AddressOf
from ..datatypes import DataType, STRING_DATATYPES
from . import to_hex, to_mflpt5, CodeError


def generate_block_init(out: Callable, block: Block) -> None:
    # generate the block initializer
    # @todo add a block initializer subroutine that can contain custom reset/init code? (static initializer)
    # @todo will be called at program start automatically, so there's no risk of forgetting to call it manually

    def _memset(varname: str, value: int, size: int) -> None:
        if size > 6:
            out("\vlda  #<" + varname)
            out("\vsta  il65_lib.SCRATCH_ZPWORD1")
            out("\vlda  #>" + varname)
            out("\vsta  il65_lib.SCRATCH_ZPWORD1+1")
            out("\vlda  #" + to_hex(value))
            out("\vldx  #<" + to_hex(size))
            out("\vldy  #>" + to_hex(size))
            out("\vjsr  il65_lib.memset")
        else:
            out("\vlda  #" + to_hex(value))
            for i in range(size):
                out("\vsta  {:s}+{:d}".format(varname, i))

    def _memsetw(varname: str, value: int, size: int) -> None:
        if size > 4:
            out("\vlda  #<" + varname)
            out("\vsta  il65_lib.SCRATCH_ZPWORD1")
            out("\vlda  #>" + varname)
            out("\vsta  il65_lib.SCRATCH_ZPWORD1+1")
            out("\vlda  #<" + to_hex(size))
            out("\vsta  il65_lib.SCRATCH_ZPWORD2")
            out("\vlda  #>" + to_hex(size))
            out("\vsta  il65_lib.SCRATCH_ZPWORD2+1")
            out("\vlda  #<" + to_hex(value))
            out("\vldx  #>" + to_hex(value))
            out("\vjsr  il65_lib.memsetw")
        else:
            out("\vlda  #<" + to_hex(value))
            out("\vldy  #>" + to_hex(value))
            for i in range(size):
                out("\vsta  {:s}+{:d}".format(varname, i * 2))
                out("\vsty  {:s}+{:d}".format(varname, i * 2 + 1))

    out("_il65_init_block\v; (re)set vars to initial values")
    float_inits = {}
    prev_value_a, prev_value_x = None, None
    vars_by_datatype = defaultdict(list)  # type: Dict[DataType, List[VarDef]]
    for vardef in block.all_nodes(VarDef):
        if vardef.vartype == VarType.VAR:       # type: ignore
            vars_by_datatype[vardef.datatype].append(vardef)           # type: ignore
    for bytevar in sorted(vars_by_datatype[DataType.BYTE], key=lambda vd: vd.value):
        assert isinstance(bytevar.value, LiteralValue) and type(bytevar.value.value) is int
        if bytevar.value.value != prev_value_a:
            out("\vlda  #${:02x}".format(bytevar.value.value))
            prev_value_a = bytevar.value.value
        out("\vsta  {:s}".format(bytevar.name))
    for wordvar in sorted(vars_by_datatype[DataType.WORD], key=lambda vd: vd.value):
        if isinstance(wordvar.value, AddressOf):
            raise CodeError("can't yet use addressof here", wordvar.sourceref)  # XXX
        assert isinstance(wordvar.value, LiteralValue) and type(wordvar.value.value) is int
        v_hi, v_lo = divmod(wordvar.value.value, 256)
        if v_hi != prev_value_a:
            out("\vlda  #${:02x}".format(v_hi))
            prev_value_a = v_hi
        if v_lo != prev_value_x:
            out("\vldx  #${:02x}".format(v_lo))
            prev_value_x = v_lo
        out("\vsta  {:s}".format(wordvar.name))
        out("\vstx  {:s}+1".format(wordvar.name))
    for floatvar in vars_by_datatype[DataType.FLOAT]:
        assert isinstance(floatvar.value, LiteralValue) and type(floatvar.value.value) in (int, float)
        fpbytes = to_mflpt5(floatvar.value.value)  # type: ignore
        float_inits[floatvar.name] = (floatvar.name, fpbytes, floatvar.value)
    for arrayvar in vars_by_datatype[DataType.BYTEARRAY]:
        assert isinstance(arrayvar.value, LiteralValue) and type(arrayvar.value.value) is int
        _memset(arrayvar.name, arrayvar.value.value, arrayvar.size[0])
    for arrayvar in vars_by_datatype[DataType.WORDARRAY]:
        assert isinstance(arrayvar.value, LiteralValue) and type(arrayvar.value.value) is int
        _memsetw(arrayvar.name, arrayvar.value.value, arrayvar.size[0])
    for arrayvar in vars_by_datatype[DataType.MATRIX]:
        assert isinstance(arrayvar.value, LiteralValue) and type(arrayvar.value.value) is int
        _memset(arrayvar.name, arrayvar.value.value, arrayvar.size[0] * arrayvar.size[1])
    if float_inits:
        out("\vldx  #4")
        out("-")
        for varname, (vname, b, fv) in sorted(float_inits.items()):
            out("\vlda  _init_float_{:s},x".format(varname))
            out("\vsta  {:s},x".format(vname))
        out("\vdex")
        out("\vbpl  -")
    out("\vrts\n")
    for varname, (vname, fpbytes, fpvalue) in sorted(float_inits.items()):
        assert isinstance(fpvalue, LiteralValue)
        out("_init_float_{:s}\t\t.byte  ${:02x}, ${:02x}, ${:02x}, ${:02x}, ${:02x}\t; {}".format(varname, *fpbytes, fpvalue.value))
    all_string_vars = []
    for svtype in STRING_DATATYPES:
        all_string_vars.extend(vars_by_datatype[svtype])
    for strvar in all_string_vars:
        # string vars are considered to be a constant, and are statically initialized.
        _generate_string_var(out, strvar)
    out("")


def generate_block_vars(out: Callable, block: Block, zeropage: bool=False) -> None:
    # Generate the block variable storage.
    # The memory bytes of the allocated variables is set to zero (so it compresses very well),
    # their actual starting values are set by the block init code.
    vars_by_vartype = defaultdict(list)  # type: Dict[VarType, List[VarDef]]
    for vardef in block.all_nodes(VarDef):
        vars_by_vartype[vardef.vartype].append(vardef)             # type: ignore
    out("; constants")
    for vardef in vars_by_vartype.get(VarType.CONST, []):
        if vardef.datatype == DataType.FLOAT:
            out("\v{:s} = {}".format(vardef.name, _numeric_value_str(vardef.value)))
        elif vardef.datatype in (DataType.BYTE, DataType.WORD):
            assert isinstance(vardef.value.value, int)      # type: ignore
            out("\v{:s} = {:s}".format(vardef.name, _numeric_value_str(vardef.value, True)))
        elif vardef.datatype.isstring():
            # a const string is just a string variable in the generated assembly
            _generate_string_var(out, vardef)
        else:
            raise CodeError("invalid const type", vardef)
    # @todo float constants that are used in expressions
    out("; memory mapped variables")
    for vardef in vars_by_vartype.get(VarType.MEMORY, []):
        # create a definition for variables at a specific place in memory (memory-mapped)
        assert isinstance(vardef.value.value, int)      # type: ignore
        if vardef.datatype.isnumeric():
            assert vardef.size == [1]
            out("\v{:s} = {:s}\t; {:s}".format(vardef.name, to_hex(vardef.value.value), vardef.datatype.name.lower()))  # type: ignore
        elif vardef.datatype == DataType.BYTEARRAY:
            assert len(vardef.size) == 1
            out("\v{:s} = {:s}\t; array of {:d} bytes".format(vardef.name, to_hex(vardef.value.value), vardef.size[0]))  # type: ignore
        elif vardef.datatype == DataType.WORDARRAY:
            assert len(vardef.size) == 1
            out("\v{:s} = {:s}\t; array of {:d} words".format(vardef.name, to_hex(vardef.value.value), vardef.size[0]))  # type: ignore
        elif vardef.datatype == DataType.MATRIX:
            assert len(vardef.size) in (2, 3)
            if len(vardef.size) == 2:
                comment = "matrix of {:d} by {:d} = {:d} bytes".format(vardef.size[0], vardef.size[1], vardef.size[0]*vardef.size[1])
            elif len(vardef.size) == 3:
                comment = "matrix of {:d} by {:d}, interleave {:d}".format(vardef.size[0], vardef.size[1], vardef.size[2])
            else:
                raise CodeError("matrix size must be 2 or 3 numbers")
            out("\v{:s} = {:s}\t; {:s}".format(vardef.name, to_hex(vardef.value.value), comment))   # type: ignore
        else:
            raise CodeError("invalid var type")
    out("; normal variables - initial values will be set by init code")
    if zeropage:
        # zeropage uses the zp_address we've allocated, instead of allocating memory here
        for vardef in vars_by_vartype.get(VarType.VAR, []):
            assert vardef.zp_address is not None
            if vardef.datatype.isstring():
                raise CodeError("cannot put strings in the zeropage", vardef.sourceref)
            if vardef.datatype.isarray():
                size_str = "size " + str(vardef.size)
            else:
                size_str = ""
            out("\v{:s} = {:s}\t; {:s} {:s}".format(vardef.name, to_hex(vardef.zp_address), vardef.datatype.name.lower(), size_str))
    else:
        # create definitions for the variables that takes up empty space and will be initialized at startup
        string_vars = []
        for vardef in vars_by_vartype.get(VarType.VAR, []):
            if vardef.datatype.isnumeric():
                assert vardef.size == [1]
                if vardef.datatype == DataType.BYTE:
                    out("{:s}\v.byte  ?".format(vardef.name))
                elif vardef.datatype == DataType.WORD:
                    out("{:s}\v.word  ?".format(vardef.name))
                elif vardef.datatype == DataType.FLOAT:
                    out("{:s}\v.fill  5\t\t; float".format(vardef.name))
                else:
                    raise CodeError("weird datatype")
            elif vardef.datatype in (DataType.BYTEARRAY, DataType.WORDARRAY):
                assert len(vardef.size) == 1
                if vardef.datatype == DataType.BYTEARRAY:
                    out("{:s}\v.fill  {:d}\t\t; bytearray".format(vardef.name, vardef.size[0]))
                elif vardef.datatype == DataType.WORDARRAY:
                    out("{:s}\v.fill  {:d}*2\t\t; wordarray".format(vardef.name, vardef.size[0]))
                else:
                    raise CodeError("invalid datatype", vardef.datatype)
            elif vardef.datatype == DataType.MATRIX:
                assert len(vardef.size) == 2
                out("{:s}\v.fill  {:d}\t\t; matrix {:d}*{:d} bytes"
                    .format(vardef.name, vardef.size[0] * vardef.size[1], vardef.size[0], vardef.size[1]))
            elif vardef.datatype.isstring():
                string_vars.append(vardef)
            else:
                raise CodeError("unknown variable type " + str(vardef.datatype))
        # string vars are considered to be a constant, and are not re-initialized.
    out("")


@no_type_check
def _generate_string_var(out: Callable, vardef: VarDef) -> None:
    if vardef.datatype == DataType.STRING:
        # 0-terminated string
        out("{:s}\n\v.null  {:s}".format(vardef.name, _format_string(str(vardef.value.value))))
    elif vardef.datatype == DataType.STRING_P:
        # pascal string
        out("{:s}\n\v.ptext  {:s}".format(vardef.name, _format_string(str(vardef.value.value))))
    elif vardef.datatype == DataType.STRING_S:
        # 0-terminated string in screencode encoding
        out(".enc  'screen'")
        out("{:s}\n\v.null  {:s}".format(vardef.name, _format_string(str(vardef.value.value), True)))
        out(".enc  'none'")
    elif vardef.datatype == DataType.STRING_PS:
        # 0-terminated pascal string in screencode encoding
        out(".enc  'screen'")
        out("{:s}n\v.ptext  {:s}".format(vardef.name, _format_string(str(vardef.value.value), True)))
        out(".enc  'none'")


def _format_string(value: str, screencodes: bool = False) -> str:
    if len(value) == 1 and screencodes:
        if value[0].isprintable() and ord(value[0]) < 128:
            return "'{:s}'".format(value[0])
        else:
            return str(ord(value[0]))
    result = '"'
    for char in value:
        if char in "{}":
            result += '", {:d}, "'.format(ord(char))
        elif char.isprintable() and ord(char) < 128:
            result += char
        else:
            if screencodes:
                result += '", {:d}, "'.format(ord(char))
            else:
                if char == '\f':
                    result += "{clear}"
                elif char == '\b':
                    result += "{delete}"
                elif char == '\n':
                    result += "{cr}"
                elif char == '\r':
                    result += "{down}"
                elif char == '\t':
                    result += "{tab}"
                else:
                    result += '", {:d}, "'.format(ord(char))
    return result + '"'


def _numeric_value_str(value: Any, as_hex: bool=False) -> str:
    if isinstance(value, LiteralValue):
        value = value.value
    if isinstance(value, bool):
        return "1" if value else "0"
    if type(value) is int:
        if as_hex:
            return to_hex(value)
        return str(value)
    if isinstance(value, (int, float)):
        if as_hex:
            raise TypeError("cannot output float as hex")
        return str(value)
    raise TypeError("no numeric representation possible", value)
