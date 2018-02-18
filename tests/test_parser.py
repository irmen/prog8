import math
import pytest
from il65.plylex import lexer, tokens, find_tok_column, literals, reserved, SourceRef
from il65.plyparse import *
from il65.datatypes import DataType, VarType
from il65.constantfold import ConstantFold


def lexer_error(sourceref: SourceRef, fmtstring: str, *args: str) -> None:
    print("ERROR: {}: {}".format(sourceref, fmtstring.format(*args)))


def parse_source(src: str) -> AstNode:
    lexer.lineno = 1
    lexer.source_filename = "sourcefile"
    tfilt = TokenFilter(lexer)
    result = parser.parse(input=src, tokenfunc=tfilt.token)
    connect_parents(result, None)
    return result


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
    result = parse_source(test_source_1)
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


def test_parser_2():
    src = """
    ~ {
        999(1,2)
        [zz]()
    }
    """
    result = parse_source(src)
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


def test_typespec():
    src = """
    ~ {
        [$c000.word] = 5
        [$c000 .byte] = 5
        [AX .word] = 5
        [AX .float] = 5
    }
    """
    result = parse_source(src)
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
    t1 = assignment1.left
    t2 = assignment2.left
    t3 = assignment3.left
    t4 = assignment4.left
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


def test_char_string():
    src = """
    ~ {
        var x1 = '@'
        var x2 = 'π'
        var x3 = 'abc'
        A = '@'
        A = 'π'
        A = 'abc'
    }
    """
    result = parse_source(src)
    block = result.scope.nodes[0]
    var1, var2, var3, assgn1, assgn2, assgn3, = block.scope.nodes
    assert var1.value.value == '@'
    assert var2.value.value == 'π'
    assert var3.value.value == "abc"
    assert assgn1.right.value == '@'
    assert assgn2.right.value == 'π'
    assert assgn3.right.value == "abc"
    # note: the actual one-charactor-to-bytevalue conversion is done at the very latest, when issuing an assignment statement


def test_boolean_int():
    src = """
    ~ {
        var x1 = true
        var x2 = false
        A = true
        A = false
    }
    """
    result = parse_source(src)
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


def test_symbol_lookup():
    sref = SourceRef("test", 1, 1)
    var1 = VarDef(name="var1", vartype="const", datatype=DataType.WORD, sourceref=sref)
    var1.value = LiteralValue(value=42, sourceref=sref)
    var1.value.parent = var1
    var2 = VarDef(name="var2", vartype="const", datatype=DataType.FLOAT, sourceref=sref)
    var2.value = LiteralValue(value=123.456, sourceref=sref)
    var2.value.parent = var2
    label1 = Label(name="outerlabel", sourceref=sref)
    label2 = Label(name="innerlabel", sourceref=sref)
    scope_inner = Scope(nodes=[
        label2,
        var2
    ], level="block", sourceref=sref)
    scope_inner.name = "inner"
    var2.parent = label2.parent = scope_inner
    scope_outer = Scope(nodes=[
        label1,
        var1,
        scope_inner
    ], level="block", sourceref=sref)
    scope_outer.name = "outer"
    var1.parent = label1.parent = scope_inner.parent = scope_outer
    scope_topmost = Scope(nodes=[scope_outer], level="module", sourceref=sref)
    scope_topmost.name = "topmost"
    scope_outer.parent = scope_topmost
    scope_topmost.define_builtin_functions()
    assert scope_inner.parent_scope is scope_outer
    assert scope_outer.parent_scope is scope_topmost
    assert scope_topmost.parent_scope is None
    assert label1.my_scope() is scope_outer
    assert var1.my_scope() is scope_outer
    assert scope_inner.my_scope() is scope_outer
    assert label2.my_scope() is scope_inner
    assert var2.my_scope() is scope_inner
    assert scope_outer.my_scope() is scope_topmost
    with pytest.raises(LookupError):
        scope_topmost.my_scope()
    with pytest.raises(UndefinedSymbolError):
        scope_inner.lookup("unexisting")
    with pytest.raises(UndefinedSymbolError):
        scope_outer.lookup("unexisting")
    assert scope_inner.lookup("innerlabel") is label2
    assert scope_inner.lookup("var2") is var2
    assert scope_inner.lookup("outerlabel") is label1
    assert scope_inner.lookup("var1") is var1
    with pytest.raises(UndefinedSymbolError):
        scope_outer.lookup("innerlabel")
    with pytest.raises(UndefinedSymbolError):
        scope_outer.lookup("var2")
    assert scope_outer.lookup("var1") is var1
    assert scope_outer.lookup("outerlabel") is label1
    math_func = scope_inner.lookup("sin")
    assert isinstance(math_func, BuiltinFunction)
    assert math_func.name == "sin" and math_func.func is math.sin
    builtin_func = scope_inner.lookup("abs")
    assert isinstance(builtin_func, BuiltinFunction)
    assert builtin_func.name == "abs" and builtin_func.func is abs
    # test dotted names:
    with pytest.raises(UndefinedSymbolError):
        scope_inner.lookup("noscope.nosymbol.nothing")
    assert scope_inner.lookup("outer.inner.var2") is var2
    with pytest.raises(UndefinedSymbolError):
        scope_inner.lookup("outer.inner.var1")
    with pytest.raises(UndefinedSymbolError):
        scope_inner.lookup("outer.var2")
    assert scope_inner.lookup("outer.var1") is var1


