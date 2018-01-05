from ply.yacc import yacc
from .symbols import SourceRef, AstNode
from .lexer import tokens, lexer, find_tok_column   # get the lexer tokens. required.


start = "start"


class Module(AstNode):
    def __init__(self, nodes, sourceref):
        super().__init__(sourceref)
        self.nodes = nodes or []


class Directive(AstNode):
    def __init__(self, name, args, sourceref):
        super().__init__(sourceref)
        self.name = name
        self.args = args or []


class Block(AstNode):
    def __init__(self, name, address, scope, sourceref):
        super().__init__(sourceref)
        self.name = name
        self.address = address
        self.scope = scope


class Scope(AstNode):
    def __init__(self, nodes, sourceref):
        super().__init__(sourceref)
        self.nodes = nodes


class Label(AstNode):
    def __init__(self, name, sourceref):
        super().__init__(sourceref)
        self.name = name


class Register(AstNode):
    def __init__(self, name, sourceref):
        super().__init__(sourceref)
        self.name = name


class PreserveRegs(AstNode):
    def __init__(self, registers, sourceref):
        super().__init__(sourceref)
        self.registers = registers


class Assignment(AstNode):
    def __init__(self, lhs, operator, rhs, sourceref):
        super().__init__(sourceref)
        self.lhs = lhs
        self.operator = operator
        self.rhs = rhs


class SubCall(AstNode):
    def __init__(self, target, arguments, sourceref):
        super().__init__(sourceref)
        self.target = target
        self.arguments = arguments


class InlineAssembly(AstNode):
    def __init__(self, assembly, sourceref):
        super().__init__(sourceref)
        self.assembly = assembly


class VarDef(AstNode):
    def __init__(self, name, vartype, datatype, value, sourceref):
        super().__init__(sourceref)
        self.name = name
        self.vartype = vartype
        self.datatype = datatype
        self.value = value


class Datatype(AstNode):
    def __init__(self, name, dimension, sourceref):
        super().__init__(sourceref)
        self.name = name
        self.dimension = dimension


class Subroutine(AstNode):
    def __init__(self, name, paramspec, resultspec, code, sourceref):
        super().__init__(sourceref)
        self.name = name
        self.paramspec = paramspec
        self.resultspec = resultspec
        self.code = code


class Goto(AstNode):
    def __init__(self, target, ifstmt, condition, sourceref):
        super().__init__(sourceref)
        self.target = target
        self.ifstmt = ifstmt
        self.condition = condition


class UnaryOp(AstNode):
    def __init__(self, operator, operand, sourceref):
        super().__init__(sourceref)
        self.operator = operator
        self.operand = operand


class BinaryOp(AstNode):
    def __init__(self, operator, left, right, sourceref):
        super().__init__(sourceref)
        self.operator = operator
        self.left = left
        self.right = right


class Integer(AstNode):
    def __init__(self, value, sourceref):
        super().__init__(sourceref)
        self.value = value


def p_start(p):
    """start :  empty
             |  module_elements"""
    if p[1]:
        p[0] = Module(p[1], _token_sref(p, 1))


def p_module(p):
    """module_elements :  module_elt
                       |  module_elements  module_elt"""
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[2]]


def p_module_elt(p):
    """module_elt :  directive
                  |  block"""
    p[0] = p[1]


def p_directive(p):
    """directive : DIRECTIVE
                 | DIRECTIVE  directive_args
    """
    if len(p) == 2:
        p[0] = Directive(p[1], None, _token_sref(p, 1))
    else:
        p[0] = Directive(p[1], p[2], _token_sref(p, 1))


def p_directive_args(p):
    """directive_args :  directive_arg
                      |  directive_args  ','  directive_arg
    """
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_directive_arg(p):
    """directive_arg :  NAME
                     |  INTEGER
                     |  STRING
    """
    p[0] = p[1]


def p_block(p):
    """block :  TILDE  NAME  INTEGER  scope
             |  TILDE  NAME  empty  scope
             |  TILDE  empty  empty  scope"""
    p[0] = Block(p[2], p[3], p[4], _token_sref(p, 1))


def p_scope(p):
    """scope : '{' scope_elements_opt '}'"""
    p[0] = Scope(p[2], _token_sref(p, 1))


def p_scope_elements_opt(p):
    """scope_elements_opt : empty
                          | scope_elements"""
    p[0] = p[1]


def p_scope_elements(p):
    """scope_elements :  scope_element
                      |  scope_elements scope_element"""
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[2]]


def p_scope_element(p):
    """scope_element :  directive
                     |  vardef
                     |  subroutine
                     |  label
                     |  inlineasm
                     |  statement"""
    p[0] = p[1]


def p_label(p):
    """label :  LABEL"""
    p[0] = Label(p[1], _token_sref(p, 1))


def p_inlineasm(p):
    """inlineasm :  INLINEASM"""
    p[0] = InlineAssembly(p[1], _token_sref(p, 1))


def p_vardef(p):
    """vardef : VARTYPE type_opt NAME IS literal_value
              | VARTYPE type_opt NAME"""
    if len(p) == 4:
        p[0] = VarDef(p[3], p[1], p[2], None, _token_sref(p, 1))
    else:
        p[0] = VarDef(p[3], p[1], p[2], p[5], _token_sref(p, 1))


def p_type_opt(p):
    """type_opt : DATATYPE
                | DATATYPE '(' dimensions ')'
                | empty"""
    if len(p) == 4:
        p[0] = Datatype(p[1], p[3], _token_sref(p, 1))
    elif p:
        p[0] = Datatype(p[1], None, _token_sref(p, 1))


