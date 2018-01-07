"""
Programming Language for 6502/6510 microprocessors
This is the parser of the IL65 code, that generates a parse tree.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

from typing import List, Any
from ply.yacc import yacc
from .symbols import SourceRef, AstNode
from .lexer import tokens, lexer, find_tok_column   # get the lexer tokens. required.

start = "start"


class Module(AstNode):
    def __init__(self, nodes: List[AstNode], sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.nodes = nodes or []


class Directive(AstNode):
    def __init__(self, name: str, args, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.name = name
        self.args = args or []


class Block(AstNode):
    def __init__(self, name: str, address, scope, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.name = name
        self.address = address
        self.scope = scope


class Scope(AstNode):
    def __init__(self, nodes: List[AstNode], sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.nodes = nodes


class Label(AstNode):
    def __init__(self, name: str, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.name = name


class Register(AstNode):
    def __init__(self, name: str, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.name = name


class PreserveRegs(AstNode):
    def __init__(self, registers, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.registers = registers


class Assignment(AstNode):
    def __init__(self, lhs, rhs, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.lhs = lhs
        self.rhs = rhs


class AugAssignment(Assignment):
    def __init__(self, lhs, operator: str, rhs, sourceref: SourceRef) -> None:
        super().__init__(lhs, rhs, sourceref)
        self.operator = operator


class SubCall(AstNode):
    def __init__(self, target, preserveregs, arguments, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.target = target
        self.preserveregs = preserveregs
        self.arguments = arguments


class Return(AstNode):
    def __init__(self, valueA, valueX, valueY, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.valueA = valueA
        self.valueX = valueX
        self.valueY = valueY


class TargetRegisters(AstNode):
    def __init__(self, registers, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.registers = registers


class InlineAssembly(AstNode):
    def __init__(self, assembly: str, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.assembly = assembly


class VarDef(AstNode):
    def __init__(self, name: str, vartype, datatype, value, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.name = name
        self.vartype = vartype
        self.datatype = datatype
        self.value = value


class Datatype(AstNode):
    def __init__(self, name: str, dimension, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.name = name
        self.dimension = dimension


class Subroutine(AstNode):
    def __init__(self, name: str, paramspec, resultspec, code, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.name = name
        self.paramspec = paramspec
        self.resultspec = resultspec
        self.code = code


class Goto(AstNode):
    def __init__(self, target, ifstmt, condition, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.target = target
        self.ifstmt = ifstmt
        self.condition = condition


class Dereference(AstNode):
    def __init__(self, location, datatype, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.location = location
        self.datatype = datatype


class CallTarget(AstNode):
    def __init__(self, target, address_of: bool, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.target = target
        self.address_of = address_of


class CallArgument(AstNode):
    def __init__(self, name: str, value, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.name = name
        self.value = value


class UnaryOp(AstNode):
    def __init__(self, operator, operand, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.operator = operator
        self.operand = operand


class Expression(AstNode):
    def __init__(self, lhs, operator, rhs, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.lhs = lhs
        self.operator = operator
        self.rhs = rhs


def p_start(p):
    """
    start :  empty
          |  module_elements
    """
    if p[1]:
        p[0] = Module(p[1], _token_sref(p, 1))


def p_module(p):
    """
    module_elements :  module_elt
                    |  module_elements  module_elt
    """
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[2]]


def p_module_elt(p):
    """
    module_elt :  ENDL
               |  directive
               |  block
   """
    p[0] = p[1]


def p_directive(p):
    """
    directive :  DIRECTIVE  ENDL
              |  DIRECTIVE  directive_args  ENDL
    """
    if len(p) == 2:
        p[0] = Directive(p[1], None, _token_sref(p, 1))
    else:
        p[0] = Directive(p[1], p[2], _token_sref(p, 1))


def p_directive_args(p):
    """
    directive_args :  directive_arg
                   |  directive_args  ','  directive_arg
    """
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_directive_arg(p):
    """
    directive_arg :  NAME
                  |  INTEGER
                  |  STRING
    """
    p[0] = p[1]


def p_block_name_addr(p):
    """
    block :  BITINVERT  NAME  INTEGER  endl_opt  scope
    """
    p[0] = Block(p[2], p[3], p[5], _token_sref(p, 1))


def p_block_name(p):
    """
    block :  BITINVERT  NAME  endl_opt  scope
    """
    p[0] = Block(p[2], None, p[4], _token_sref(p, 1))


def p_block(p):
    """
    block :  BITINVERT  endl_opt  scope
    """
    p[0] = Block(None, None, p[3], _token_sref(p, 1))


def p_endl_opt(p):
    """
    endl_opt :  empty
             |  ENDL
    """
    pass


def p_scope(p):
    """
    scope :  '{'  scope_elements_opt  '}'
    """
    p[0] = Scope(p[2], _token_sref(p, 1))


def p_scope_elements_opt(p):
    """
    scope_elements_opt :  empty
                       |  scope_elements
   """
    p[0] = p[1]


def p_scope_elements(p):
    """
    scope_elements :  scope_element
                   |  scope_elements  scope_element
   """
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[2]]


def p_scope_element(p):
    """
    scope_element :  ENDL
                  |  label
                  |  directive
                  |  vardef
                  |  subroutine
                  |  inlineasm
                  |  statement
    """
    p[0] = p[1]


def p_label(p):
    """
    label :  LABEL
    """
    p[0] = Label(p[1], _token_sref(p, 1))


def p_inlineasm(p):
    """
    inlineasm :  INLINEASM  ENDL
    """
    p[0] = InlineAssembly(p[1], _token_sref(p, 1))


def p_vardef(p):
    """
    vardef :  VARTYPE  type_opt  NAME  ENDL
    """
    p[0] = VarDef(p[3], p[1], p[2], None, _token_sref(p, 1))


def p_vardef_value(p):
    """
    vardef :  VARTYPE  type_opt  NAME  IS  expression
    """
    p[0] = VarDef(p[3], p[1], p[2], p[5], _token_sref(p, 1))


def p_type_opt(p):
    """
    type_opt :  DATATYPE  '('  dimensions  ')'
             |  DATATYPE
             |  empty
    """
    if len(p) == 5:
        p[0] = Datatype(p[1], p[3], _token_sref(p, 1))
    elif len(p) == 2:
        p[0] = Datatype(p[1], None, _token_sref(p, 1))


def p_dimensions(p):
    """
    dimensions :  INTEGER
               |  dimensions  ','  INTEGER
    """
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
    """
    subroutine :  SUB NAME  '('  sub_param_spec  ')'  RARROW  '('  sub_result_spec  ')'  subroutine_body  ENDL
    """
    p[0] = Subroutine(p[1], p[3], p[7], p[9], _token_sref(p, 1))


def p_sub_param_spec(p):
    """
    sub_param_spec : empty
                   | sub_param_list
    """
    p[0] = p[1]


def p_sub_param_list(p):
    """
    sub_param_list :  sub_param
                   |  sub_param_list  ','  sub_param
    """
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_sub_param(p):
    """
    sub_param :  LABEL  REGISTER
              |  REGISTER
    """
    if len(p) == 3:
        p[0] = (p[1], p[2])
    elif len(p) == 2:
        p[0] = (None, p[1])


def p_sub_result_spec(p):
    """
    sub_result_spec :  empty
                    |  '?'
                    |  sub_result_list
    """
    if p[1] == '?':
        p[0] = ['A', 'X', 'Y']      # '?' means: all registers clobbered
    else:
        p[0] = p[1]


def p_sub_result_list(p):
    """
    sub_result_list :  sub_result_reg
                    |  sub_result_list  ','  sub_result_reg
    """
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_sub_result_reg(p):
    """
    sub_result_reg :  REGISTER
                   |  CLOBBEREDREGISTER
    """
    p[0] = p[1]


def p_subroutine_body(p):
    """
    subroutine_body :  scope
                    |  IS INTEGER
    """
    if len(p) == 2:
        p[0] = p[1]
    else:
        p[0] = p[2]


def p_statement(p):
    """
    statement :  assignment  ENDL
              |  aug_assignment ENDL
              |  subroutine_call  ENDL
              |  goto  ENDL
              |  conditional_goto  ENDL
              |  incrdecr  ENDL
              |  return  ENDL
    """
    p[0] = p[1]


def p_incrdecr(p):
    """
    incrdecr :  assignment_target  INCR
             |  assignment_target  DECR
    """
    p[0] = UnaryOp(p[2], p[1], _token_sref(p, 1))


def p_call_subroutine(p):
    """
    subroutine_call :  calltarget  preserveregs_opt  '('  call_arguments_opt  ')'
    """
    p[0] = SubCall(p[1], p[2], p[4], _token_sref(p, 1))


def p_preserveregs_opt(p):
    """
    preserveregs_opt :  empty
                     |  preserveregs
    """
    p[0] = p[1]


def p_preserveregs(p):
    """
    preserveregs :  PRESERVEREGS
    """
    p[0] = PreserveRegs(p[1], _token_sref(p, 1))


def p_call_arguments_opt(p):
    """
    call_arguments_opt :  empty
                       |  call_arguments
    """
    p[0] = p[1]


def p_call_arguments(p):
    """
    call_arguments :  call_argument
                   |  call_arguments  ','  call_argument
    """
    if len(p) == 2:
        p[0] = [p[1]]
    else:
        p[0] = p[1] + [p[3]]


def p_call_argument(p):
    """
    call_argument :  expression
                  |  register  IS  expression
                  |  NAME  IS  expression
    """
    if len(p) == 2:
        p[0] = CallArgument(None, p[1], _token_sref(p, 1))
    elif len(p) == 4:
        p[0] = CallArgument(p[1], p[3], _token_sref(p, 1))


def p_return(p):
    """
    return :  RETURN
           |  RETURN  expression
           |  RETURN  expression  ','  expression
           |  RETURN  expression  ','  expression  ','  expression
    """
    if len(p) == 2:
        p[0] = Return(None, None, None, _token_sref(p, 1))
    elif len(p) == 3:
        p[0] = Return(p[2], None, None, _token_sref(p, 1))
    elif len(p) == 5:
        p[0] = Return(p[2], p[4], None, _token_sref(p, 1))
    elif len(p) == 7:
        p[0] = Return(p[2], p[4], p[6], _token_sref(p, 1))


def p_register(p):
    """
    register :  REGISTER
    """
    p[0] = Register(p[1], _token_sref(p, 1))


def p_goto(p):
    """
    goto :  GOTO  calltarget
    """
    p[0] = Goto(p[2], None, None, _token_sref(p, 1))


def p_conditional_goto_plain(p):
    """
    conditional_goto :  IF  GOTO  calltarget
    """
    p[0] = Goto(p[3], p[1], None, _token_sref(p, 1))


def p_conditional_goto_expr(p):
    """
    conditional_goto :  IF  expression  GOTO  calltarget
    """
    p[0] = Goto(p[4], p[1], p[2], _token_sref(p, 1))


def p_calltarget(p):
    """
    calltarget :  symbolname
               |  INTEGER
               |  BITAND symbolname
               |  dereference
    """
    if len(p) == 2:
        p[0] = CallTarget(p[1], False, _token_sref(p, 1))
    elif len(p) == 3:
        p[0] = CallTarget(p[2], True, _token_sref(p, 1))


def p_dereference(p):
    """
    dereference :  '['  dereference_operand  ']'
    """
    p[0] = Dereference(p[2][0], p[2][1], _token_sref(p, 1))


def p_dereference_operand(p):
    """
    dereference_operand :  symbolname  type_opt
                        |  REGISTER  type_opt
                        |  INTEGER  type_opt
    """
    p[0] = (p[1], p[2])


def p_symbolname(p):
    """
    symbolname :  NAME
               |  DOTTEDNAME
    """
    p[0] = p[1]


def p_assignment(p):
    """
    assignment :  assignment_target  IS  expression
               |  assignment_target  IS  assignment
    """
    p[0] = Assignment(p[1], p[3], _token_sref(p, 1))


def p_aug_assignment(p):
    """
    aug_assignment :  assignment_target  AUGASSIGN  expression
    """
    p[0] = AugAssignment(p[1], p[2], p[3], _token_sref(p, 1))


precedence = (
    ('left', '+', '-'),
    ('left', '*', '/'),
    ('right', 'UNARY_MINUS', 'BITINVERT', "UNARY_ADDRESSOF"),
    ('left', "LT", "GT", "LE", "GE", "EQUALS", "NOTEQUALS"),
    ('nonassoc', "COMMENT"),
)


def p_expression(p):
    """
    expression :  expression  '+'  expression
               |  expression  '-'  expression
               |  expression  '*'  expression
               |  expression  '/'  expression
               |  expression  LT  expression
               |  expression  GT  expression
               |  expression  LE  expression
               |  expression  GE  expression
               |  expression  EQUALS  expression
               |  expression  NOTEQUALS  expression
    """
    p[0] = Expression(p[1], p[2], p[3], _token_sref(p, 1))


def p_expression_uminus(p):
    """
    expression :  '-'  expression  %prec UNARY_MINUS
    """
    p[0] = UnaryOp(p[1], p[2], _token_sref(p, 1))


def p_expression_addressof(p):
    """
    expression :  BITAND  symbolname  %prec UNARY_ADDRESSOF
    """
    p[0] = UnaryOp(p[1], p[2], _token_sref(p, 1))


def p_unary_expression_bitinvert(p):
    """
    expression :  BITINVERT  expression
    """
    p[0] = UnaryOp(p[1], p[2], _token_sref(p, 1))


def p_expression_group(p):
    """
    expression :  '('  expression  ')'
    """
    p[0] = p[2]


def p_expression_ass_rhs(p):
    """expression :  expression_value"""
    p[0] = p[1]


def p_expression_value(p):
    """
    expression_value :  literal_value
                     |  symbolname
                     |  register
                     |  subroutine_call
                     |  dereference
    """
    p[0] = p[1]


def p_assignment_target(p):
    """
    assignment_target :  target_registers
                      |  symbolname
                      |  dereference
    """
    p[0] = p[1]


def p_target_registers(p):
    """
    target_registers :  register
                     |  target_registers  ','  register
    """
    if len(p) == 2:
        p[0] = TargetRegisters([p[1]], _token_sref(p, 1))
    else:
        p[1].add_register(p[3])
        p[0] = p[1]



def p_empty(p):
    """empty :"""
    pass


def p_error(p):
    if p:
        sref = SourceRef(p.lexer.source_filename, p.lineno, find_tok_column(p))
        p.lexer.error_function("{}: before '{:.20s}' ({})", sref, str(p.value), repr(p))
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
    return SourceRef(p.lexer.source_filename, p.lineno(token_idx), column)


class TokenFilter:
    def __init__(self, lexer):
        self.lexer = lexer
        self.prev_was_EOL = False
        assert "ENDL" in tokens

    def token(self):
        # make sure we only ever emit ONE "ENDL" token in sequence
        if self.prev_was_EOL:
            # skip all EOLS that might follow
            while True:
                tok = self.lexer.token()
                if not tok or tok.type != "ENDL":
                    break
            self.prev_was_EOL = False
        else:
            tok = self.lexer.token()
            self.prev_was_EOL = tok and tok.type == "ENDL"
        return tok


parser = yacc(write_tables=True)


if __name__ == "__main__":
    import sys
    file = sys.stdin  # open(sys.argv[1], "rU")
    lexer.source_filename = "derp"
    tokenfilter = TokenFilter(lexer)
    result = parser.parse(input=file.read(),
                          tokenfunc=tokenfilter.token) or Module(None, SourceRef(lexer.source_filename, 1, 1))
    # print("RESULT:")
    # print(str(result))
