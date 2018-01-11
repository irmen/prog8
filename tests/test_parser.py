from il65.plylex import lexer, tokens, find_tok_column, literals, reserved, SourceRef
from il65.plyparse import parser, TokenFilter, Module, Subroutine, Block, Return, Scope, \
    VarDef, Expression, LiteralValue, Label, SubCall, CallTarget, SymbolName


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
    assert isinstance(result, Module)
    assert result.name == "sourcefile"
    assert result.scope.name == "<sourcefile global scope>"
    assert result.subroutine_usage == {}
    assert result.scope.parent_scope is None
    sub = result.scope["block.calculate"]
    assert isinstance(sub, Subroutine)
    assert sub.name == "calculate"
    block = result.scope["block"]
    assert isinstance(block, Block)
    assert block.name == "block"
    assert block.nodes is block.scope.nodes
    bool_vdef = block.scope.nodes[1]
    assert isinstance(bool_vdef, VarDef)
    assert isinstance(bool_vdef.value, Expression)
    assert isinstance(bool_vdef.value.right, LiteralValue)
    assert isinstance(bool_vdef.value.right.value, bool)
    assert bool_vdef.value.right.value == True
    assert block.address == 49152
    sub2 = block.scope["calculate"]
    assert sub2 is sub
    assert sub2.lineref == "src l. 19"
    all_scopes = list(result.all_scopes())
    assert len(all_scopes) == 3
    assert isinstance(all_scopes[0][0], Module)
    assert all_scopes[0][1] is None
    assert isinstance(all_scopes[1][0], Block)
    assert isinstance(all_scopes[1][1], Module)
    assert isinstance(all_scopes[2][0], Subroutine)
    assert isinstance(all_scopes[2][1], Block)
    stmt = list(all_scopes[2][0].scope.filter_nodes(Return))
    assert len(stmt) == 1
    assert isinstance(stmt[0], Return)
    assert stmt[0].lineref == "src l. 20"


def test_block_nodes():
    sref = SourceRef("file", 1, 1)
    sub1 = Subroutine(name="subaddr", param_spec=[], result_spec=[], address=0xc000, sourceref=sref)
    sub2 = Subroutine(name="subblock", param_spec=[], result_spec=[],
                      scope=Scope(nodes=[Label(name="start", sourceref=sref)], sourceref=sref), sourceref=sref)
    assert sub1.scope is None
    assert sub1.nodes == []
    assert sub2.scope is not None
    assert len(sub2.scope.nodes) > 0
    assert sub2.nodes is sub2.scope.nodes


test_source_2 = """
~ {
    999(1,2)
    &zz()
}
"""


def test_parser_2():
    lexer.lineno = 1
    lexer.source_filename = "sourcefile"
    filter = TokenFilter(lexer)
    result = parser.parse(input=test_source_2, tokenfunc=filter.token)
    block = result.nodes[0]
    call = block.nodes[0]
    assert isinstance(call, SubCall)
    assert len(call.arguments) == 2
    assert isinstance(call.target, CallTarget)
    assert call.target.target == 999
    assert call.target.address_of is False
    call = block.nodes[1]
    assert isinstance(call, SubCall)
    assert len(call.arguments) == 0
    assert isinstance(call.target, CallTarget)
    assert isinstance(call.target.target, SymbolName)
    assert call.target.target.name == "zz"
    assert call.target.address_of is True
