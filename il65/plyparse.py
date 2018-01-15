"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the parser of the IL65 code, that generates a parse tree.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import math
import builtins
import inspect
import enum
from collections import defaultdict
from typing import Union, Generator, Tuple, List, Optional, Dict, Any, Iterable
import attr
from ply.yacc import yacc
from .plylex import SourceRef, tokens, lexer, find_tok_column, print_warning
from .datatypes import DataType, VarType, REGISTER_SYMBOLS, REGISTER_BYTES, REGISTER_WORDS, \
    char_to_bytevalue, FLOAT_MAX_NEGATIVE, FLOAT_MAX_POSITIVE


class ProgramFormat(enum.Enum):
    RAW = "raw"
    PRG = "prg"
    BASIC = "basicprg"


class ZpOptions(enum.Enum):
    NOCLOBBER = "noclobber"
    CLOBBER = "clobber"
    CLOBBER_RESTORE = "clobber_restore"


math_functions = {name: func for name, func in vars(math).items() if inspect.isbuiltin(func)}
builtin_functions = {name: func for name, func in vars(builtins).items() if inspect.isbuiltin(func)}


class ParseError(Exception):
    def __init__(self, message: str, sourceref: SourceRef) -> None:
        super().__init__(message)
        self.sourceref = sourceref
        # @todo chain attribute, a list of other exceptions, so we can have more than 1 error at a time.

    def __str__(self):
        return "{} {:s}".format(self.sourceref, self.args[0])


class ExpressionEvaluationError(ParseError):
    pass


start = "start"


@attr.s(cmp=False, slots=True, frozen=False)
class AstNode:
    sourceref = attr.ib(type=SourceRef)
    # when evaluating an expression, does it have to be a constant value?:
    processed_expr_must_be_constant = attr.ib(type=bool, init=False, default=False)

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

    def process_expressions(self, scope: 'Scope') -> None:
        # process/simplify all expressions (constant folding etc)
        # this is implemented in node types that have expression(s) and that should act on this.
        pass


@attr.s(cmp=False, repr=False)
class Directive(AstNode):
    name = attr.ib(type=str)
    args = attr.ib(type=list, default=attr.Factory(list))


@attr.s(cmp=False, slots=True, repr=False)
class Scope(AstNode):
    nodes = attr.ib(type=list)
    symbols = attr.ib(init=False)
    name = attr.ib(init=False)          # will be set by enclosing block, or subroutine etc.
    parent_scope = attr.ib(init=False, default=None)  # will be wired up later
    save_registers = attr.ib(type=bool, default=None, init=False)  # None = look in parent scope's setting  @todo property that does that

    def __attrs_post_init__(self):
        # populate the symbol table for this scope for fast lookups via scope["name"] or scope["dotted.name"]
        self.symbols = {}
        for node in self.nodes:
            assert isinstance(node, AstNode)
            self._populate_symboltable(node)

    def _populate_symboltable(self, node: AstNode) -> None:
        if isinstance(node, (Label, VarDef)):
            if node.name in self.symbols:
                raise ParseError("symbol already defined at {}".format(self.symbols[node.name].sourceref), node.sourceref)
            self.symbols[node.name] = node
        if isinstance(node, Subroutine):
            if node.name in self.symbols:
                raise ParseError("symbol already defined at {}".format(self.symbols[node.name].sourceref), node.sourceref)
            self.symbols[node.name] = node
            if node.scope:
                node.scope.parent_scope = self
        if isinstance(node, Block):
            if node.name:
                if node.name != "ZP" and node.name in self.symbols:
                    raise ParseError("symbol already defined at {}".format(self.symbols[node.name].sourceref), node.sourceref)
                self.symbols[node.name] = node
                node.scope.parent_scope = self

    def __getitem__(self, name: str) -> AstNode:
        assert isinstance(name, str)
        if '.' in name:
            # look up the dotted name starting from the topmost scope
            scope = self
            while scope.parent_scope:
                scope = scope.parent_scope
            for namepart in name.split('.'):
                if isinstance(scope, (Block, Subroutine)):
                    scope = scope.scope
                if not isinstance(scope, Scope):
                    raise LookupError("undefined symbol: " + name)
                scope = scope.symbols.get(namepart, None)
                if not scope:
                    raise LookupError("undefined symbol: " + name)
            return scope
        else:
            # find the name in nested scope hierarchy
            if name in self.symbols:
                return self.symbols[name]
            if self.parent_scope:
                return self.parent_scope[name]
            raise LookupError("undefined symbol: " + name)

    def filter_nodes(self, nodetype) -> Generator[AstNode, None, None]:
        for node in self.nodes:
            if isinstance(node, nodetype):
                yield node

    def remove_node(self, node: AstNode) -> None:
        if hasattr(node, "name"):
            try:
                del self.symbols[node.name]     # type: ignore
            except KeyError:
                pass
        self.nodes.remove(node)

    def replace_node(self, oldnode: AstNode, newnode: AstNode) -> None:
        assert isinstance(newnode, AstNode)
        idx = self.nodes.index(oldnode)
        self.nodes[idx] = newnode
        if hasattr(oldnode, "name"):
            del self.symbols[oldnode.name]  # type: ignore

    def add_node(self, newnode: AstNode, index: int=None) -> None:
        assert isinstance(newnode, AstNode)
        if index is None:
            self.nodes.append(newnode)
        else:
            self.nodes.insert(index, newnode)
        self._populate_symboltable(newnode)


