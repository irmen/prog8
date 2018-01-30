import pytest
from il65.plylex import lexer, tokens, find_tok_column, literals, reserved, SourceRef
from il65.plyparse import parser, connect_parents, TokenFilter, Module, Subroutine, Block, IncrDecr, Scope, \
    VarDef, Register, ExpressionWithOperator, LiteralValue, Label, SubCall, Dereference
from il65.datatypes import DataType


def lexer_error(sourceref: SourceRef, fmtstring: str, *args: str) -> None:
    print("ERROR: {}: {}".format(sourceref, fmtstring.format(*args)))


lexer.error_function = lexer_error


def test_lexer_definitions():
    assert "ENDL" in tokens
    assert "GOTO" in tokens
    assert '+' in literals
    assert ';' not in literals
    assert "return" in reserved
    assert "sub" in reserved
    assert "A" in reserved
    assert "if_cc" in reserved


test_source_1 = """ %output prg, sys

; c1

; c2


~ block $c000 {
         %import a,b


    ; comment

    var foo = 42+true
    var .matrix(20,30) m = 9.234556
    ;comment2


    sub calculate () -> () {
        return 
    }
    
    ;z
    
}
"""


def test_lexer():
    lexer.input(test_source_1)
    lexer.lineno = 1
    tokens = list(iter(lexer))
    token_types = list(t.type for t in tokens)
    assert token_types == ['DIRECTIVE', 'NAME', ',', 'NAME', 'ENDL', 'ENDL', 'ENDL',
                           'BITINVERT', 'NAME', 'INTEGER', '{', 'ENDL',
                           'DIRECTIVE', 'NAME', ',', 'NAME', 'ENDL', 'ENDL',
                           'VARTYPE', 'NAME', 'IS', 'INTEGER', '+', 'BOOLEAN', 'ENDL',
                           'VARTYPE', 'DATATYPE', '(', 'INTEGER', ',', 'INTEGER', ')', 'NAME', 'IS', 'FLOATINGPOINT', 'ENDL', 'ENDL',
                           'SUB', 'NAME', '(', ')', 'RARROW', '(', ')', '{', 'ENDL', 'RETURN', 'ENDL', '}', 'ENDL', 'ENDL', 'ENDL', 'ENDL',
                           '}', 'ENDL']
    directive_token = tokens[12]
    assert directive_token.type == "DIRECTIVE"
    assert directive_token.value == "import"
    assert directive_token.lineno == 9
    assert directive_token.lexpos == lexer.lexdata.index("%import")
    assert find_tok_column(directive_token) == 10
    bool_token = tokens[23]
    assert bool_token.type == "BOOLEAN"
    assert type(bool_token.value) is bool
    assert bool_token.value == True


def test_lexer_strings():
    lexer.input(r"'hello\tbye\n\n' '\n'")
    lexer.lineno = 1
    tokens = list(iter(lexer))
    assert len(tokens) == 2
    st = tokens[0]
    assert st.type == "STRING"
    assert st.value == "hello\tbye\n\n"
    lexer.input(r"'hello\tbye\n\n'")
    st = tokens[1]
    assert st.type == "CHARACTER"
    assert st.value == '\n'


def test_tokenfilter():
    lexer.input(test_source_1)
    lexer.lineno = 1
    filter = TokenFilter(lexer)
    tokens = []
    while True:
        token = filter.token()
        if not token:
            break
        tokens.append(token)
    token_types = list(t.type for t in tokens)
    assert token_types == ['DIRECTIVE', 'NAME', ',', 'NAME', 'ENDL',
                           'BITINVERT', 'NAME', 'INTEGER', '{', 'ENDL',
                           'DIRECTIVE', 'NAME', ',', 'NAME', 'ENDL',
                           'VARTYPE', 'NAME', 'IS', 'INTEGER', '+', 'BOOLEAN', 'ENDL',
                           'VARTYPE', 'DATATYPE', '(', 'INTEGER', ',', 'INTEGER', ')', 'NAME', 'IS', 'FLOATINGPOINT', 'ENDL',
                           'SUB', 'NAME', '(', ')', 'RARROW', '(', ')', '{', 'ENDL', 'RETURN', 'ENDL', '}', 'ENDL',
                           '}', 'ENDL']


def test_parser():
    lexer.lineno = 1
    lexer.source_filename = "sourcefile"
    filter = TokenFilter(lexer)
    result = parser.parse(input=test_source_1, tokenfunc=filter.token)
    connect_parents(result, None)
    assert isinstance(result, Module)
    assert result.name == "sourcefile"
    assert result.scope.name == "<sourcefile global scope>"
    assert result.subroutine_usage == {}
    assert result.scope.parent_scope is None
    sub = result.scope.lookup("block.calculate")
    assert isinstance(sub, Subroutine)
    assert sub.name == "calculate"
    block = result.scope.lookup("block")
    assert isinstance(block, Block)
    assert block.name == "block"
    bool_vdef = block.scope.nodes[1]
    assert isinstance(bool_vdef, VarDef)
    assert isinstance(bool_vdef.value, ExpressionWithOperator)
    assert isinstance(bool_vdef.value.right, LiteralValue)
    assert isinstance(bool_vdef.value.right.value, int)
    assert bool_vdef.value.right.value == 1
    assert block.address == 49152
    sub2 = block.scope.lookup("calculate")
    assert sub2 is sub
    assert sub2.lineref == "src l. 19"
    all_nodes = list(result.all_nodes())
    assert len(all_nodes) == 14
    all_nodes = list(result.all_nodes(Subroutine))
    assert len(all_nodes) == 1
    assert isinstance(all_nodes[0], Subroutine)
    assert isinstance(all_nodes[0].parent, Scope)
    assert all_nodes[0] in all_nodes[0].parent.nodes
    assert all_nodes[0].lineref == "src l. 19"
    assert all_nodes[0].parent.lineref == "src l. 8"


