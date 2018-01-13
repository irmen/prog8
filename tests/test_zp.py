import pytest
from il65.compile import Zeropage, CompileError
from il65.plyparse import ZpOptions
from il65.datatypes import DataType


def test_zp_names():
    zp = Zeropage(ZpOptions.NOCLOBBER)
    zp.allocate("", DataType.BYTE)
    zp.allocate("", DataType.BYTE)
    zp.allocate("varname", DataType.BYTE)
    with pytest.raises(AssertionError):
        zp.allocate("varname", DataType.BYTE)
    zp.allocate("varname2", DataType.BYTE)


def test_zp_noclobber_allocation():
    zp = Zeropage(ZpOptions.NOCLOBBER)
    assert zp.available() == 9
    with pytest.raises(CompileError):
        zp.allocate("impossible", DataType.FLOAT)     # in regular zp there aren't 5 sequential bytes free
    for i in range(zp.available()):
        loc = zp.allocate("bytevar"+str(i), DataType.BYTE)
        assert loc > 0
    assert zp.available() == 0
    with pytest.raises(CompileError):
        zp.allocate("", DataType.BYTE)
    with pytest.raises(CompileError):
        zp.allocate("", DataType.WORD)


def test_zp_clobber_allocation():
    zp = Zeropage(ZpOptions.CLOBBER)
    assert zp.available() == 239
    loc = zp.allocate("", DataType.FLOAT)
    assert loc > 3 and loc not in zp.free
    num, rest = divmod(zp.available(), 5)
    for _ in range(num-3):
        zp.allocate("", DataType.FLOAT)
    assert zp.available() == 19
    with pytest.raises(CompileError):
        zp.allocate("", DataType.FLOAT)     # can't allocate because no more sequential bytes, only fragmented
    for _ in range(14):
        zp.allocate("", DataType.BYTE)
    zp.allocate("", DataType.WORD)
    zp.allocate("", DataType.WORD)
    with pytest.raises(CompileError):
        zp.allocate("", DataType.WORD)
    assert zp.available() == 1
    zp.allocate("last", DataType.BYTE)
    with pytest.raises(CompileError):
        zp.allocate("impossible", DataType.BYTE)


def test_zp_efficient_allocation():
    # free = [0x04, 0x05, 0x06, 0x2a, 0x52, 0xf7, 0xf8, 0xf9, 0xfa]
    zp = Zeropage(ZpOptions.NOCLOBBER)
    assert zp.available() == 9
    assert 0x2a == zp.allocate("", DataType.BYTE)
    assert 0x52 == zp.allocate("", DataType.BYTE)
    assert 0x04 == zp.allocate("", DataType.WORD)
    assert 0xf7 == zp.allocate("", DataType.WORD)
    assert 0x06 == zp.allocate("", DataType.BYTE)
    assert 0xf9 == zp.allocate("", DataType.WORD)
    assert zp.available() == 0