def validate_address(obj: AstNode, attrib: attr.Attribute, value: Optional[int]) -> None:
    if value is None:
        return
    if isinstance(obj, Block) and obj.name == "ZP":
        raise ParseError("zeropage block cannot have custom start {:s}".format(attrib.name), obj.sourceref)
    if value < 0x0200 or value > 0xffff:
        raise ParseError("invalid {:s} (must be from $0200 to $ffff)".format(attrib.name), obj.sourceref)


def dimensions_validator(obj: 'DatatypeNode', attrib: attr.Attribute, value: List[int]) -> None:
    if not value:
        return
    dt = obj.to_enum()
    if value and dt not in (DataType.MATRIX, DataType.WORDARRAY, DataType.BYTEARRAY):
        raise ParseError("cannot use a dimension for this datatype", obj.sourceref)
    if dt == DataType.WORDARRAY or dt == DataType.BYTEARRAY:
        if len(value) == 1:
            if value[0] <= 0 or value[0] > 256:
                raise ParseError("array length must be 1..256", obj.sourceref)
        else:
            raise ParseError("array must have only one dimension", obj.sourceref)
    if dt == DataType.MATRIX:
        if len(value) < 2 or len(value) > 3:
            raise ParseError("matrix must have two dimensions, with optional interleave", obj.sourceref)
        if len(value) == 3:
            if value[2] < 1 or value[2] > 256:
                raise ParseError("matrix interleave must be 1..256", obj.sourceref)
        if value[0] < 0 or value[0] > 128 or value[1] < 0 or value[1] > 128:
            raise ParseError("matrix rows and columns must be 1..128", obj.sourceref)


@attr.s(cmp=False, repr=False)
class Block(AstNode):
    scope = attr.ib(type=Scope)
    name = attr.ib(type=str, default=None)
    address = attr.ib(type=int, default=None, validator=validate_address)
    _unnamed_block_labels = {}  # type: Dict[Block, str]

    def __attrs_post_init__(self):
        self.scope.name = self.name

    @property
    def nodes(self) -> Iterable[AstNode]:
        if self.scope:
            return self.scope.nodes
        return []

    @property
    def label(self) -> str:
        if self.name:
            return self.name
        if self in self._unnamed_block_labels:
            return self._unnamed_block_labels[self]
        label = "il65_block_{:d}".format(len(self._unnamed_block_labels))
        self._unnamed_block_labels[self] = label
        return label


@attr.s(cmp=False, repr=False)
class Module(AstNode):
    name = attr.ib(type=str)     # filename
    scope = attr.ib(type=Scope)
    subroutine_usage = attr.ib(type=defaultdict, init=False, default=attr.Factory(lambda: defaultdict(set)))    # will be populated later
    format = attr.ib(type=ProgramFormat, init=False, default=ProgramFormat.PRG)     # can be set via directive
    address = attr.ib(type=int, init=False, default=0xc000, validator=validate_address)     # can be set via directive
    zp_options = attr.ib(type=ZpOptions, init=False, default=ZpOptions.NOCLOBBER)    # can be set via directive

    @property
    def nodes(self) -> Iterable[AstNode]:
        if self.scope:
            return self.scope.nodes
        return []

    def all_scopes(self) -> Generator[Tuple[AstNode, AstNode], None, None]:
        # generator that recursively yields through the scopes (preorder traversal), yields (node, parent_node) tuples.
        # it iterates of copies of the node collections, so it's okay to modify the scopes you iterate over.
        yield self, None
        for block in list(self.scope.filter_nodes(Block)):
            yield block, self
            for subroutine in list(block.scope.filter_nodes(Subroutine)):
                yield subroutine, block

    def zeropage(self) -> Optional[Block]:
        # return the zeropage block (if defined)
        first_block = next(self.scope.filter_nodes(Block))
        if first_block.name == "ZP":
            return first_block
        return None

    def main(self) -> Optional[Block]:
        # return the 'main' block (if defined)
        for block in self.scope.filter_nodes(Block):
            if block.name == "main":
                return block
        return None


@attr.s(cmp=False, repr=False)
class Label(AstNode):
    name = attr.ib(type=str)