def test_block_nodes():
    sref = SourceRef("file", 1, 1)
    sub1 = Subroutine(name="subaddr", param_spec=[], result_spec=[], address=0xc000, sourceref=sref)
    sub2 = Subroutine(name="subblock", param_spec=[], result_spec=[], sourceref=sref)
    sub2.scope = Scope(nodes=[Label(name="start", sourceref=sref)], level="block", sourceref=sref)
    assert sub1.scope is None
    assert sub1.nodes == []
    assert sub2.scope is not None
    assert len(sub2.scope.nodes) > 0


test_source_2 = """
~ {
    999(1,2)
    [zz]()
}
"""


def test_parser_2():
    lexer.lineno = 1
    lexer.source_filename = "sourcefile"
    filter = TokenFilter(lexer)
    result = parser.parse(input=test_source_2, tokenfunc=filter.token)
    connect_parents(result, None)
    block = result.scope.nodes[0]
    call = block.scope.nodes[0]
    assert isinstance(call, SubCall)
    assert len(call.arguments.nodes) == 2
    assert isinstance(call.target, LiteralValue)
    assert call.target.value == 999
    call = block.scope.nodes[1]
    assert isinstance(call, SubCall)
    assert len(call.arguments.nodes) == 0
    assert isinstance(call.target, Dereference)
    assert call.target.operand.name == "zz"


test_source_3 = """
~ {
    [$c000.word] = 5
    [$c000 .byte] = 5
    [AX .word] = 5
    [AX .float] = 5
}
"""


def test_typespec():
    lexer.lineno = 1
    lexer.source_filename = "sourcefile"
    filter = TokenFilter(lexer)
    result = parser.parse(input=test_source_3, tokenfunc=filter.token)
    connect_parents(result, None)
    block = result.scope.nodes[0]
    assignment1, assignment2, assignment3, assignment4 = block.scope.nodes
    assert assignment1.right.value == 5
    assert assignment2.right.value == 5
    assert assignment3.right.value == 5
    assert assignment4.right.value == 5
    assert len(assignment1.left.nodes) == 1
    assert len(assignment2.left.nodes) == 1
    assert len(assignment3.left.nodes) == 1
    assert len(assignment4.left.nodes) == 1
    t1 = assignment1.left.nodes[0]
    t2 = assignment2.left.nodes[0]
    t3 = assignment3.left.nodes[0]
    t4 = assignment4.left.nodes[0]
    assert isinstance(t1, Dereference)
    assert isinstance(t2, Dereference)
    assert isinstance(t3, Dereference)
    assert isinstance(t4, Dereference)
    assert isinstance(t1.operand, LiteralValue)
    assert isinstance(t2.operand, LiteralValue)
    assert isinstance(t3.operand, Register)
    assert isinstance(t4.operand, Register)
    assert t1.operand.value == 0xc000
    assert t2.operand.value == 0xc000
    assert t3.operand.name == "AX"
    assert t4.operand.name == "AX"
    assert t1.datatype == DataType.WORD
    assert t2.datatype == DataType.BYTE
    assert t3.datatype == DataType.WORD
    assert t4.datatype == DataType.FLOAT
    assert t1.size is None
    assert t2.size is None
    assert t3.size is None
    assert t4.size is None


test_source_4 = """
~ {
    var x1 = '@'
    var x2 = 'π'
    var x3 = 'abc'
    A = '@'
    A = 'π'
    A = 'abc'
}
"""


def test_char_string():
    lexer.lineno = 1
    lexer.source_filename = "sourcefile"
    filter = TokenFilter(lexer)
    result = parser.parse(input=test_source_4, tokenfunc=filter.token)
    connect_parents(result, None)
    block = result.scope.nodes[0]
    var1, var2, var3, assgn1, assgn2, assgn3, = block.scope.nodes
    assert var1.value.value == 64
    assert var2.value.value == 126
    assert var3.value.value == "abc"
    assert assgn1.right.value == 64
    assert assgn2.right.value == 126
    assert assgn3.right.value == "abc"



test_source_5 = """
~ {
    var x1 = true
    var x2 = false
    A = true
    A = false
}
"""


def test_boolean_int():
    lexer.lineno = 1
    lexer.source_filename = "sourcefile"
    filter = TokenFilter(lexer)
    result = parser.parse(input=test_source_5, tokenfunc=filter.token)
    connect_parents(result, None)
    block = result.scope.nodes[0]
    var1, var2, assgn1, assgn2, = block.scope.nodes
    assert type(var1.value.value) is int and var1.value.value == 1
    assert type(var2.value.value) is int and var2.value.value == 0
    assert type(assgn1.right.value) is int and assgn1.right.value == 1
    assert type(assgn2.right.value) is int and assgn2.right.value == 0


def test_incrdecr():
    sref = SourceRef("test", 1, 1)
    with pytest.raises(ValueError):
        IncrDecr(operator="??", sourceref=sref)
    i = IncrDecr(operator="++", sourceref=sref)
    assert i.howmuch == 1
