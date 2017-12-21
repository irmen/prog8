import pytest
from il65 import il65, symbols


def test_float_to_mflpt5():
    mflpt = il65.CodeGenerator.to_mflpt5(1.0)
    assert type(mflpt) is bytearray
    assert b"\x00\x00\x00\x00\x00" == il65.CodeGenerator.to_mflpt5(0)
    assert b"\x82\x49\x0F\xDA\xA1" == il65.CodeGenerator.to_mflpt5(3.141592653)
    assert b"\x82\x49\x0F\xDA\xA2" == il65.CodeGenerator.to_mflpt5(3.141592653589793)
    assert b"\x90\x80\x00\x00\x00" == il65.CodeGenerator.to_mflpt5(-32768)
    assert b"\x81\x00\x00\x00\x00" == il65.CodeGenerator.to_mflpt5(1)
    assert b"\x80\x35\x04\xF3\x34" == il65.CodeGenerator.to_mflpt5(0.7071067812)
    assert b"\x80\x35\x04\xF3\x33" == il65.CodeGenerator.to_mflpt5(0.7071067811865476)
    assert b"\x81\x35\x04\xF3\x34" == il65.CodeGenerator.to_mflpt5(1.4142135624)
    assert b"\x81\x35\x04\xF3\x33" == il65.CodeGenerator.to_mflpt5(1.4142135623730951)
    assert b"\x80\x80\x00\x00\x00" == il65.CodeGenerator.to_mflpt5(-.5)
    assert b"\x80\x31\x72\x17\xF8" == il65.CodeGenerator.to_mflpt5(0.69314718061)
    assert b"\x80\x31\x72\x17\xF7" == il65.CodeGenerator.to_mflpt5(0.6931471805599453)
    assert b"\x84\x20\x00\x00\x00" == il65.CodeGenerator.to_mflpt5(10)
    assert b"\x9E\x6E\x6B\x28\x00" == il65.CodeGenerator.to_mflpt5(1000000000)
    assert b"\x80\x00\x00\x00\x00" == il65.CodeGenerator.to_mflpt5(.5)
    assert b"\x81\x38\xAA\x3B\x29" == il65.CodeGenerator.to_mflpt5(1.4426950408889634)
    assert b"\x81\x49\x0F\xDA\xA2" == il65.CodeGenerator.to_mflpt5(1.5707963267948966)
    assert b"\x83\x49\x0F\xDA\xA2" == il65.CodeGenerator.to_mflpt5(6.283185307179586)
    assert b"\x7F\x00\x00\x00\x00" == il65.CodeGenerator.to_mflpt5(.25)


def test_float_range():
    assert b"\xff\x7f\xff\xff\xff" == il65.CodeGenerator.to_mflpt5(symbols.FLOAT_MAX_POSITIVE)
    assert b"\xff\xff\xff\xff\xff" == il65.CodeGenerator.to_mflpt5(symbols.FLOAT_MAX_NEGATIVE)
    with pytest.raises(OverflowError):
        il65.CodeGenerator.to_mflpt5(1.7014118346e+38)
    with pytest.raises(OverflowError):
        il65.CodeGenerator.to_mflpt5(-1.7014118346e+38)
    with pytest.raises(OverflowError):
        il65.CodeGenerator.to_mflpt5(1.7014118347e+38)
    with pytest.raises(OverflowError):
        il65.CodeGenerator.to_mflpt5(-1.7014118347e+38)
    assert b"\x03\x39\x1d\x15\x63" == il65.CodeGenerator.to_mflpt5(1.7e-38)
    assert b"\x00\x00\x00\x00\x00" == il65.CodeGenerator.to_mflpt5(1.7e-39)
    assert b"\x03\xb9\x1d\x15\x63" == il65.CodeGenerator.to_mflpt5(-1.7e-38)
    assert b"\x00\x00\x00\x00\x00" == il65.CodeGenerator.to_mflpt5(-1.7e-39)