@attr.s(cmp=False, repr=False)
class Register(AstNode):
    name = attr.ib(type=str, validator=attr.validators.in_(REGISTER_SYMBOLS))
    datatype = attr.ib(type=DataType, init=False)

    def __attrs_post_init__(self):
        if self.name in REGISTER_BYTES:
            self.datatype = DataType.BYTE
        elif self.name in REGISTER_WORDS:
            self.datatype = DataType.WORD
        else:
            self.datatype = None    # register 'SC' etc.

    def __hash__(self) -> int:
        return hash(self.name)

    def __eq__(self, other) -> bool:
        if not isinstance(other, Register):
            return NotImplemented
        return self.name == other.name

    def __lt__(self, other) -> bool:
        if not isinstance(other, Register):
            return NotImplemented
        return self.name < other.name


@attr.s(cmp=False, repr=False)
class PreserveRegs(AstNode):
    registers = attr.ib(type=str)


@attr.s(cmp=False, repr=False)
class Assignment(AstNode):
    # can be single- or multi-assignment
    left = attr.ib(type=list)     # type: List[Union[str, TargetRegisters, Dereference]]
    right = attr.ib()

    def process_expressions(self, scope: Scope) -> None:
        self.right = process_expression(self.right, scope, self.right.sourceref)


@attr.s(cmp=False, repr=False)
class AugAssignment(AstNode):
    left = attr.ib()
    operator = attr.ib(type=str)
    right = attr.ib()

    def process_expressions(self, scope: Scope) -> None:
        self.right = process_expression(self.right, scope, self.right.sourceref)


@attr.s(cmp=False, repr=False)
class SubCall(AstNode):
    target = attr.ib()
    preserve_regs = attr.ib()
    arguments = attr.ib()

    def __attrs_post_init__(self):
        self.arguments = self.arguments or []

    def process_expressions(self, scope: Scope) -> None:
        for callarg in self.arguments:
            assert isinstance(callarg, CallArgument)
            callarg.process_expressions(scope)


@attr.s(cmp=False, repr=False)
class Return(AstNode):
    value_A = attr.ib(default=None)
    value_X = attr.ib(default=None)
    value_Y = attr.ib(default=None)

    def process_expressions(self, scope: Scope) -> None:
        if self.value_A is not None:
            self.value_A = process_expression(self.value_A, scope, self.sourceref)
            if isinstance(self.value_A, (int, float, str, bool)):
                try:
                    _, self.value_A = coerce_constant_value(DataType.BYTE, self.value_A, self.sourceref)
                except (OverflowError, TypeError) as x:
                    raise ParseError("first value (A): " + str(x), self.sourceref) from None
        if self.value_X is not None:
            self.value_X = process_expression(self.value_X, scope, self.sourceref)
            if isinstance(self.value_X, (int, float, str, bool)):
                try:
                    _, self.value_X = coerce_constant_value(DataType.BYTE, self.value_X, self.sourceref)
                except (OverflowError, TypeError) as x:
                    raise ParseError("second value (X): " + str(x), self.sourceref) from None
        if self.value_Y is not None:
            self.value_Y = process_expression(self.value_Y, scope, self.sourceref)
            if isinstance(self.value_Y, (int, float, str, bool)):
                try:
                    _, self.value_Y = coerce_constant_value(DataType.BYTE, self.value_Y, self.sourceref)
                except (OverflowError, TypeError) as x:
                    raise ParseError("third value (Y): " + str(x), self.sourceref) from None


@attr.s(cmp=False, repr=False)
class TargetRegisters(AstNode):
    # This is a tuple of 1 or more registers.
    # In it's multiple-register form it is only used to be able to parse
    # the result of a subroutine call such as A,X = sub().
    # It will be replaced by a regular Register node if it contains just one register.
    registers = attr.ib(type=list)

    def add(self, register: str) -> None:
        self.registers.append(register)


@attr.s(cmp=False, repr=False)
class InlineAssembly(AstNode):
    assembly = attr.ib(type=str)


@attr.s(cmp=False, repr=True, slots=True)
class VarDef(AstNode):
    name = attr.ib(type=str)
    vartype = attr.ib()
    datatype = attr.ib()
    value = attr.ib(default=None)
    size = attr.ib(type=list, default=None)
    zp_address = attr.ib(type=int, default=None, init=False)    # the address in the zero page if this var is there, will be set later

    def __attrs_post_init__(self):
        # convert vartype to enum
        if self.vartype == "const":
            self.vartype = VarType.CONST
        elif self.vartype == "var":
            self.vartype = VarType.VAR
        elif self.vartype == "memory":
            self.vartype = VarType.MEMORY
        else:
            raise ValueError("invalid vartype", self.vartype)
        # convert datatype node to enum + size
        if self.datatype is None:
            assert self.size is None
            self.size = [1]
            self.datatype = DataType.BYTE
        elif isinstance(self.datatype, DatatypeNode):
            assert self.size is None
            self.size = self.datatype.dimensions or [1]
            self.datatype = self.datatype.to_enum()
        if self.datatype.isarray() and sum(self.size) in (0, 1):
            print("warning: {}: array/matrix with size 1, use normal byte/word instead for efficiency".format(self.sourceref))
        if self.vartype == VarType.CONST and self.value is None:
            raise ParseError("constant value assignment is missing",
                             attr.evolve(self.sourceref, column=self.sourceref.column+len(self.name)))
        # if the value is an expression, mark it as a *constant* expression here
        if isinstance(self.value, AstNode):
            self.value.processed_expr_must_be_constant = True
        elif self.value is None and (self.datatype.isnumeric() or self.datatype.isarray()):
            self.value = 0
        # if it's a matrix with interleave, it must be memory mapped
        if self.datatype == DataType.MATRIX and len(self.size) == 3:
            if self.vartype != VarType.MEMORY:
                raise ParseError("matrix with interleave can only be a memory-mapped variable", self.sourceref)
        # note: value coercion is done later, when all expressions are evaluated

    def process_expressions(self, scope: Scope) -> None:
        self.value = process_expression(self.value, scope, self.sourceref)
        assert not isinstance(self.value, Expression), "processed expression for vardef should reduce to a constant value"
        if self.vartype in (VarType.CONST, VarType.VAR):
            try:
                _, self.value = coerce_constant_value(self.datatype, self.value, self.sourceref)
            except OverflowError as x:
                raise ParseError(str(x), self.sourceref) from None
            except TypeError as x:
                raise ParseError("processed expression vor vardef is not a constant value: " + str(x), self.sourceref) from None


