"""
Intermediate Language for 6502/6510 microprocessors
This is the expression parser/evaluator.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

import ast
from typing import Union, Optional
from .symbols import FLOAT_MAX_POSITIVE, FLOAT_MAX_NEGATIVE, SourceRef, SymbolTable, SymbolError, DataType, PrimitiveType


class ParseError(Exception):
    def __init__(self, message: str, text: str, sourceref: SourceRef) -> None:
        self.sourceref = sourceref
        self.msg = message
        self.text = text

    def __str__(self):
        return "{} {:s}".format(self.sourceref, self.msg)


class SourceLine:
    def __init__(self, text: str, sourceref: SourceRef) -> None:
        self.sourceref = sourceref
        self.text = text.strip()

    def to_error(self, message: str) -> ParseError:
        return ParseError(message, self.text, self.sourceref)

    def preprocess(self) -> str:
        # transforms the source text into valid Python syntax by bending some things, so ast can parse it.
        # $d020      ->  0xd020
        # %101001    ->  0xb101001
        # #something ->  __ptr@something   (matmult operator)
        text = ""
        quotes_stack = ""
        characters = enumerate(self.text + " ")
        for i, c in characters:
            if c in ("'", '"'):
                if quotes_stack and quotes_stack[-1] == c:
                    quotes_stack = quotes_stack[:-1]
                else:
                    quotes_stack += c
                text += c
                continue
            if not quotes_stack:
                if c == '%' and self.text[i + 1] in "01":
                    text += "0b"
                    continue
                if c == '$' and self.text[i + 1] in "0123456789abcdefABCDEF":
                    text += "0x"
                    continue
                if c == '#':
                    if i > 0:
                        text += " "
                    text += "__ptr@"
                    continue
            text += c
        return text


def parse_expr_as_int(text: str, context: Optional[SymbolTable], sourceref: SourceRef, *,
                      minimum: int=0, maximum: int=0xffff) -> int:
    result = parse_expr_as_primitive(text, context, sourceref, minimum=minimum, maximum=maximum)
    if isinstance(result, int):
        return result
    src = SourceLine(text, sourceref)
    raise src.to_error("int expected, not " + type(result).__name__)


def parse_expr_as_number(text: str, context: Optional[SymbolTable], sourceref: SourceRef, *,
                         minimum: float=FLOAT_MAX_NEGATIVE, maximum: float=FLOAT_MAX_POSITIVE) -> Union[int, float]:
    result = parse_expr_as_primitive(text, context, sourceref, minimum=minimum, maximum=maximum)
    if isinstance(result, (int, float)):
        return result
    src = SourceLine(text, sourceref)
    raise src.to_error("int or float expected, not " + type(result).__name__)


def parse_expr_as_string(text: str, context: Optional[SymbolTable], sourceref: SourceRef) -> str:
    result = parse_expr_as_primitive(text, context, sourceref)
    if isinstance(result, str):
        return result
    src = SourceLine(text, sourceref)
    raise src.to_error("string expected, not " + type(result).__name__)


def parse_expr_as_primitive(text: str, context: Optional[SymbolTable], sourceref: SourceRef, *,
                            minimum: float = FLOAT_MAX_NEGATIVE, maximum: float = FLOAT_MAX_POSITIVE) -> PrimitiveType:
    src = SourceLine(text, sourceref)
    text = src.preprocess()
    try:
        node = ast.parse(text, sourceref.file, mode="eval")
    except SyntaxError as x:
        raise src.to_error(str(x))
    if isinstance(node, ast.Expression):
        result = ExpressionTransformer(src, context).evaluate(node)
    else:
        raise TypeError("ast.Expression expected")
    if isinstance(result, bool):
        return int(result)
    if isinstance(result, (int, float)):
        if minimum <= result <= maximum:
            return result
        raise src.to_error("number too large")
    if isinstance(result, str):
        return result
    raise src.to_error("int or float or string expected, not " + type(result).__name__)


def parse_statement(text: str, sourceref: SourceRef) -> int:    # @todo in progress...
    src = SourceLine(text, sourceref)
    text = src.preprocess()
    node = ast.parse(text, sourceref.file, mode="single")
    return node


class EvaluatingTransformer(ast.NodeTransformer):
    def __init__(self, src: SourceLine, context: SymbolTable) -> None:
        super().__init__()
        self.src = src
        self.context = context

    def error(self, message: str, column: int=0) -> ParseError:
        if column:
            ref = self.src.sourceref.copy()
            ref.column = column
        else:
            ref = self.src.sourceref
        return ParseError(message, self.src.text, ref)

    def evaluate(self, node: ast.Expression) -> PrimitiveType:
        node = self.visit(node)
        code = compile(node, self.src.sourceref.file, mode="eval")
        if self.context:
            globals = None
            locals = self.context.as_eval_dict()
        else:
            globals = {"__builtins__": {}}
            locals = None
        try:
            result = eval(code, globals, locals)
        except Exception as x:
            raise self.src.to_error(str(x))
        else:
            if type(result) is bool:
                return int(result)
            return result


class ExpressionTransformer(EvaluatingTransformer):
    def _dotted_name_from_attr(self, node: ast.Attribute) -> str:
        if isinstance(node.value, ast.Name):
            return node.value.id + '.' + node.attr
        if isinstance(node.value, ast.Attribute):
            return self._dotted_name_from_attr(node.value) + '.' + node.attr
        raise self.error("dotted name error")

    def visit_Name(self, node: ast.Name):
        # convert true/false names to True/False constants
        if node.id == "true":
            return ast.copy_location(ast.NameConstant(True), node)
        if node.id == "false":
            return ast.copy_location(ast.NameConstant(False), node)
        return node

    def visit_UnaryOp(self, node):
        if isinstance(node.operand, ast.Num):
            if isinstance(node.op, ast.USub):
                node = self.generic_visit(node)
                return ast.copy_location(ast.Num(-node.operand.n), node)
            if isinstance(node.op, ast.UAdd):
                node = self.generic_visit(node)
                return ast.copy_location(ast.Num(node.operand.n), node)
            raise self.error("expected unary + or -")
        else:
            raise self.error("expected numeric operand for unary operator")

    def visit_BinOp(self, node):
        node = self.generic_visit(node)
        if isinstance(node.op, ast.MatMult):
            if isinstance(node.left, ast.Name) and node.left.id == "__ptr":
                if isinstance(node.right, ast.Attribute):
                    symbolname = self._dotted_name_from_attr(node.right)
                elif isinstance(node.right, ast.Name):
                    symbolname = node.right.id
                else:
                    raise self.error("can only take address of a named variable")
                try:
                    address = self.context.get_address(symbolname)
                except SymbolError as x:
                    raise self.error(str(x))
                else:
                    return ast.copy_location(ast.Num(address), node)
            else:
                raise self.error("invalid MatMult/Pointer node in AST")
        return node


if __name__ == "__main__":
    symbols = SymbolTable("<root>", None, None)
    symbols.define_variable("derp", SourceRef("<source>", 1), DataType.BYTE, address=2345)
    result = parse_expr_as_primitive("2+#derp",  symbols, SourceRef("<source>", 1))
    print("EXPRESSION RESULT:", result)
