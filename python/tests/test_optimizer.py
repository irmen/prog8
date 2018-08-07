import pytest
from il65.plyparse import IncrDecr, AugAssignment, VarDef, SymbolName
from il65.optimize import optimize
from .test_parser import parse_source


def test_incrdecr_joins_nonfloat():
    src = """~ test {
        X ++
        X ++
        X += 10
        Y--
        Y--
        Y-=20
    }"""
    result = parse_source(src)
    testscope = result.scope.nodes[0].nodes[0]
    assert len(testscope.nodes) == 6
    assert isinstance(testscope.nodes[0], IncrDecr)
    assert testscope.nodes[0].howmuch == 1
    assert isinstance(testscope.nodes[1], IncrDecr)
    assert testscope.nodes[1].howmuch == 1
    assert isinstance(testscope.nodes[2], AugAssignment)
    assert testscope.nodes[2].right.value == 10
    assert isinstance(testscope.nodes[3], IncrDecr)
    assert testscope.nodes[3].howmuch == 1
    assert isinstance(testscope.nodes[4], IncrDecr)
    assert testscope.nodes[4].howmuch == 1
    assert isinstance(testscope.nodes[5], AugAssignment)
    assert testscope.nodes[5].right.value == 20
    # now optimize the incrdecrs (joins them)
    optimize(result)
    testscope = result.scope.nodes[0].nodes[0]
    assert len(testscope.nodes) == 2                    # @todo broken optimization right now
    assert isinstance(testscope.nodes[0], IncrDecr)
    assert testscope.nodes[0].operator == "++"
    assert testscope.nodes[0].howmuch == 12
    assert isinstance(testscope.nodes[1], IncrDecr)
    assert testscope.nodes[1].operator == "--"
    assert testscope.nodes[1].howmuch == 22


def test_incrdecr_joins_float():
    src = """~ test {
        var .float flt = 0
        flt ++
        flt ++
        flt += 10
        flt --
        flt --
        flt --
        flt -= 5 
    }"""
    result = parse_source(src)
    testscope = result.scope.nodes[0].nodes[0]
    assert len(testscope.nodes) == 8
    # now optimize the incrdecrs (joins them)
    optimize(result)
    testscope = result.scope.nodes[0].nodes[0]
    assert len(testscope.nodes) == 2
    assert isinstance(testscope.nodes[0], VarDef)
    assert isinstance(testscope.nodes[1], IncrDecr)
    assert testscope.nodes[1].operator == "++"
    assert testscope.nodes[1].howmuch == 4
    assert isinstance(testscope.nodes[1].target, SymbolName)
    assert testscope.nodes[1].target.name == "flt"


def test_large_incrdecr_to_augassign():
    src = """~ test {
        X ++
        X ++
        X += 255
        Y --
        Y --
        Y -= 255
    }"""
    result = parse_source(src)
    testscope = result.scope.nodes[0].nodes[0]
    assert len(testscope.nodes) == 6
    # now optimize; joins the incrdecrs then converts to augassign because values are too large.
    optimize(result)
    testscope = result.scope.nodes[0].nodes[0]
    assert len(testscope.nodes) == 2
    assert isinstance(testscope.nodes[0], AugAssignment)
    assert testscope.nodes[0].left.name == "X"
    assert testscope.nodes[0].operator == "+="
    assert testscope.nodes[0].right.value == 257
    assert isinstance(testscope.nodes[1], AugAssignment)
    assert testscope.nodes[1].left.name == "Y"
    assert testscope.nodes[1].operator == "-="
    assert testscope.nodes[1].right.value == 257