@attr.s(cmp=False, slots=True, repr=False)
class DatatypeNode(AstNode):
    name = attr.ib(type=str)
    dimensions = attr.ib(type=list, default=None, validator=dimensions_validator)    # if set, 1 or more dimensions (ints)

    def to_enum(self):
        return {
            "byte": DataType.BYTE,
            "word": DataType.WORD,
            "float": DataType.FLOAT,
            "text": DataType.STRING,
            "ptext": DataType.STRING_P,
            "stext": DataType.STRING_S,
            "pstext": DataType.STRING_PS,
            "matrix": DataType.MATRIX,
            "array": DataType.BYTEARRAY,
            "wordarray": DataType.WORDARRAY
        }[self.name]


@attr.s(cmp=False, repr=False)
class Subroutine(AstNode):
    name = attr.ib(type=str)
    param_spec = attr.ib(type=list)
    result_spec = attr.ib(type=list)
    scope = attr.ib(type=Scope, default=None)
    address = attr.ib(type=int, default=None, validator=validate_address)

    @property
    def nodes(self) -> Iterable[AstNode]:
        if self.scope:
            return self.scope.nodes
        return []

    def __attrs_post_init__(self):
        if self.scope and self.address is not None:
            raise ValueError("subroutine must have either a scope or an address, not both")
        if self.scope:
            self.scope.name = self.name


@attr.s(cmp=False, repr=False)
class Goto(AstNode):
    target = attr.ib()
    if_stmt = attr.ib(default=None)
    condition = attr.ib(default=None)

    def process_expressions(self, scope: Scope) -> None:
        if self.condition is not None:
            self.condition = process_expression(self.condition, scope, self.condition.sourceref)


@attr.s(cmp=False, repr=False)
class Dereference(AstNode):
    location = attr.ib()
    datatype = attr.ib()
    size = attr.ib(type=int, default=None)

    def __attrs_post_init__(self):
        # convert datatype node to enum + size
        if self.datatype is None:
            assert self.size is None
            self.size = 1
            self.datatype = DataType.BYTE
        elif isinstance(self.datatype, DatatypeNode):
            assert self.size is None
            self.size = self.datatype.dimensions
            if not self.datatype.to_enum().isnumeric():
                raise ParseError("dereference target value must be byte, word, float", self.datatype.sourceref)
            self.datatype = self.datatype.to_enum()


@attr.s(cmp=False, repr=False)
class LiteralValue(AstNode):
    value = attr.ib()


@attr.s(cmp=False, repr=False)
class AddressOf(AstNode):
    name = attr.ib(type=str)


@attr.s(cmp=False, repr=False)
class IncrDecr(AstNode):
    # increment or decrement something by a CONSTANT value (1 or more)
    target = attr.ib()
    operator = attr.ib(type=str, validator=attr.validators.in_(["++", "--"]))
    howmuch = attr.ib(default=1)

    def __attrs_post_init__(self):
        # make sure the amount is always >= 0
        if self.howmuch < 0:
            self.howmuch = -self.howmuch
            self.operator = "++" if self.operator == "--" else "--"
        if isinstance(self.target, Register):
            if self.target.name not in REGISTER_BYTES | REGISTER_WORDS:
                raise ParseError("cannot incr/decr that register", self.sourceref)
        if isinstance(self.target, TargetRegisters):
            raise ParseError("cannot incr/decr multiple registers at once", self.sourceref)


@attr.s(cmp=False, repr=False)
class SymbolName(AstNode):
    name = attr.ib(type=str)


@attr.s(cmp=False, slots=True, repr=False)
class CallTarget(AstNode):
    target = attr.ib()
    address_of = attr.ib(type=bool)


@attr.s(cmp=False, slots=True, repr=False)
class CallArgument(AstNode):
    value = attr.ib()
    name = attr.ib(type=str, default=None)

    def process_expressions(self, scope: Scope) -> None:
        self.value = process_expression(self.value, scope, self.sourceref)


