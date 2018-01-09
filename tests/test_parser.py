from il65.plylex import lexer, tokens, find_tok_column, literals, reserved
from il65.plyparse import parser, TokenFilter, Module, Subroutine, Block, Return


def test_lexer_definitions():
    assert "ENDL" in tokens
    assert "GOTO" in tokens
    assert '+' in literals
    assert ';' not in literals
    assert "return" in reserved
    assert "sub" in reserved
    assert "A" in reserved
    assert "if_cc" in reserved


test_source = """ %output prg, sys

; c1

; c2


~ block $c000 {
         %import a,b


    ; comment

    var .matrix(20,30) m = 9.234556
    ;comment2


    sub calculate () -> () {
        return 
    }
    
    ;z
    
}
"""

def test_lexer():
    lexer.input(test_source)
    lexer.lineno = 1
    tokens = list(iter(lexer))
    token_types = list(t.type for t in tokens)
    assert token_types == ['DIRECTIVE', 'NAME', ',', 'NAME', 'ENDL', 'ENDL', 'ENDL',
                           'BITINVERT', 'NAME', 'INTEGER', '{', 'ENDL',
                           'DIRECTIVE', 'NAME', ',', 'NAME', 'ENDL', 'ENDL',
                           'VARTYPE', 'DATATYPE', '(', 'INTEGER', ',', 'INTEGER', ')', 'NAME', 'IS', 'FLOATINGPOINT', 'ENDL', 'ENDL',
                           'SUB', 'NAME', '(', ')', 'RARROW', '(', ')', '{', 'ENDL', 'RETURN', 'ENDL', '}', 'ENDL', 'ENDL', 'ENDL', 'ENDL',
                           '}', 'ENDL']
    directive_token = tokens[12]
    assert directive_token.type == "DIRECTIVE"
    assert directive_token.value == "import"
    assert directive_token.lineno == 9
    assert directive_token.lexpos == lexer.lexdata.index("%import")
    assert find_tok_column(directive_token) == 10


def test_tokenfilter():
    lexer.input(test_source)
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
                           'VARTYPE', 'DATATYPE', '(', 'INTEGER', ',', 'INTEGER', ')', 'NAME', 'IS', 'FLOATINGPOINT', 'ENDL',
                           'SUB', 'NAME', '(', ')', 'RARROW', '(', ')', '{', 'ENDL', 'RETURN', 'ENDL', '}', 'ENDL',
                           '}', 'ENDL']


def test_parser():
    lexer.lineno = 1
    lexer.source_filename = "sourcefile"
    filter = TokenFilter(lexer)
    result = parser.parse(input=test_source, tokenfunc=filter.token)
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
    assert block.address == 49152
    sub2 = block.scope["calculate"]
    assert sub2 is sub
    assert sub2.lineref == "src l. 18"
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
    assert stmt[0].lineref == "src l. 19"
