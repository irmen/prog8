"""
Programming Language for 6502/6510 microprocessors
This is the parser of the IL65 code, that generates a parse tree.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

import attr
from ply.yacc import yacc
from typing import Union
from .symbols import SourceRef
from .lexer import tokens, lexer, find_tok_column   # get the lexer tokens. required.


start = "start"


@attr.s(cmp=False, slots=True, frozen=False)
class AstNode:
    sourceref = attr.ib(type=SourceRef)

    @property
    def lineref(self) -> str:
        return "src l. " + str(self.sourceref.line)

    def print_tree(self) -> None:
        def tostr(node: AstNode, level: int) -> None:
            if not isinstance(node, AstNode):
                return
            indent = "   " * level
            name = getattr(node, "name", "")
            print(indent, node.__class__.__name__, repr(name))
            try:
                variables = vars(node).items()
            except TypeError:
                return
            for name, value in variables:
                if isinstance(value, AstNode):
                    tostr(value, level + 1)
                if isinstance(value, (list, tuple, set)):
                    if len(value) > 0:
                        elt = list(value)[0]
                        if isinstance(elt, AstNode) or name == "nodes":
                            print(indent, "  >", name, "=")
                            for elt in value:
                                tostr(elt, level + 2)
        tostr(self, 0)


@attr.s(cmp=False)
class Module(AstNode):
    nodes = attr.ib(type=list)


@attr.s(cmp=False)
class Directive(AstNode):
    name = attr.ib(type=str)
    args = attr.ib(type=list, default=attr.Factory(list))


@attr.s(cmp=False)
class Scope(AstNode):
    nodes = attr.ib(type=list)


@attr.s(cmp=False)
class Block(AstNode):
    scope = attr.ib(type=Scope)
    name = attr.ib(type=str, default=None)
    address = attr.ib(type=int, default=None)


@attr.s(cmp=False)
class Label(AstNode):
    name = attr.ib(type=str)


@attr.s(cmp=False)
class Register(AstNode):
    name = attr.ib(type=str)


@attr.s(cmp=False)
class PreserveRegs(AstNode):
    registers = attr.ib(type=str)


@attr.s(cmp=False)
class Assignment(AstNode):
    left = attr.ib()     # type: Union[str, TargetRegisters, Dereference]
    right = attr.ib()


@attr.s(cmp=False)
class AugAssignment(Assignment):
    operator = attr.ib(type=str)


@attr.s(cmp=False)
class SubCall(AstNode):
    target = attr.ib()
    preserve_regs = attr.ib()
    arguments = attr.ib()


@attr.s(cmp=False)
class Return(AstNode):
    value_A = attr.ib(default=None)
    value_X = attr.ib(default=None)
    value_Y = attr.ib(default=None)


@attr.s(cmp=False)
class TargetRegisters(AstNode):
    registers = attr.ib(type=list)

    def add(self, register: str) -> None:
        self.registers.append(register)


@attr.s(cmp=False)
class InlineAssembly(AstNode):
    assembly = attr.ib(type=str)


@attr.s(cmp=False)
class VarDef(AstNode):
    name = attr.ib(type=str)
    vartype = attr.ib()
    datatype = attr.ib()
    value = attr.ib(default=None)


@attr.s(cmp=False, slots=True)
class Datatype(AstNode):
    name = attr.ib(type=str)
    dimension = attr.ib(type=list, default=None)


@attr.s(cmp=False)
class Subroutine(AstNode):
    name = attr.ib(type=str)
    param_spec = attr.ib()
    result_spec = attr.ib()
    scope = attr.ib(type=Scope, default=None)
    address = attr.ib(type=int, default=None)

    def __attrs_post_init__(self):
        if self.scope is not None and self.address is not None:
            raise ValueError("subroutine must have either a scope or an address, not both")


@attr.s(cmp=False)
class Goto(AstNode):
    target = attr.ib()
    if_stmt = attr.ib(default=None)
    condition = attr.ib(default=None)


@attr.s(cmp=False)
class Dereference(AstNode):
    location = attr.ib()
    datatype = attr.ib()


@attr.s(cmp=False, slots=True)
class CallTarget(AstNode):
    target = attr.ib()
    address_of = attr.ib(type=bool)


@attr.s(cmp=False, slots=True)
class CallArgument(AstNode):
    value = attr.ib()
    name = attr.ib(type=str, default=None)


@attr.s(cmp=False)
class UnaryOp(AstNode):
    operator = attr.ib(type=str)
    operand = attr.ib()


@attr.s(cmp=False, slots=True)
class Expression(AstNode):
    left = attr.ib()
    operator = attr.ib(type=str)
    right = attr.ib()


def p_start(p):
    """
    start :  empty
          |  module_elements
    """
    if p[1]:
        p[0] = Module(nodes=p[1], sourceref=_token_sref(p, 1))


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
        p[0] = Directive(name=p[1], sourceref=_token_sref(p, 1))
    else:
        p[0] = Directive(name=p[1], args=p[2], sourceref=_token_sref(p, 1))


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
    p[0] = Block(name=p[2], address=p[3], scope=p[5], sourceref=_token_sref(p, 1))


def p_block_name(p):
    """
    block :  BITINVERT  NAME  endl_opt  scope
    """
    p[0] = Block(name=p[2], scope=p[4], sourceref=_token_sref(p, 1))


def p_block(p):
    """
    block :  BITINVERT  endl_opt  scope
    """
    p[0] = Block(scope=p[3], sourceref=_token_sref(p, 1))


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
    p[0] = Scope(nodes=p[2], sourceref=_token_sref(p, 1))


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
    p[0] = Label(name=p[1], sourceref=_token_sref(p, 1))


def p_inlineasm(p):
    """
    inlineasm :  INLINEASM  ENDL
    """
    p[0] = InlineAssembly(assembly=p[1], sourceref=_token_sref(p, 1))


def p_vardef(p):
    """
    vardef :  VARTYPE  type_opt  NAME  ENDL
    """
    p[0] = VarDef(name=p[3], vartype=p[1], datatype=p[2], sourceref=_token_sref(p, 1))


def p_vardef_value(p):
    """
    vardef :  VARTYPE  type_opt  NAME  IS  expression
    """
    p[0] = VarDef(name=p[3], vartype=p[1], datatype=p[2], value=p[5], sourceref=_token_sref(p, 1))


def p_type_opt(p):
    """
    type_opt :  DATATYPE  '('  dimensions  ')'
             |  DATATYPE
             |  empty
    """
    if len(p) == 5:
        p[0] = Datatype(name=p[1], dimension=p[3], sourceref=_token_sref(p, 1))
    elif len(p) == 2:
        p[0] = Datatype(name=p[1], sourceref=_token_sref(p, 1))


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
    subroutine :  SUB  NAME  '('  sub_param_spec  ')'  RARROW  '('  sub_result_spec  ')'  subroutine_body  ENDL
    """
    body = p[10]
    if isinstance(body, Scope):
        p[0] = Subroutine(name=p[2], param_spec=p[4], result_spec=p[8], scope=body, sourceref=_token_sref(p, 1))
    elif isinstance(body, int):
        p[0] = Subroutine(name=p[2], param_spec=p[4], result_spec=p[8], address=body, sourceref=_token_sref(p, 1))
    else:
        raise TypeError("subroutine_body", p.slice)


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
    p[0] = UnaryOp(operator=p[2], operand=p[1], sourceref=_token_sref(p, 1))