@attr.s(cmp=False, slots=True, repr=False)
class Expression(AstNode):
    left = attr.ib()
    operator = attr.ib(type=str)
    right = attr.ib()
    unary = attr.ib(type=bool, default=False)

    def __attrs_post_init__(self):
        assert self.operator not in ("++", "--"), "incr/decr should not be an expression"

    def process_expressions(self, scope: Scope) -> None:
        raise RuntimeError("must be done via parent node's process_expressions")

    def evaluate_primitive_constants(self, scope: Scope) -> Union[int, float, str, bool]:
        # make sure the lvalue and rvalue are primitives, and the operator is allowed
        if not isinstance(self.left, (LiteralValue, int, float, str, bool)):
            raise TypeError("left", self)
        if not isinstance(self.right, (LiteralValue, int, float, str, bool)):
            raise TypeError("right", self)
        if self.operator not in {'+', '-', '*', '/', '//', '~', '<', '>', '<=', '>=', '==', '!='}:
            raise ValueError("operator", self)
        estr = "{} {} {}".format(repr(self.left), self.operator, repr(self.right))
        try:
            return eval(estr, {}, {})   # safe because of checks above
        except Exception as x:
            raise ExpressionEvaluationError("expression error: " + str(x), self.sourceref) from None

    def print_tree(self) -> None:
        def tree(expr: Any, level: int) -> str:
            indent = "  "*level
            if not isinstance(expr, Expression):
                return indent + str(expr) + "\n"
            if expr.unary:
                return indent + "{}{}".format(expr.operator, tree(expr.left, level+1))
            else:
                return indent + "{}".format(tree(expr.left, level+1)) + \
                       indent + str(expr.operator) + "\n" + \
                       indent + "{}".format(tree(expr.right, level + 1))
        print(tree(self, 0))


def datatype_of(assignmenttarget: AstNode, scope: Scope) -> DataType:
    # tries to determine the DataType of an assignment target node
    if isinstance(assignmenttarget, (VarDef, Dereference, Register)):
        return assignmenttarget.datatype
    elif isinstance(assignmenttarget, SymbolName):
        symdef = scope[assignmenttarget.name]
        if isinstance(symdef, VarDef):
            return symdef.datatype
    elif isinstance(assignmenttarget, TargetRegisters):
        if len(assignmenttarget.registers) == 1:
            return datatype_of(assignmenttarget.registers[0], scope)
    raise TypeError("cannot determine datatype", assignmenttarget)


def coerce_constant_value(datatype: DataType, value: Union[int, float, str],
                          sourceref: SourceRef=None) -> Tuple[bool, Union[int, float, str]]:
    # if we're a BYTE type, and the value is a single character, convert it to the numeric value
    def verify_bounds(value: Union[int, float, str]) -> None:
        # if the value is out of bounds, raise an overflow exception
        if isinstance(value, (int, float)):
            if datatype == DataType.BYTE and not (0 <= value <= 0xff):       # type: ignore
                raise OverflowError("value out of range for byte: " + str(value))
            if datatype == DataType.WORD and not (0 <= value <= 0xffff):        # type: ignore
                raise OverflowError("value out of range for word: " + str(value))
            if datatype == DataType.FLOAT and not (FLOAT_MAX_NEGATIVE <= value <= FLOAT_MAX_POSITIVE):      # type: ignore
                raise OverflowError("value out of range for float: " + str(value))
    if isinstance(value, str) and len(value) == 1 and (datatype.isnumeric() or datatype.isarray()):
        return True, char_to_bytevalue(value)
    # if we're an integer value and the passed value is float, truncate it (and give a warning)
    if datatype in (DataType.BYTE, DataType.WORD, DataType.MATRIX) and isinstance(value, float):
        frac = math.modf(value)
        if frac != 0:
            print_warning("float value truncated ({} to datatype {})".format(value, datatype.name), sourceref=sourceref)
            value = int(value)
            verify_bounds(value)
            return True, value
    if isinstance(value, (int, float)):
        verify_bounds(value)
    if isinstance(value, (Expression, SubCall)):
        return False, value
    elif datatype == DataType.WORD:
        if not isinstance(value, (int, float, str, Dereference, Register, SymbolName, AddressOf)):
            raise TypeError("cannot assign '{:s}' to {:s}".format(type(value).__name__, datatype.name.lower()), sourceref)
    elif datatype in (DataType.BYTE, DataType.WORD, DataType.FLOAT):
        if not isinstance(value, (int, float, Dereference, Register, SymbolName)):
            raise TypeError("cannot assign '{:s}' to {:s}".format(type(value).__name__, datatype.name.lower()), sourceref)
    return False, value


def process_expression(value: Any, scope: Scope, sourceref: SourceRef) -> Any:
    # process/simplify all expressions (constant folding etc)
    if isinstance(value, AstNode):
        must_be_constant = value.processed_expr_must_be_constant
    else:
        must_be_constant = False
    if must_be_constant:
        return process_constant_expression(value, sourceref, scope)
    else:
        return process_dynamic_expression(value, sourceref, scope)


