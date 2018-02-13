"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the part of the compiler/optimizer that simplifies expressions by doing
'constant folding' - replacing expressions with constant, compile-time precomputed values.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import sys
from .plylex import SourceRef
from .datatypes import VarType
from .plyparse import *


def handle_internal_error(exc: Exception, msg: str = "") -> None:
    out = sys.stdout
    if out.isatty():
        print("\x1b[1m", file=out)
    print("\nERROR: internal parser/optimizer error: ", exc, file=out)
    if msg:
        print("    Message:", msg, end="\n\n")
    if out.isatty():
        print("\x1b[0m", file=out, end="", flush=True)
    raise exc


class ConstantFold:
    def __init__(self, mod: Module) -> None:
        self.num_warnings = 0
        self.module = mod
        self.optimizations_performed = False

    def fold_constants(self, once: bool=False) -> None:
        self.num_warnings = 0
        if once:
            self._constant_folding()
        else:
            self.optimizations_performed = True
            # keep optimizing as long as there were changes made
            while self.optimizations_performed:
                self.optimizations_performed = False
                self._constant_folding()

    def _constant_folding(self) -> None:
        for expression in list(self.module.all_nodes(Expression)):
            if expression.parent is None or expression.parent.parent is None:
                # stale expression node (was part of an expression that was constant-folded away)
                continue
            if isinstance(expression, LiteralValue):
                continue
            try:
                evaluated = self._process_expression(expression)      # type: ignore
                if evaluated is not expression:
                    # replace the node with the newly evaluated result
                    parent = expression.parent
                    parent.replace_node(expression, evaluated)
                    self.optimizations_performed = True
            except ParseError:
                raise
            except Exception as x:
                handle_internal_error(x, "process_expressions of node {}".format(expression))

    def _process_expression(self, expr: Expression) -> Expression:
        # process/simplify all expressions (constant folding etc)
        result = None   # type: Expression
        if expr.is_compile_constant() or isinstance(expr, ExpressionWithOperator) and expr.must_be_constant:
            result = self._process_constant_expression(expr, expr.sourceref)
        else:
            result = self._process_dynamic_expression(expr, expr.sourceref)
        result.parent = expr.parent
        return result

    def _process_constant_expression(self, expr: Expression, sourceref: SourceRef) -> LiteralValue:
        # the expression must result in a single (constant) value (int, float, whatever) wrapped as LiteralValue.
        if isinstance(expr, LiteralValue):
            return expr
        if expr.is_compile_constant():
            return LiteralValue(value=expr.const_value(), sourceref=sourceref)  # type: ignore
        elif isinstance(expr, SymbolName):
            value = check_symbol_definition(expr.name, expr.my_scope(), expr.sourceref)
            if isinstance(value, VarDef):
                if value.vartype == VarType.MEMORY:
                    raise ExpressionEvaluationError("can't take a memory value, must be a constant", expr.sourceref)
                value = value.value
            if isinstance(value, ExpressionWithOperator):
                raise ExpressionEvaluationError("circular reference?", expr.sourceref)
            elif isinstance(value, LiteralValue):
                return value
            elif isinstance(value, (int, float, str, bool)):
                raise TypeError("symbol value node should not be a python primitive value", expr)
            else:
                raise ExpressionEvaluationError("constant symbol required, not {}".format(value.__class__.__name__), expr.sourceref)
        elif isinstance(expr, AddressOf):
            assert isinstance(expr.name, str)
            value = check_symbol_definition(expr.name, expr.my_scope(), expr.sourceref)
            if isinstance(value, VarDef):
                if value.vartype == VarType.MEMORY:
                    if isinstance(value.value, LiteralValue):
                        return value.value
                    else:
                        raise ExpressionEvaluationError("constant literal value required", value.sourceref)
                if value.vartype == VarType.CONST:
                    raise ExpressionEvaluationError("can't take the address of a constant", expr.sourceref)
                raise ExpressionEvaluationError("address-of this {} isn't a compile-time constant"
                                                .format(value.__class__.__name__), expr.sourceref)
            else:
                raise ExpressionEvaluationError("constant address required, not {}"
                                                .format(value.__class__.__name__), expr.sourceref)
        elif isinstance(expr, SubCall):
            if isinstance(expr.target, SymbolName):      # 'function(1,2,3)'
                funcname = expr.target.name
                if funcname in math_functions or funcname in builtin_functions:
                    func_args = []
                    for a in (self._process_constant_expression(callarg.value, sourceref) for callarg in list(expr.arguments.nodes)):
                        if isinstance(a, LiteralValue):
                            func_args.append(a.value)
                        else:
                            func_args.append(a)
                    func = math_functions.get(funcname, builtin_functions.get(funcname))
                    try:
                        return LiteralValue(value=func(*func_args), sourceref=expr.arguments.sourceref)  # type: ignore
                    except Exception as x:
                        raise ExpressionEvaluationError(str(x), expr.sourceref)
                else:
                    raise ExpressionEvaluationError("can only use math- or builtin function", expr.sourceref)
            elif isinstance(expr.target, Dereference):       # '[...](1,2,3)'
                raise ExpressionEvaluationError("dereferenced value call is not a constant value", expr.sourceref)
            elif isinstance(expr.target, LiteralValue) and type(expr.target.value) is int:   # '64738()'
                raise ExpressionEvaluationError("immediate address call is not a constant value", expr.sourceref)
            else:
                raise NotImplementedError("weird call target", expr.target)
        elif isinstance(expr, ExpressionWithOperator):
            if expr.unary:
                left_sourceref = expr.left.sourceref if isinstance(expr.left, AstNode) else sourceref
                expr.left = self._process_constant_expression(expr.left, left_sourceref)
                expr.left.parent = expr
                if isinstance(expr.left, LiteralValue) and type(expr.left.value) in (int, float):
                    try:
                        if expr.operator == '-':
                            return LiteralValue(value=-expr.left.value, sourceref=expr.left.sourceref)  # type: ignore
                        elif expr.operator == '~':
                            return LiteralValue(value=~expr.left.value, sourceref=expr.left.sourceref)  # type: ignore
                        elif expr.operator in ("++", "--"):
                            raise ValueError("incr/decr should not be an expression")
                        raise ValueError("invalid unary operator", expr.operator)
                    except TypeError as x:
                        raise ParseError(str(x), expr.sourceref) from None
                raise ValueError("invalid operand type for unary operator", expr.left, expr.operator)
            else:
                left_sourceref = expr.left.sourceref if isinstance(expr.left, AstNode) else sourceref
                expr.left = self._process_constant_expression(expr.left, left_sourceref)
                expr.left.parent = expr
                right_sourceref = expr.right.sourceref if isinstance(expr.right, AstNode) else sourceref
                expr.right = self._process_constant_expression(expr.right, right_sourceref)
                expr.right.parent = expr
                if isinstance(expr.left, LiteralValue):
                    if isinstance(expr.right, LiteralValue):
                        return expr.evaluate_primitive_constants(expr.right.sourceref)
                    else:
                        raise ExpressionEvaluationError("constant literal value required on right, not {}"
                                                        .format(expr.right.__class__.__name__), right_sourceref)
                else:
                    raise ExpressionEvaluationError("constant literal value required on left, not {}"
                                                    .format(expr.left.__class__.__name__), left_sourceref)
        else:
            raise ExpressionEvaluationError("constant value required, not {}".format(expr.__class__.__name__), expr.sourceref)

    def _process_dynamic_expression(self, expr: Expression, sourceref: SourceRef) -> Expression:
        # constant-fold a dynamic expression
        if isinstance(expr, LiteralValue):
            return expr
        if expr.is_compile_constant():
            return LiteralValue(value=expr.const_value(), sourceref=sourceref)  # type: ignore
        elif isinstance(expr, SymbolName):
            if expr.is_compile_constant():
                try:
                    return self._process_constant_expression(expr, sourceref)
                except ExpressionEvaluationError:
                    pass
            return expr
        elif isinstance(expr, AddressOf):
            if expr.is_compile_constant():
                try:
                    return self._process_constant_expression(expr, sourceref)
                except ExpressionEvaluationError:
                    pass
            return expr
        elif isinstance(expr, SubCall):
            try:
                return self._process_constant_expression(expr, sourceref)
            except ExpressionEvaluationError:
                if isinstance(expr.target, SymbolName):
                    check_symbol_definition(expr.target.name, expr.my_scope(), expr.target.sourceref)
                return expr
        elif isinstance(expr, (Register, Dereference)):
            return expr
        elif isinstance(expr, ExpressionWithOperator):
            if expr.unary:
                left_sourceref = expr.left.sourceref if isinstance(expr.left, AstNode) else sourceref
                expr.left = self._process_dynamic_expression(expr.left, left_sourceref)
                expr.left.parent = expr
                if expr.is_compile_constant():
                    try:
                        return self._process_constant_expression(expr, sourceref)
                    except ExpressionEvaluationError:
                        pass
                return expr
            else:
                left_sourceref = expr.left.sourceref if isinstance(expr.left, AstNode) else sourceref
                expr.left = self._process_dynamic_expression(expr.left, left_sourceref)
                expr.left.parent = expr
                right_sourceref = expr.right.sourceref if isinstance(expr.right, AstNode) else sourceref
                expr.right = self._process_dynamic_expression(expr.right, right_sourceref)
                expr.right.parent = expr
                if expr.is_compile_constant():
                    try:
                        return self._process_constant_expression(expr, sourceref)
                    except ExpressionEvaluationError:
                        pass
                return expr
        else:
            raise ParseError("expression required, not {}".format(expr.__class__.__name__), expr.sourceref)
