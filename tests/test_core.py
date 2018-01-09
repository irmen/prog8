import pytest
from il65.symbols import DataType, STRING_DATATYPES, to_hex
from il65.compile import ParseError
from il65.plylex import SourceRef


def test_datatypes():
    assert all(isinstance(s, DataType) for s in STRING_DATATYPES)


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
    assert to_hex(0) == "$00"
    assert to_hex(1) == "$01"
    assert to_hex(255) == "$ff"
    assert to_hex(256) == "$0100"
    assert to_hex(20060) == "$4e5c"
    assert to_hex(65535) == "$ffff"
    with pytest.raises(OverflowError):
        to_hex(-1)
    with pytest.raises(OverflowError):
        to_hex(65536)