def process_constant_expression(expr: Any, sourceref: SourceRef, symbolscope: Scope) -> Union[int, float, str, bool]:
    # the expression must result in a single (constant) value (int, float, whatever)
    if expr is None or isinstance(expr, (int, float, str, bool)):
        return expr
    elif isinstance(expr, LiteralValue):
        return expr.value
    elif isinstance(expr, SymbolName):
        try:
            value = symbolscope[expr.name]
            if isinstance(value, VarDef):
                if value.vartype == VarType.MEMORY:
                    raise ExpressionEvaluationError("can't take a memory value, must be a constant", expr.sourceref)
                value = value.value
            if isinstance(value, Expression):
                raise ExpressionEvaluationError("circular reference?", expr.sourceref)
            elif isinstance(value, (int, float, str, bool)):
                return value
            else:
                raise ExpressionEvaluationError("constant symbol required, not {}".format(value.__class__.__name__), expr.sourceref)
        except LookupError as x:
            raise ExpressionEvaluationError(str(x), expr.sourceref) from None
    elif isinstance(expr, AddressOf):
        assert isinstance(expr.name, SymbolName)
        try:
            value = symbolscope[expr.name.name]
            if isinstance(value, VarDef):
                if value.vartype == VarType.MEMORY:
                    return value.value
                if value.vartype == VarType.CONST:
                    raise ExpressionEvaluationError("can't take the address of a constant", expr.name.sourceref)
                raise ExpressionEvaluationError("address-of this {} isn't a compile-time constant"
                                                .format(value.__class__.__name__), expr.name.sourceref)
            else:
                raise ExpressionEvaluationError("constant address required, not {}"
                                                .format(value.__class__.__name__), expr.name.sourceref)
        except LookupError as x:
            raise ParseError(str(x), expr.sourceref) from None
    elif isinstance(expr, SubCall):
        if isinstance(expr.target, CallTarget):
            target = expr.target.target
            if isinstance(target, SymbolName):      # 'function(1,2,3)'
                funcname = target.name
                if funcname in math_functions or funcname in builtin_functions:
                    if isinstance(expr.target.target, SymbolName):
                        func_args = []
                        for a in (process_constant_expression(callarg.value, sourceref, symbolscope) for callarg in expr.arguments):
                            if isinstance(a, LiteralValue):
                                func_args.append(a.value)
                            else:
                                func_args.append(a)
                        func = math_functions.get(funcname, builtin_functions.get(funcname))
                        try:
                            return func(*func_args)
                        except Exception as x:
                            raise ExpressionEvaluationError(str(x), expr.sourceref)
                    else:
                        raise ParseError("symbol name required, not {}".format(expr.target.__class__.__name__), expr.sourceref)
                else:
                    raise ExpressionEvaluationError("can only use math- or builtin function", expr.sourceref)
            elif isinstance(target, Dereference):       # '[...](1,2,3)'
                raise ExpressionEvaluationError("dereferenced value call is not a constant value", expr.sourceref)
            elif type(target) is int:    # '64738()'
                raise ExpressionEvaluationError("immediate address call is not a constant value", expr.sourceref)
            else:
                raise NotImplementedError("weird call target", target)
        else:
            raise ParseError("function name required, not {}".format(expr.target.__class__.__name__), expr.sourceref)
    elif not isinstance(expr, Expression):
        raise ExpressionEvaluationError("constant value required, not {}".format(expr.__class__.__name__), expr.sourceref)
    if expr.unary:
        left_sourceref = expr.left.sourceref if isinstance(expr.left, AstNode) else sourceref
        expr.left = process_constant_expression(expr.left, left_sourceref, symbolscope)
        if isinstance(expr.left, (int, float)):
            try:
                if expr.operator == '-':
                    return -expr.left
                elif expr.operator == '~':
                    return ~expr.left       # type: ignore
                elif expr.operator in ("++", "--"):
                    raise ValueError("incr/decr should not be an expression")
                raise ValueError("invalid unary operator", expr.operator)
            except TypeError as x:
                raise ParseError(str(x), expr.sourceref) from None
        raise ValueError("invalid operand type for unary operator", expr.left, expr.operator)
    else:
        left_sourceref = expr.left.sourceref if isinstance(expr.left, AstNode) else sourceref
        expr.left = process_constant_expression(expr.left, left_sourceref, symbolscope)
        right_sourceref = expr.right.sourceref if isinstance(expr.right, AstNode) else sourceref
        expr.right = process_constant_expression(expr.right, right_sourceref, symbolscope)
        if isinstance(expr.left, (LiteralValue, SymbolName, int, float, str, bool)):
            if isinstance(expr.right, (LiteralValue, SymbolName, int, float, str, bool)):
                return expr.evaluate_primitive_constants(symbolscope)
            else:
                raise ExpressionEvaluationError("constant value required on right, not {}"
                                                .format(expr.right.__class__.__name__), right_sourceref)
        else:
            raise ExpressionEvaluationError("constant value required on left, not {}"
                                            .format(expr.left.__class__.__name__), left_sourceref)


