import pytest
from il65.symbols import Zeropage, SymbolError, DataType


def test_zp_configure_onlyonce():
    zp = Zeropage()
    zp.configure()
    with pytest.raises(SymbolError):
        zp.configure()


def test_zp_names():
    zp = Zeropage()
    zp.configure()
    zp.allocate("", DataType.BYTE)
    zp.allocate("", DataType.BYTE)
    zp.allocate("varname", DataType.BYTE)
    with pytest.raises(AssertionError):
        zp.allocate("varname", DataType.BYTE)
    zp.allocate("varname2", DataType.BYTE)


def test_zp_noclobber_allocation():
    zp = Zeropage()
    zp.configure(False)
    assert zp.available() == 9
    with pytest.raises(LookupError):
        zp.allocate("impossible", DataType.FLOAT)     # in regular zp there aren't 5 sequential bytes free
    for i in range(zp.available()):
        zp.allocate("bytevar"+str(i), DataType.BYTE)
    assert zp.available() == 0
    with pytest.raises(LookupError):
        zp.allocate("", DataType.BYTE)
    with pytest.raises(LookupError):
        zp.allocate("", DataType.WORD)


def test_zp_clobber_allocation():
    zp = Zeropage()
    zp.configure(True)
    assert zp.available() == 248
    loc = zp.allocate("", DataType.FLOAT)
    assert loc > 3 and loc not in zp.free
    num, rest = divmod(zp.available(), 5)
    for _ in range(num):
        zp.allocate("", DataType.FLOAT)
    assert zp.available() == rest
    for _ in range(rest // 2):
        zp.allocate("", DataType.WORD)
    assert zp.available() == 1
    zp.allocate("last", DataType.BYTE)
    with pytest.raises(LookupError):
        zp.allocate("impossible", DataType.BYTE)
