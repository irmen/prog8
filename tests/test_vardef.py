import pytest
from il65.datatypes import DataType
from il65.plyparse import LiteralValue, VarDef, VarType, DatatypeNode, ExpressionWithOperator, Scope, AddressOf, SymbolName, UndefinedSymbolError
from il65.plylex import SourceRef

# zero or one subnode: value (an Expression, LiteralValue, AddressOf or SymbolName.).
# name = attr.ib(type=str)
# vartype = attr.ib()
# datatype = attr.ib()
# size = attr.ib(type=list, default=None)
# zp_address = attr.ib(type=int, default=None, init=False)  # the address in the zero page if this var is there, will be set later


def test_creation():
    sref = SourceRef("test", 1, 1)
    v = VarDef(name="v1", vartype="const", datatype=None, sourceref=sref)
    assert v.name == "v1"
    assert v.vartype == VarType.CONST
    assert v.datatype == DataType.BYTE
    assert v.size == [1]
    assert v.value is None
    assert v.zp_address is None
    v = VarDef(name="v2", vartype="memory", datatype=None, sourceref=sref)
    assert v.vartype == VarType.MEMORY
    assert isinstance(v.value, LiteralValue)
    assert v.value.value == 0
    dt = DatatypeNode(name="float", sourceref=sref)
    v = VarDef(name="v2", vartype="var", datatype=dt, sourceref=sref)
    assert v.vartype == VarType.VAR
    assert v.datatype == DataType.FLOAT
    assert isinstance(v.value, LiteralValue)
    assert v.value.value == 0
    dt = DatatypeNode(name="matrix", sourceref=sref)
    with pytest.raises(ValueError):
        VarDef(name="v2", vartype="var", datatype=dt, sourceref=sref)
    dt.dimensions = [2, 3]
    v = VarDef(name="v2", vartype="var", datatype=dt, sourceref=sref)
    assert v.vartype == VarType.VAR
    assert v.datatype == DataType.MATRIX
    assert v.size == [2, 3]
    assert isinstance(v.value, LiteralValue)
    assert v.value.value == 0
    dt = DatatypeNode(name="text", sourceref=sref)
    v = VarDef(name="v2", vartype="var", datatype=dt, sourceref=sref)
    assert v.vartype == VarType.VAR
    assert v.datatype == DataType.STRING
    assert v.size == [1]
    assert v.value is None


def test_set_value():
    sref = SourceRef("test", 1, 1)
    v = VarDef(name="v1", vartype="var", datatype=DatatypeNode(name="word", sourceref=sref), sourceref=sref)
    assert v.datatype == DataType.WORD
    assert v.value.value == 0
    v.value = LiteralValue(value=42, sourceref=sref)
    assert v.value.value == 42
    v = VarDef(name="v1", vartype="var", datatype=DatatypeNode(name="text", sourceref=sref), sourceref=sref)
    assert v.datatype == DataType.STRING
    assert v.value is None
    v.value = LiteralValue(value="hello", sourceref=sref)
    assert v.value.value == "hello"
    e = ExpressionWithOperator(operator="-", sourceref=sref)
    e.left = LiteralValue(value=42, sourceref=sref)
    assert not e.must_be_constant
    v.value = e
    assert v.value is e
    assert e.must_be_constant


def test_const_value():
    sref = SourceRef("test", 1, 1)
    scope = Scope(nodes=[], level="block", sourceref=sref)
    vardef = VarDef(name="constvar", vartype="const", datatype=None, sourceref=sref)
    vardef.value = LiteralValue(value=43, sourceref=sref)
    scope.add_node(vardef)
    vardef = VarDef(name="varvar", vartype="var", datatype=None, sourceref=sref)
    vardef.value = LiteralValue(value=44, sourceref=sref)
    scope.add_node(vardef)
    vardef = VarDef(name="memvar", vartype="memory", datatype=None, sourceref=sref)
    vardef.value = LiteralValue(value=45, sourceref=sref)
    scope.add_node(vardef)
    v = VarDef(name="v1", vartype="var", datatype=DatatypeNode(name="word", sourceref=sref), sourceref=sref)
    with pytest.raises(TypeError):
        v.const_value()
    v = VarDef(name="v1", vartype="memory", datatype=DatatypeNode(name="word", sourceref=sref), sourceref=sref)
    with pytest.raises(TypeError):
        v.const_value()
    v = VarDef(name="v1", vartype="const", datatype=DatatypeNode(name="word", sourceref=sref), sourceref=sref)
    with pytest.raises(ValueError):
        v.const_value()
    v.value = LiteralValue(value=42, sourceref=sref)
    assert v.const_value() == 42
    v = VarDef(name="v1", vartype="const", datatype=DatatypeNode(name="float", sourceref=sref), sourceref=sref)
    with pytest.raises(ValueError):
        v.const_value()
    v.value = LiteralValue(value=42.9988, sourceref=sref)
    assert v.const_value() == 42.9988
    e = ExpressionWithOperator(operator="-", sourceref=sref)
    e.left = LiteralValue(value=42, sourceref=sref)
    v.value = e
    assert v.const_value() == -42
    s = SymbolName(name="unexisting", sourceref=sref)
    s.parent = scope
    v.value = s
    with pytest.raises(UndefinedSymbolError):
        v.const_value()
    s = SymbolName(name="constvar", sourceref=sref)
    s.parent = scope
    v.value = s
    assert v.const_value() == 43
    a = AddressOf(name="varvar", sourceref=sref)
    a.parent = scope
    v.value = a
    with pytest.raises(TypeError):
        v.const_value()
    a = AddressOf(name="memvar", sourceref=sref)
    a.parent = scope
    v.value = a
    assert v.const_value() == 45
