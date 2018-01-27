import pytest
from il65.datatypes import DataType, VarType, STRING_DATATYPES, FLOAT_MAX_POSITIVE, FLOAT_MAX_NEGATIVE, char_to_bytevalue
from il65.plyparse import coerce_constant_value, LiteralValue, Scope, AddressOf, SymbolName, VarDef
from il65.compile import ParseError
from il65.plylex import SourceRef
from il65.emit import to_hex, to_mflpt5


def test_datatypes():
    assert all(isinstance(s, DataType) for s in STRING_DATATYPES)
    assert all(s.isstring() for s in STRING_DATATYPES)
    assert not any(s.isarray() or s.isnumeric() for s in STRING_DATATYPES)
    assert DataType.WORDARRAY.isarray()
    assert not DataType.WORDARRAY.isnumeric()
    assert not DataType.WORDARRAY.isstring()
    assert not DataType.WORD.isarray()
    assert DataType.WORD.isnumeric()
    assert not DataType.WORD.isstring()


def test_sourceref():
    s = SourceRef("file", 99, 42)
    assert str(s) == "file:99:42"
    s = SourceRef("file", 99)
    assert str(s) == "file:99"


def test_parseerror():
    p = ParseError("message", SourceRef("filename", 99, 42))
    assert p.args == ("message", )
    assert str(p) == "filename:99:42 message"


def test_to_hex():
    assert to_hex(0) == "0"
    assert to_hex(1) == "1"
    assert to_hex(10) == "10"
    assert to_hex(15) == "15"
    assert to_hex(16) == "$10"
    assert to_hex(255) == "$ff"
    assert to_hex(256) == "$0100"
    assert to_hex(20060) == "$4e5c"
    assert to_hex(65535) == "$ffff"
    with pytest.raises(OverflowError):
        to_hex(-1)
    with pytest.raises(OverflowError):
        to_hex(65536)


def test_float_to_mflpt5():
    mflpt = to_mflpt5(1.0)
    assert type(mflpt) is bytearray
    assert b"\x00\x00\x00\x00\x00" == to_mflpt5(0)
    assert b"\x82\x49\x0F\xDA\xA1" == to_mflpt5(3.141592653)
    assert b"\x82\x49\x0F\xDA\xA2" == to_mflpt5(3.141592653589793)
    assert b"\x90\x80\x00\x00\x00" == to_mflpt5(-32768)
    assert b"\x81\x00\x00\x00\x00" == to_mflpt5(1)
    assert b"\x80\x35\x04\xF3\x34" == to_mflpt5(0.7071067812)
    assert b"\x80\x35\x04\xF3\x33" == to_mflpt5(0.7071067811865476)
    assert b"\x81\x35\x04\xF3\x34" == to_mflpt5(1.4142135624)
    assert b"\x81\x35\x04\xF3\x33" == to_mflpt5(1.4142135623730951)
    assert b"\x80\x80\x00\x00\x00" == to_mflpt5(-.5)
    assert b"\x80\x31\x72\x17\xF8" == to_mflpt5(0.69314718061)
    assert b"\x80\x31\x72\x17\xF7" == to_mflpt5(0.6931471805599453)
    assert b"\x84\x20\x00\x00\x00" == to_mflpt5(10)
    assert b"\x9E\x6E\x6B\x28\x00" == to_mflpt5(1000000000)
    assert b"\x80\x00\x00\x00\x00" == to_mflpt5(.5)
    assert b"\x81\x38\xAA\x3B\x29" == to_mflpt5(1.4426950408889634)
    assert b"\x81\x49\x0F\xDA\xA2" == to_mflpt5(1.5707963267948966)
    assert b"\x83\x49\x0F\xDA\xA2" == to_mflpt5(6.283185307179586)
    assert b"\x7F\x00\x00\x00\x00" == to_mflpt5(.25)


def test_float_range():
    assert b"\xff\x7f\xff\xff\xff" == to_mflpt5(FLOAT_MAX_POSITIVE)
    assert b"\xff\xff\xff\xff\xff" == to_mflpt5(FLOAT_MAX_NEGATIVE)
    with pytest.raises(OverflowError):
        to_mflpt5(1.7014118346e+38)
    with pytest.raises(OverflowError):
        to_mflpt5(-1.7014118346e+38)
    with pytest.raises(OverflowError):
        to_mflpt5(1.7014118347e+38)
    with pytest.raises(OverflowError):
        to_mflpt5(-1.7014118347e+38)
    assert b"\x03\x39\x1d\x15\x63" == to_mflpt5(1.7e-38)
    assert b"\x00\x00\x00\x00\x00" == to_mflpt5(1.7e-39)
    assert b"\x03\xb9\x1d\x15\x63" == to_mflpt5(-1.7e-38)
    assert b"\x00\x00\x00\x00\x00" == to_mflpt5(-1.7e-39)