def p_dimensions(p):
    """dimensions : INTEGER
                  | dimensions ',' INTEGER"""
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_literal_value(p):
    """literal_value : INTEGER
                     | FLOATINGPOINT
                     | STRING
                     | CHARACTER
                     | BOOLEAN"""
    p[0] = p[1]


def p_subroutine(p):
    """subroutine : SUB NAME '(' sub_param_spec ')' RARROW '(' sub_result_spec ')' subroutine_body"""
    p[0] = Subroutine(p[2], p[4], p[8], p[10], _token_sref(p, 1))


def p_sub_param_spec(p):
    """sub_param_spec : empty
                      | sub_param_list"""
    p[0] = p[1]


def p_sub_param_list(p):
    """sub_param_list : sub_param
                      | sub_param_list ',' sub_param"""
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[2]]


def p_sub_param(p):
    """sub_param : LABEL REGISTER
                 | empty REGISTER"""
    p[0] = (p[1], p[2])


def p_param_name(p):
    """param_name : NAME ':'"""
    p[0] = p[1]


def p_sub_result_spec(p):
    """sub_result_spec : empty
                       | '?'
                       | sub_result_list"""
    if p[1] == '?':
        p[0] = ['A', 'X', 'Y']      # '?' means: all registers clobbered
    p[0] = p[1]


def p_sub_result_list(p):
    """sub_result_list : sub_result_reg
                       | sub_result_list ',' sub_result_reg"""
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_sub_result_reg(p):
    """sub_result_reg : REGISTER
                      | CLOBBEREDREGISTER"""
    p[0] = p[1]


def p_subroutine_body(p):
    """subroutine_body : scope
                       | IS INTEGER"""
    if len(p) == 2:
        p[0] = p[1]
    else:
        p[0] = p[2]


def p_statement(p):
    """statement :  assignment
                 |  subroutine_call
                 |  goto
                 |  conditional_goto
                 |  incrdecr
                 |  RETURN
    """
    p[0] = p[1]


def p_incrdecr(p):
    """incrdecr :  register  INCR
                |  register  DECR
                |  symbolname  INCR
                |  symbolname  DECR"""
    p[0] = UnaryOp(p[2], p[1], _token_sref(p, 1))


def p_call_subroutine(p):
    """subroutine_call : symbolname  preserveregs_opt  '(' call_arguments_opt ')'"""
    p[0] = SubCall(p[1], p[3], _token_sref(p, 1))


def p_preserveregs_opt(p):
    """preserveregs_opt :  empty
                        |  preserveregs"""
    p[0] = p[1]


def p_preserveregs(p):
    """preserveregs :  PRESERVEREGS"""
    p[0] = PreserveRegs(p[1], _token_sref(p, 1))


def p_call_arguments_opt(p):
    """call_arguments_opt : empty
                          | call_arguments"""
    p[0] = p[1]


def p_call_arguments(p):
    """call_arguments : call_argument
                      | call_arguments ',' call_argument"""
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_call_argument(p):
    """call_argument : literal_value
                     | register"""
    p[0] = p[1]


def p_register(p):
    """register :  REGISTER"""
    p[0] = Register(p[1], _token_sref(p, 1))


def p_goto(p):
    """goto : GOTO  symbolname
            | GOTO  INTEGER"""
    p[0] = Goto(p[2], None, None, _token_sref(p, 1))


def p_conditional_goto(p):
    """conditional_goto : IF GOTO symbolname"""
    # @todo support conditional expression
    p[0] = Goto(p[3], p[1], None, _token_sref(p, 1))


def p_symbolname(p):
    """symbolname :  NAME
                  |  DOTTEDNAME"""
    p[0] = p[1]


def p_assignment(p):
    """assignment : assignment_lhs assignment_operator assignment_rhs"""
    # @todo replace lhs/rhs by expressions
    p[0] = Assignment(p[1], p[2], p[3], _token_sref(p, 1))


def p_assignment_operator(p):
    """assignment_operator : IS
                           | AUGASSIGN"""
    p[0] = p[1]


def p_unary_operator(p):
    """unary_operator : '+'
                      | '-'
                      | NOT
                      | ADDRESSOF"""
    p[0] = p[1]


def p_assignment_lhs(p):
    """assignment_lhs : register
                      | symbolname
                      | assignment_lhs ',' register
                      | assignment_lhs ',' symbolname"""
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[2]]


def p_assignment_rhs(p):
    """assignment_rhs : literal_value
                      | symbolname
                      | register
                      | subroutine_call"""
    p[0] = p[1]


def p_term(p):
    """term : register"""
    p[0] = p[1]


def p_empty(p):
    """empty :"""
    pass


def p_error(p):
    if p:
        sref = SourceRef("@todo-filename2", p.lineno, find_tok_column(p))
        lexer.error_function("{}: before '{:.20s}' ({})", sref, str(p.value), repr(p))
    else:
        lexer.error_function("{}: at end of input", "@todo-filename3")


def _token_sref(p, token_idx):
    """ Returns the coordinates for the YaccProduction object 'p' indexed
        with 'token_idx'. The coordinate includes the 'lineno' and
        'column'. Both follow the lex semantic, starting from 1.
    """
    last_cr = p.lexer.lexdata.rfind('\n', 0, p.lexpos(token_idx))
    if last_cr < 0:
        last_cr = -1
    column = (p.lexpos(token_idx) - last_cr)
    return SourceRef("@todo-filename", p.lineno(token_idx), column)


precedence = (
    ('nonassoc', "COMMENT"),
)

parser = yacc()


if __name__ == "__main__":
    import sys
    file = sys.stdin  # open(sys.argv[1], "rU")
    result = parser.parse(input=file.read()) or Module(None, SourceRef("@todo-sfile", 1, 1))
    print("RESULT")
    print(str(result))
