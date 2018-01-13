import pytest
from il65.compile import Zeropage, CompileError
from il65.plyparse import ZpOptions, VarDef
from il65.plylex import SourceRef
from il65.datatypes import DataType


def test_zp_names():
    sref = SourceRef("test", 1, 1)
    zp = Zeropage(ZpOptions.NOCLOBBER)
    with pytest.raises(AssertionError):
        zp.allocate(VarDef(name="", vartype="memory", datatype=DataType.BYTE, sourceref=sref))
    zp.allocate(VarDef(name="", vartype="var", datatype=DataType.BYTE, sourceref=sref))
    zp.allocate(VarDef(name="", vartype="var", datatype=DataType.BYTE, sourceref=sref))
    zp.allocate(VarDef(name="varname", vartype="var", datatype=DataType.BYTE, sourceref=sref))
    with pytest.raises(AssertionError):
        zp.allocate(VarDef(name="varname", vartype="var", datatype=DataType.BYTE, sourceref=sref))
    zp.allocate(VarDef(name="varname2", vartype="var", datatype=DataType.BYTE, sourceref=sref))


def test_zp_noclobber_allocation():
    sref = SourceRef("test", 1, 1)
    zp = Zeropage(ZpOptions.NOCLOBBER)
    assert zp.available() == 9
    with pytest.raises(CompileError):
        # in regular zp there aren't 5 sequential bytes free
        zp.allocate(VarDef(name="impossible", vartype="var", datatype=DataType.FLOAT, sourceref=sref))
    for i in range(zp.available()):
        loc = zp.allocate(VarDef(name="bvar"+str(i), vartype="var", datatype=DataType.BYTE, sourceref=sref))
        assert loc > 0
    assert zp.available() == 0
    with pytest.raises(CompileError):
        zp.allocate(VarDef(name="", vartype="var", datatype=DataType.BYTE, sourceref=sref))
    with pytest.raises(CompileError):
        zp.allocate(VarDef(name="", vartype="var", datatype=DataType.WORD, sourceref=sref))


def test_zp_clobber_allocation():
    sref = SourceRef("test", 1, 1)
    zp = Zeropage(ZpOptions.CLOBBER)
    assert zp.available() == 239
    loc = zp.allocate(VarDef(name="", vartype="var", datatype=DataType.FLOAT, sourceref=sref))
    assert loc > 3 and loc not in zp.free
    num, rest = divmod(zp.available(), 5)
    for _ in range(num-3):
        zp.allocate(VarDef(name="", vartype="var", datatype=DataType.FLOAT, sourceref=sref))
    assert zp.available() == 19
    with pytest.raises(CompileError):
        # can't allocate because no more sequential bytes, only fragmented
        zp.allocate(VarDef(name="", vartype="var", datatype=DataType.FLOAT, sourceref=sref))
    for _ in range(14):
        zp.allocate(VarDef(name="", vartype="var", datatype=DataType.BYTE, sourceref=sref))
    zp.allocate(VarDef(name="", vartype="var", datatype=DataType.WORD, sourceref=sref))
    zp.allocate(VarDef(name="", vartype="var", datatype=DataType.WORD, sourceref=sref))
    with pytest.raises(CompileError):
        zp.allocate(VarDef(name="", vartype="var", datatype=DataType.WORD, sourceref=sref))
    assert zp.available() == 1
    zp.allocate(VarDef(name="last", vartype="var", datatype=DataType.BYTE, sourceref=sref))
    with pytest.raises(CompileError):
        zp.allocate(VarDef(name="impossible", vartype="var", datatype=DataType.BYTE, sourceref=sref))


def test_zp_efficient_allocation():
    # free = [0x04, 0x05, 0x06, 0x2a, 0x52, 0xf7, 0xf8, 0xf9, 0xfa]
    sref = SourceRef("test", 1, 1)
    zp = Zeropage(ZpOptions.NOCLOBBER)
    assert zp.available() == 9
    assert 0x2a == zp.allocate(VarDef(name="", vartype="var", datatype=DataType.BYTE, sourceref=sref))
    assert 0x52 == zp.allocate(VarDef(name="", vartype="var", datatype=DataType.BYTE, sourceref=sref))
    assert 0x04 == zp.allocate(VarDef(name="", vartype="var", datatype=DataType.WORD, sourceref=sref))
    assert 0xf7 == zp.allocate(VarDef(name="", vartype="var", datatype=DataType.WORD, sourceref=sref))
    assert 0x06 == zp.allocate(VarDef(name="", vartype="var", datatype=DataType.BYTE, sourceref=sref))
    assert 0xf9 == zp.allocate(VarDef(name="", vartype="var", datatype=DataType.WORD, sourceref=sref))
    assert zp.available() == 0