def process_dynamic_expression(expr: Any, sourceref: SourceRef, symbolscope: Scope) -> Any:
    # constant-fold a dynamic expression
    if expr is None or isinstance(expr, (int, float, str, bool)):
        return expr
    elif isinstance(expr, LiteralValue):
        return expr.value
    elif isinstance(expr, SymbolName):
        try:
            return process_constant_expression(expr, sourceref, symbolscope)
        except ExpressionEvaluationError:
            return expr
    elif isinstance(expr, AddressOf):
        try:
            return process_constant_expression(expr, sourceref, symbolscope)
        except ExpressionEvaluationError:
            return expr
    elif isinstance(expr, SubCall):
        try:
            return process_constant_expression(expr, sourceref, symbolscope)
        except ExpressionEvaluationError:
            return expr
    elif isinstance(expr, Register):
        return expr
    elif isinstance(expr, Dereference):
        return expr
    elif not isinstance(expr, Expression):
        raise ParseError("expression required, not {}".format(expr.__class__.__name__), expr.sourceref)
    if expr.unary:
        left_sourceref = expr.left.sourceref if isinstance(expr.left, AstNode) else sourceref
        expr.left = process_dynamic_expression(expr.left, left_sourceref, symbolscope)
        try:
            return process_constant_expression(expr, sourceref, symbolscope)
        except ExpressionEvaluationError:
            return expr
    else:
        left_sourceref = expr.left.sourceref if isinstance(expr.left, AstNode) else sourceref
        expr.left = process_dynamic_expression(expr.left, left_sourceref, symbolscope)
        right_sourceref = expr.right.sourceref if isinstance(expr.right, AstNode) else sourceref
        expr.right = process_dynamic_expression(expr.right, right_sourceref, symbolscope)
        try:
            return process_constant_expression(expr, sourceref, symbolscope)
        except ExpressionEvaluationError:
            return expr


# ----------------- PLY parser definition follows ----------------------

def p_start(p):
    """
    start :  empty
          |  module_elements
    """
    if p[1]:
        scope = Scope(nodes=p[1], sourceref=_token_sref(p, 1))
        scope.name = "<" + p.lexer.source_filename + " global scope>"
        p[0] = Module(name=p.lexer.source_filename, scope=scope, sourceref=_token_sref(p, 1))
    else:
        scope = Scope(nodes=[], sourceref=_token_sref(p, 1))
        scope.name = "<" + p.lexer.source_filename + " global scope>"
        p[0] = Module(name=p.lexer.source_filename, scope=scope, sourceref=SourceRef(lexer.source_filename, 1, 1))


def p_module(p):
    """
    module_elements :  module_elt
                    |  module_elements  module_elt
    """
    if len(p) == 2:
        if p[1] is None:
            p[0] = []
        else:
            p[0] = [p[1]]
    else:
        if p[2] is None:
            p[0] = p[1]
        else:
            p[0] = p[1] + [p[2]]


def p_module_elt(p):
    """
    module_elt :  ENDL
               |  directive
               |  block
    """
    if p[1] != '\n':
        p[0] = p[1]


