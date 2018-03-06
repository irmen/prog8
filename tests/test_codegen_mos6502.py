import pytest
from il65.datatypes import FLOAT_MAX_NEGATIVE, FLOAT_MAX_POSITIVE
from il65.codegen.shared import to_hex, to_mflpt5


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
