from il65.symbols import DataType, STRING_DATATYPES
from il65.compiler import ParseError
from il65.plylexer import SourceRef


def test_datatypes():
    assert all(isinstance(s, DataType) for s in STRING_DATATYPES)


def test_sourceref():
    s = SourceRef("file", 99, 42)
    assert str(s) == "file:99:42"
    s = SourceRef("file", 99)
    assert str(s) == "file:99"


def test_parseerror():
    p = ParseError("message", "source code", SourceRef("filename", 99, 42))
    assert p.args == ("message", )
    assert str(p) == "filename:99:42 message"