def p_directive(p):
    """
    directive :  DIRECTIVE  ENDL
              |  DIRECTIVE  directive_args  ENDL
    """
    if len(p) == 3:
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
                  |  BOOLEAN
    """
    p[0] = p[1]


def p_block_name_addr(p):
    """
    block :  BITINVERT  NAME  INTEGER  endl_opt  scope
    """
    p[0] = Block(name=p[2], address=p[3], scope=p[5], sourceref=_token_sref(p, 2))


def p_block_name(p):
    """
    block :  BITINVERT  NAME  endl_opt  scope
    """
    p[0] = Block(name=p[2], scope=p[4], sourceref=_token_sref(p, 2))


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
    p[0] = Scope(nodes=p[2] or [], sourceref=_token_sref(p, 1))


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
        p[0] = [] if p[1] in (None, '\n') else [p[1]]
    else:
        if p[2] in (None, '\n'):
            p[0] = p[1]
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
    if p[1] != '\n':
        p[0] = p[1]
    else:
        p[0] = None


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
    p[0] = VarDef(name=p[3], vartype=p[1], datatype=p[2], sourceref=_token_sref(p, 3))


def p_vardef_value(p):
    """
    vardef :  VARTYPE  type_opt  NAME  IS  expression
    """
    p[0] = VarDef(name=p[3], vartype=p[1], datatype=p[2], value=p[5], sourceref=_token_sref(p, 3))


def p_type_opt(p):
    """
    type_opt :  DATATYPE  '('  dimensions  ')'
             |  DATATYPE
             |  empty
    """
    if len(p) == 5:
        p[0] = DatatypeNode(name=p[1], dimensions=p[3], sourceref=_token_sref(p, 1))
    elif len(p) == 2 and p[1]:
        p[0] = DatatypeNode(name=p[1], sourceref=_token_sref(p, 1))


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
    tok = p.slice[-1]
    if tok.type == "CHARACTER":
        p[1] = char_to_bytevalue(p[1])     # character literals are converted to byte value.
    elif tok.type == "BOOLEAN":
        p[1] = int(p[1])    # boolean literals are converted to integer form (true=1, false=0).
    p[0] = LiteralValue(value=p[1], sourceref=_token_sref(p, 1))


def p_subroutine(p):
    """
    subroutine :  SUB  NAME  '('  sub_param_spec  ')'  RARROW  '('  sub_result_spec  ')'  subroutine_body  ENDL
    """
    body = p[10]
    if isinstance(body, Scope):
        p[0] = Subroutine(name=p[2], param_spec=p[4] or [], result_spec=p[8] or [], scope=body, sourceref=_token_sref(p, 1))
    elif type(body) is int:
        p[0] = Subroutine(name=p[2], param_spec=p[4] or [], result_spec=p[8] or [], address=body, sourceref=_token_sref(p, 1))
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
    p[0] = IncrDecr(target=p[1], operator=p[2], sourceref=_token_sref(p, 2))


def p_call_subroutine(p):
    """
    subroutine_call :  calltarget  preserveregs_opt  '('  call_arguments_opt  ')'
    """
    p[0] = SubCall(target=p[1], preserve_regs=p[2], arguments=p[4], sourceref=_token_sref(p, 3))


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
    p[0] = SymbolName(name=p[1], sourceref=_token_sref(p, 1))


def p_assignment(p):
    """
    assignment :  assignment_target  IS  expression
               |  assignment_target  IS  assignment
    """
    p[0] = Assignment(left=[p[1]], right=p[3], sourceref=_token_sref(p, 2))


def p_aug_assignment(p):
    """
    aug_assignment :  assignment_target  AUGASSIGN  expression
    """
    p[0] = AugAssignment(left=p[1], operator=p[2], right=p[3], sourceref=_token_sref(p, 2))


precedence = (
    ('left', '+', '-'),
    ('left', '*', '/', 'INTEGERDIVIDE'),
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
               |  expression  INTEGERDIVIDE  expression
               |  expression  LT  expression
               |  expression  GT  expression
               |  expression  LE  expression
               |  expression  GE  expression
               |  expression  EQUALS  expression
               |  expression  NOTEQUALS  expression
    """
    p[0] = Expression(left=p[1], operator=p[2], right=p[3], sourceref=_token_sref(p, 2))


def p_expression_uminus(p):
    """
    expression :  '-'  expression  %prec UNARY_MINUS
    """
    p[0] = Expression(left=p[2], operator=p[1], right=None, unary=True, sourceref=_token_sref(p, 1))


def p_expression_addressof(p):
    """
    expression :  BITAND  symbolname  %prec UNARY_ADDRESSOF
    """
    p[0] = AddressOf(name=p[2], sourceref=_token_sref(p, 1))


def p_unary_expression_bitinvert(p):
    """
    expression :  BITINVERT  expression
    """
    p[0] = Expression(left=p[2], operator=p[1], right=None, unary=True, sourceref=_token_sref(p, 1))


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
    if isinstance(p[1], TargetRegisters):
        # if the target registers is just a single register, use that instead
        if len(p[1].registers) == 1:
            assert isinstance(p[1].registers[0], Register)
            p[1] = p[1].registers[0]
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
    stack_state_str = '  '.join([symbol.type for symbol in parser.symstack][1:])
    print('\n[ERROR DEBUG: parser state={:d} stack: {} . {} ]'.format(parser.state, stack_state_str, p))
    if p:
        sref = SourceRef(p.lexer.source_filename, p.lineno, find_tok_column(p))
        if p.value in ("", "\n"):
            p.lexer.error_function(sref, "syntax error before end of line")
        else:
            p.lexer.error_function(sref, "syntax error before or at '{:.20s}'", str(p.value).rstrip())
    else:
        lexer.error_function(None, "syntax error at end of input", lexer.source_filename)


def _token_sref(p, token_idx):
    """ Returns the coordinates for the YaccProduction object 'p' indexed
        with 'token_idx'. The coordinate includes the 'lineno' and 'column', starting from 1.
    """
    last_cr = p.lexer.lexdata.rfind('\n', 0, p.lexpos(token_idx))
    if last_cr < 0:
        last_cr = -1
    chunk = p.lexer.lexdata[last_cr:p.lexpos(token_idx)]
    column = len(chunk.expandtabs())
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


def parse_file(filename: str, lexer_error_func=None) -> Module:
    lexer.error_function = lexer_error_func
    lexer.lineno = 1
    lexer.source_filename = filename
    tfilter = TokenFilter(lexer)
    with open(filename, "rU") as inf:
        sourcecode = inf.read()
    return parser.parse(input=sourcecode, tokenfunc=tfilter.token)