def p_call_subroutine(p):
    """
    subroutine_call :  calltarget  preserveregs_opt  '('  call_arguments_opt  ')'
    """
    p[0] = SubCall(target=p[1], preserve_regs=p[2], arguments=p[4], sourceref=_token_sref(p, 1))


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
    p[0] = PreserveRegs(registers=p[1], sourceref=_token_sref(p, 1))


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
        p[0] = CallArgument(value=p[1], sourceref=_token_sref(p, 1))
    elif len(p) == 4:
        p[0] = CallArgument(name=p[1], value=p[3], sourceref=_token_sref(p, 1))


def p_return(p):
    """
    return :  RETURN
           |  RETURN  expression
           |  RETURN  expression  ','  expression
           |  RETURN  expression  ','  expression  ','  expression
    """
    if len(p) == 2:
        p[0] = Return(sourceref=_token_sref(p, 1))
    elif len(p) == 3:
        p[0] = Return(value_A=p[2], sourceref=_token_sref(p, 1))
    elif len(p) == 5:
        p[0] = Return(value_A=p[2], value_X=p[4], sourceref=_token_sref(p, 1))
    elif len(p) == 7:
        p[0] = Return(value_A=p[2], value_X=p[4], value_Y=p[6], sourceref=_token_sref(p, 1))


