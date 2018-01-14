import pytest
from il65 import datatypes
from il65.compile import ParseError
from il65.plylex import SourceRef
from il65.emit import to_hex, to_mflpt5


def test_datatypes():
    assert all(isinstance(s, datatypes.DataType) for s in datatypes.STRING_DATATYPES)
    assert all(s.isstring() for s in datatypes.STRING_DATATYPES)
    assert not any(s.isarray() or s.isnumeric() for s in datatypes.STRING_DATATYPES)
    assert datatypes.DataType.WORDARRAY.isarray()
    assert not datatypes.DataType.WORDARRAY.isnumeric()
    assert not datatypes.DataType.WORDARRAY.isstring()
    assert not datatypes.DataType.WORD.isarray()
    assert datatypes.DataType.WORD.isnumeric()
    assert not datatypes.DataType.WORD.isstring()


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
    assert b"\xff\x7f\xff\xff\xff" == to_mflpt5(datatypes.FLOAT_MAX_POSITIVE)
    assert b"\xff\xff\xff\xff\xff" == to_mflpt5(datatypes.FLOAT_MAX_NEGATIVE)
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
    assert datatypes.char_to_bytevalue('a') == 65
    assert datatypes.char_to_bytevalue('\n') == 13
    assert datatypes.char_to_bytevalue('π') == 126
    assert datatypes.char_to_bytevalue('▒') == 230
    assert datatypes.char_to_bytevalue('\x00') == 0
    assert datatypes.char_to_bytevalue('\xff') == 255
    with pytest.raises(AssertionError):
        datatypes.char_to_bytevalue('<undefined>')
    # screencodes not yet implemented: assert datatypes.char_to_bytevalue('a', False) == 65


def test_coerce_value():
    assert datatypes.coerce_value(datatypes.DataType.BYTE, 0) == (False, 0)
    assert datatypes.coerce_value(datatypes.DataType.BYTE, 255) == (False, 255)
    assert datatypes.coerce_value(datatypes.DataType.WORD, 0) == (False, 0)
    assert datatypes.coerce_value(datatypes.DataType.WORD, 65535) == (False, 65535)
    assert datatypes.coerce_value(datatypes.DataType.FLOAT, -999.22) == (False, -999.22)
    assert datatypes.coerce_value(datatypes.DataType.FLOAT, 123.45) == (False, 123.45)
    assert datatypes.coerce_value(datatypes.DataType.BYTE, 5.678) == (True, 5)
    assert datatypes.coerce_value(datatypes.DataType.WORD, 5.678) == (True, 5)
    assert datatypes.coerce_value(datatypes.DataType.STRING, "string") == (False, "string")
    assert datatypes.coerce_value(datatypes.DataType.STRING_P, "string") == (False, "string")
    assert datatypes.coerce_value(datatypes.DataType.STRING_S, "string") == (False, "string")
    assert datatypes.coerce_value(datatypes.DataType.STRING_PS, "string") == (False, "string")
    with pytest.raises(OverflowError):
        datatypes.coerce_value(datatypes.DataType.BYTE, -1)
    with pytest.raises(OverflowError):
        datatypes.coerce_value(datatypes.DataType.BYTE, 256)
    with pytest.raises(OverflowError):
        datatypes.coerce_value(datatypes.DataType.BYTE, 256.12345)
    with pytest.raises(OverflowError):
        datatypes.coerce_value(datatypes.DataType.WORD, -1)
    with pytest.raises(OverflowError):
        datatypes.coerce_value(datatypes.DataType.WORD, 65536)
    with pytest.raises(OverflowError):
        datatypes.coerce_value(datatypes.DataType.WORD, 65536.12345)
    with pytest.raises(OverflowError):
        datatypes.coerce_value(datatypes.DataType.FLOAT, -1.7014118346e+38)
    with pytest.raises(OverflowError):
        datatypes.coerce_value(datatypes.DataType.FLOAT, 1.7014118347e+38)
    with pytest.raises(TypeError):
        datatypes.coerce_value(datatypes.DataType.BYTE, "string")
    with pytest.raises(TypeError):
        datatypes.coerce_value(datatypes.DataType.WORD, "string")
    with pytest.raises(TypeError):
        datatypes.coerce_value(datatypes.DataType.FLOAT, "string")