def test_const_numeric_expressions():
    src = """
~ {
        A = 1+2+3+4+5
        X = 1+2*5+2
        Y = (1+2)*(5+2)
        A = (((10+20)/2)+5)**3
        X = -10-11-12
        Y = 1.234 mod (0.9 / 1.2)
        A = sin(1.234)
        X = round(4.567)-2
        Y = 1+abs(-100)
        A = ~1
        X = -1
        A = 4 << (9-3)
        X = 5000 >> 2
        Y = 999//88
}
"""
    result = parse_source(src)
    if isinstance(result, Module):
        result.scope.define_builtin_functions()
    assignments = list(result.all_nodes(Assignment))
    e = [a.nodes[1] for a in assignments]
    assert all(x.is_compiletime_const() for x in e)
    assert e[0].const_value() == 15     # 1+2+3+4+5
    assert e[1].const_value() == 13     # 1+2*5+2
    assert e[2].const_value() == 21     # (1+2)*(5+2)
    assert e[3].const_value() == 8000   # (((10+20)/2)+5)**3
    assert e[4].const_value() == -33    # -10-11-12
    assert e[5].const_value() == 0.484  # 1.234 mod (0.9 / 1.2)
    assert math.isclose(e[6].const_value(), 0.9438182093746337)   # sin(1.234)
    assert e[7].const_value() == 3      # round(4.567)-2
    assert e[8].const_value() == 101    # 1+abs(-100)
    assert e[9].const_value() == -2     # ~1
    assert e[10].const_value() == -1    # -1
    assert e[11].const_value() == 256   # 4 << (9-3)
    assert e[12].const_value() == 1250  # 5000 >> 2
    assert e[13].const_value() == 11    # 999//88


def test_const_logic_expressions():
    src = """
~ {
        A = true or false
        X = true and false
        Y = true xor false
        A = false and false or true
        X = (false and (false or true))
        Y = not (false or true)
        A = 1 < 2
        X = 1 >= 2
        Y = 1 == (2+3)
}
"""
    result = parse_source(src)
    assignments = list(result.all_nodes(Assignment))
    e = [a.nodes[1] for a in assignments]
    assert all(x.is_compiletime_const() for x in e)
    assert e[0].const_value() == True
    assert e[1].const_value() == False
    assert e[2].const_value() == True
    assert e[3].const_value() == True
    assert e[4].const_value() == False
    assert e[5].const_value() == False
    assert e[6].const_value() == True
    assert e[7].const_value() == False
    assert e[8].const_value() == False


def test_const_other_expressions():
    src = """
~ {
        memory memvar = $c123
        A = &memvar     ; constant
        X = &sin        ; non-constant
        Y = [memvar]    ; non-constant
}
"""
    result = parse_source(src)
    if isinstance(result, Module):
        result.scope.define_builtin_functions()
    assignments = list(result.all_nodes(Assignment))
    e = [a.nodes[1] for a in assignments]
    assert e[0].is_compiletime_const()
    assert e[0].const_value() == 0xc123
    assert not e[1].is_compiletime_const()
    with pytest.raises(TypeError):
        e[1].const_value()
    assert not e[2].is_compiletime_const()
    with pytest.raises(TypeError):
        e[2].const_value()