def p_register(p):
    """
    register :  REGISTER
    """
    p[0] = Register(name=p[1], sourceref=_token_sref(p, 1))


def p_goto(p):
    """
    goto :  GOTO  calltarget
    """
    p[0] = Goto(target=p[2], sourceref=_token_sref(p, 1))


def p_conditional_goto_plain(p):
    """
    conditional_goto :  IF  GOTO  calltarget
    """
    p[0] = Goto(target=p[3], if_stmt=p[1], sourceref=_token_sref(p, 1))


def p_conditional_goto_expr(p):
    """
    conditional_goto :  IF  expression  GOTO  calltarget
    """
    p[0] = Goto(target=p[4], if_stmt=p[1], condition=p[2], sourceref=_token_sref(p, 1))


def p_calltarget(p):
    """
    calltarget :  symbolname
               |  INTEGER
               |  BITAND symbolname
               |  dereference
    """
    if len(p) == 2:
        p[0] = CallTarget(target=p[1], address_of=False, sourceref=_token_sref(p, 1))
    elif len(p) == 3:
        p[0] = CallTarget(target=p[2], address_of=True, sourceref=_token_sref(p, 1))


def p_dereference(p):
    """
    dereference :  '['  dereference_operand  ']'
    """
    p[0] = Dereference(location=p[2][0], datatype=p[2][1], sourceref=_token_sref(p, 1))


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
    p[0] = Assignment(left=p[1], right=p[3], sourceref=_token_sref(p, 1))


def p_aug_assignment(p):
    """
    aug_assignment :  assignment_target  AUGASSIGN  expression
    """
    p[0] = AugAssignment(left=p[1], operator=p[2], right=p[3], sourceref=_token_sref(p, 1))


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
    p[0] = Expression(left=p[1], operator=p[2], right=p[3], sourceref=_token_sref(p, 1))


def p_expression_uminus(p):
    """
    expression :  '-'  expression  %prec UNARY_MINUS
    """
    p[0] = UnaryOp(operator=p[1], operand=p[2], sourceref=_token_sref(p, 1))


def p_expression_addressof(p):
    """
    expression :  BITAND  symbolname  %prec UNARY_ADDRESSOF
    """
    p[0] = UnaryOp(operator=p[1], operand=p[2], sourceref=_token_sref(p, 1))


def p_unary_expression_bitinvert(p):
    """
    expression :  BITINVERT  expression
    """
    p[0] = UnaryOp(operator=p[1], operand=p[2], sourceref=_token_sref(p, 1))


def p_expression_group(p):
    """
    expression :  '('  expression  ')'
    """
    p[0] = p[2]


def p_expression_expr_value(p):
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
        p[0] = TargetRegisters(registers=[p[1]], sourceref=_token_sref(p, 1))
    else:
        p[1].add(p[3])
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
    print("RESULT:")
    result.print_tree()