def test_char_to_bytevalue():
    assert char_to_bytevalue('a') == 65
    assert char_to_bytevalue('\n') == 13
    assert char_to_bytevalue('π') == 126
    assert char_to_bytevalue('▒') == 230
    assert char_to_bytevalue('\x00') == 0
    assert char_to_bytevalue('\xff') == 255
    with pytest.raises(AssertionError):
        char_to_bytevalue('<undefined>')
    # screencodes not yet implemented: assert datatypes.char_to_bytevalue('a', False) == 65


def test_coerce_value_novars():
    sref = SourceRef("test", 1, 1)
    def lv(v) -> LiteralValue:
        return LiteralValue(value=v, sourceref=sref)     # type: ignore
    assert coerce_constant_value(DataType.BYTE, lv(0)) == (False, lv(0))
    assert coerce_constant_value(DataType.BYTE, lv(255)) == (False, lv(255))
    assert coerce_constant_value(DataType.BYTE, lv('@')) == (True, lv(64))
    assert coerce_constant_value(DataType.WORD, lv(0)) == (False, lv(0))
    assert coerce_constant_value(DataType.WORD, lv(65535)) == (False, lv(65535))
    assert coerce_constant_value(DataType.WORD, lv('@')) == (True, lv(64))
    assert coerce_constant_value(DataType.FLOAT, lv(-999.22)) == (False, lv(-999.22))
    assert coerce_constant_value(DataType.FLOAT, lv(123.45)) == (False, lv(123.45))
    assert coerce_constant_value(DataType.FLOAT, lv('@')) == (True, lv(64))
    assert coerce_constant_value(DataType.BYTE, lv(5.678)) == (True, lv(5))
    assert coerce_constant_value(DataType.WORD, lv(5.678)) == (True, lv(5))
    assert coerce_constant_value(DataType.WORD,
                                 lv("string")) == (False, lv("string")),  "string (address) can be assigned to a word"
    assert coerce_constant_value(DataType.STRING, lv("string")) == (False, lv("string"))
    assert coerce_constant_value(DataType.STRING_P, lv("string")) == (False, lv("string"))
    assert coerce_constant_value(DataType.STRING_S, lv("string")) == (False, lv("string"))
    assert coerce_constant_value(DataType.STRING_PS, lv("string")) == (False, lv("string"))
    with pytest.raises(OverflowError):
        coerce_constant_value(DataType.BYTE, lv(-1))
    with pytest.raises(OverflowError):
        coerce_constant_value(DataType.BYTE, lv(256))
    with pytest.raises(OverflowError):
        coerce_constant_value(DataType.BYTE, lv(256.12345))
    with pytest.raises(OverflowError):
        coerce_constant_value(DataType.WORD, lv(-1))
    with pytest.raises(OverflowError):
        coerce_constant_value(DataType.WORD, lv(65536))
    with pytest.raises(OverflowError):
        coerce_constant_value(DataType.WORD, lv(65536.12345))
    with pytest.raises(OverflowError):
        coerce_constant_value(DataType.FLOAT, lv(-1.7014118346e+38))
    with pytest.raises(OverflowError):
        coerce_constant_value(DataType.FLOAT, lv(1.7014118347e+38))
    with pytest.raises(TypeError):
        coerce_constant_value(DataType.BYTE, lv("string"))
    with pytest.raises(TypeError):
        coerce_constant_value(DataType.FLOAT, lv("string"))


def test_coerce_value_vars():
    sref = SourceRef("test", 1, 1)
    scope = Scope(nodes=[], level="block", sourceref=sref)
    vardef = VarDef(name="constantvar", vartype="const", datatype=None, sourceref=sref)
    vardef.value = LiteralValue(value=99, sourceref=sref)
    scope.add_node(vardef)
    vardef = VarDef(name="varvar", vartype="var", datatype=None, sourceref=sref)
    vardef.value = LiteralValue(value=42, sourceref=sref)
    scope.add_node(vardef)
    vardef = VarDef(name="memvar", vartype="memory", datatype=None, sourceref=sref)
    vardef.value = LiteralValue(value=0xc000, sourceref=sref)
    scope.add_node(vardef)
    value = SymbolName(name="constantvar", sourceref=sref)
    value.parent = scope
    assert coerce_constant_value(DataType.BYTE, value) == (True, LiteralValue(value=99, sourceref=sref))