def test_vdef_const_folds():
    src = """
~ {
    const  cb1 = 123
    const  cb2 = cb1
    const  cb3 = cb1*3
}
"""
    result = parse_source(src)
    if isinstance(result, Module):
        result.scope.define_builtin_functions()
    vd = list(result.all_nodes(VarDef))
    assert vd[0].name == "cb1"
    assert vd[0].vartype == VarType.CONST
    assert vd[0].datatype == DataType.BYTE
    assert isinstance(vd[0].value, LiteralValue)
    assert vd[0].value.value == 123
    assert vd[1].name == "cb2"
    assert vd[1].vartype == VarType.CONST
    assert vd[1].datatype == DataType.BYTE
    assert isinstance(vd[1].value, SymbolName)
    assert vd[1].value.name == "cb1"
    assert vd[2].name == "cb3"
    assert vd[2].vartype == VarType.CONST
    assert vd[2].datatype == DataType.BYTE
    assert isinstance(vd[2].value, ExpressionWithOperator)
    cf = ConstantFold(result)
    cf.fold_constants()
    vd = list(result.all_nodes(VarDef))
    assert vd[0].name == "cb1"
    assert vd[0].vartype == VarType.CONST
    assert vd[0].datatype == DataType.BYTE
    assert isinstance(vd[0].value, LiteralValue)
    assert vd[0].value.value == 123
    assert vd[1].name == "cb2"
    assert vd[1].vartype == VarType.CONST
    assert vd[1].datatype == DataType.BYTE
    assert isinstance(vd[1].value, LiteralValue)
    assert vd[1].value.value == 123
    assert vd[2].name == "cb3"
    assert vd[2].vartype == VarType.CONST
    assert vd[2].datatype == DataType.BYTE
    assert isinstance(vd[2].value, LiteralValue)
    assert vd[2].value.value == 369


def test_vdef_const_expressions():
    src = """
~ {
    var bvar = 99
    var .float fvar = sin(1.2-0.3)
    var .float flt2 = -9.87e-6
    
    bvar ++
    fvar ++
    flt2 ++
    bvar += 2+2
    fvar += 2+3
    flt2 += 2+4
    bvar = 2+5
    fvar = 2+6
    flt2 = 2+7
}
"""
    result = parse_source(src)
    if isinstance(result, Module):
        result.scope.define_builtin_functions()
    cf = ConstantFold(result)
    cf.fold_constants()
    vd = list(result.all_nodes(VarDef))
    assert len(vd)==3
    assert vd[0].name == "bvar"
    assert isinstance(vd[0].value, LiteralValue)
    assert vd[0].value.value == 99
    assert vd[1].name == "fvar"
    assert isinstance(vd[1].value, LiteralValue)
    assert type(vd[1].value.value) is float
    assert math.isclose(vd[1].value.value, math.sin(0.9))
    assert vd[2].name == "flt2"
    assert isinstance(vd[2].value, LiteralValue)
    assert math.isclose(-9.87e-6, vd[2].value.value)
    # test incrdecr assignment target
    nodes = list(result.all_nodes(IncrDecr))
    assert len(nodes) == 3
    assert isinstance(nodes[0].target, SymbolName)
    assert nodes[0].target.name == "bvar"
    assert isinstance(nodes[1].target, SymbolName)
    assert nodes[1].target.name == "fvar"
    assert isinstance(nodes[2].target, SymbolName)
    assert nodes[2].target.name == "flt2"
    # test augassign assignment target
    nodes = list(result.all_nodes(AugAssignment))
    assert len(nodes) == 3
    assert isinstance(nodes[0].left, SymbolName)
    assert nodes[0].left.name == "bvar"
    assert isinstance(nodes[1].left, SymbolName)
    assert nodes[1].left.name == "fvar"
    assert isinstance(nodes[2].left, SymbolName)
    assert nodes[2].left.name == "flt2"
    # test assign assignment target
    nodes = list(result.all_nodes(Assignment))
    assert len(nodes) == 3
    assert isinstance(nodes[0].left, SymbolName)
    assert nodes[0].left.name == "bvar"
    assert isinstance(nodes[1].left, SymbolName)
    assert nodes[1].left.name == "fvar"
    assert isinstance(nodes[2].left, SymbolName)
    assert nodes[2].left.name == "flt2"
