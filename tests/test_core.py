import pytest
from il65.datatypes import DataType, STRING_DATATYPES, char_to_bytevalue
from il65.plyparse import coerce_constant_value, LiteralValue, Scope, SymbolName, VarDef
from il65.compile import ParseError
from il65.plylex import SourceRef


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
