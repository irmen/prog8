"""
Programming Language for 6502/6510 microprocessors
These are the Abstract Syntax Tree node classes that form the Parse Tree.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

from .symbols import SourceRef, SymbolTable, SubroutineDef, SymbolDefinition, SymbolError, DataType, \
    STRING_DATATYPES, REGISTER_SYMBOLS, REGISTER_BYTES, REGISTER_SBITS, check_value_in_range
from typing import Dict, Set, List, Tuple, Optional, Union, Generator, Any

__all__ = ["_AstNode", "Block", "Value", "IndirectValue", "IntegerValue", "FloatValue", "StringValue", "RegisterValue",
           "MemMappedValue", "Comment", "Label", "AssignmentStmt", "AugmentedAssignmentStmt", "ReturnStmt",
           "InplaceIncrStmt", "InplaceDecrStmt", "IfCondition", "CallStmt", "InlineAsm", "BreakpointStmt"]


class _AstNode:
    def __init__(self, sourceref: SourceRef) -> None:
        self.sourceref = sourceref.copy()

    @property
    def lineref(self) -> str:
        return "src l. " + str(self.sourceref.line)


class Block(_AstNode):
    _unnamed_block_labels = {}  # type: Dict[Block, str]

    def __init__(self, name: str, sourceref: SourceRef, parent_scope: SymbolTable) -> None:
        super().__init__(sourceref)
        self.address = 0
        self.name = name
        self.statements = []  # type: List[_AstNode]
        self.symbols = SymbolTable(name, parent_scope, self)

    @property
    def ignore(self) -> bool:
        return not self.name and not self.address

    @property
    def label_names(self) -> Set[str]:
        return {symbol.name for symbol in self.symbols.iter_labels()}

    @property
    def label(self) -> str:
        if self.name:
            return self.name
        if self in self._unnamed_block_labels:
            return self._unnamed_block_labels[self]
        label = "il65_block_{:d}".format(len(self._unnamed_block_labels))
        self._unnamed_block_labels[self] = label
        return label

    def lookup(self, dottedname: str) -> Tuple[Optional['Block'], Optional[Union[SymbolDefinition, SymbolTable]]]:
        # Searches a name in the current block or globally, if the name is scoped (=contains a '.').
        # Does NOT utilize a symbol table from a preprocessing parse phase, only looks in the current.
        try:
            scope, result = self.symbols.lookup(dottedname)
            return scope.owning_block, result
        except (SymbolError, LookupError):
            return None, None

    def all_statements(self) -> Generator[Tuple['Block', Optional[SubroutineDef], _AstNode], None, None]:
        for stmt in self.statements:
            yield self, None, stmt
        for sub in self.symbols.iter_subroutines(True):
            for stmt in sub.sub_block.statements:
                yield sub.sub_block, sub, stmt


class Value(_AstNode):
    def __init__(self, datatype: DataType, sourceref: SourceRef, name: str = None, constant: bool = False) -> None:
        super().__init__(sourceref)
        self.datatype = datatype
        self.name = name
        self.constant = constant

    def assignable_from(self, other: 'Value') -> Tuple[bool, str]:
        if self.constant:
            return False, "cannot assign to a constant"
        return False, "incompatible value for assignment"


class IndirectValue(Value):
    # only constant integers, memmapped and register values are wrapped in this.
    def __init__(self, value: Value, type_modifier: DataType, sourceref: SourceRef) -> None:
        assert type_modifier
        super().__init__(type_modifier, sourceref, value.name, False)
        self.value = value

    def __str__(self):
        return "<IndirectValue {} itype={} name={}>".format(self.value, self.datatype, self.name)

    def __hash__(self):
        return hash((self.datatype, self.name, self.value))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, IndirectValue):
            return NotImplemented
        elif self is other:
            return True
        else:
            vvo = getattr(other.value, "value", getattr(other.value, "address", None))
            vvs = getattr(self.value, "value", getattr(self.value, "address", None))
            return (other.datatype, other.name, other.value.name, other.value.datatype, other.value.constant, vvo) == \
                   (self.datatype, self.name, self.value.name, self.value.datatype, self.value.constant, vvs)

    def assignable_from(self, other: Value) -> Tuple[bool, str]:
        if self.constant:
            return False, "cannot assign to a constant"
        if self.datatype == DataType.BYTE:
            if other.datatype == DataType.BYTE:
                return True, ""
        if self.datatype == DataType.WORD:
            if other.datatype in {DataType.BYTE, DataType.WORD} | STRING_DATATYPES:
                return True, ""
        if self.datatype == DataType.FLOAT:
            if other.datatype in {DataType.BYTE, DataType.WORD, DataType.FLOAT}:
                return True, ""
        if isinstance(other, (IntegerValue, FloatValue, StringValue)):
            rangefault = check_value_in_range(self.datatype, "", 1, other.value)
            if rangefault:
                return False, rangefault
            return True, ""
        return False, "incompatible value for indirect assignment (need byte, word, float or string)"


class IntegerValue(Value):
    def __init__(self, value: Optional[int], sourceref: SourceRef, *, datatype: DataType = None, name: str = None) -> None:
        if type(value) is int:
            if datatype is None:
                if 0 <= value < 0x100:
                    datatype = DataType.BYTE
                elif value < 0x10000:
                    datatype = DataType.WORD
                else:
                    raise OverflowError("value too big: ${:x}".format(value))
            else:
                faultreason = check_value_in_range(datatype, "", 1, value)
                if faultreason:
                    raise OverflowError(faultreason)
            super().__init__(datatype, sourceref, name, True)
            self.value = value
        elif value is None:
            if not name:
                raise ValueError("when integer value is not given, the name symbol should be speicified")
            super().__init__(datatype, sourceref, name, True)
            self.value = None
        else:
            raise TypeError("invalid data type")

    def __hash__(self):
        return hash((self.datatype, self.value, self.name))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, IntegerValue):
            return NotImplemented
        elif self is other:
            return True
        else:
            return (other.datatype, other.value, other.name) == (self.datatype, self.value, self.name)

    def __str__(self):
        return "<IntegerValue {} name={}>".format(self.value, self.name)


class FloatValue(Value):
    def __init__(self, value: float, sourceref: SourceRef, name: str = None) -> None:
        if type(value) is float:
            super().__init__(DataType.FLOAT, sourceref, name, True)
            self.value = value
        else:
            raise TypeError("invalid data type")

    def __hash__(self):
        return hash((self.datatype, self.value, self.name))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, FloatValue):
            return NotImplemented
        elif self is other:
            return True
        else:
            return (other.datatype, other.value, other.name) == (self.datatype, self.value, self.name)

    def __str__(self):
        return "<FloatValue {} name={}>".format(self.value, self.name)


class StringValue(Value):
    def __init__(self, value: str, sourceref: SourceRef, name: str = None, constant: bool = False) -> None:
        super().__init__(DataType.STRING, sourceref, name, constant)
        self.value = value

    def __hash__(self):
        return hash((self.datatype, self.value, self.name, self.constant))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, StringValue):
            return NotImplemented
        elif self is other:
            return True
        else:
            return (other.datatype, other.value, other.name, other.constant) == (self.datatype, self.value, self.name, self.constant)

    def __str__(self):
        return "<StringValue {!r:s} name={} constant={}>".format(self.value, self.name, self.constant)


class RegisterValue(Value):
    def __init__(self, register: str, datatype: DataType, sourceref: SourceRef, name: str = None) -> None:
        assert datatype in (DataType.BYTE, DataType.WORD)
        assert register in REGISTER_SYMBOLS
        super().__init__(datatype, sourceref, name, False)
        self.register = register

    def __hash__(self):
        return hash((self.datatype, self.register, self.name))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, RegisterValue):
            return NotImplemented
        elif self is other:
            return True
        else:
            return (other.datatype, other.register, other.name) == (self.datatype, self.register, self.name)

    def __str__(self):
        return "<RegisterValue {:s} type {:s} name={}>".format(self.register, self.datatype, self.name)

    def assignable_from(self, other: Value) -> Tuple[bool, str]:
        if isinstance(other, IndirectValue):
            if self.datatype == DataType.BYTE:
                if other.datatype == DataType.BYTE:
                    return True, ""
                return False, "(unsigned) byte required"
            if self.datatype == DataType.WORD:
                if other.datatype in (DataType.BYTE, DataType.WORD):
                    return True, ""
                return False, "(unsigned) byte required"
            return False, "incompatible indirect value for register assignment"
        if self.register in ("SC", "SI"):
            if isinstance(other, IntegerValue) and other.value in (0, 1):
                return True, ""
            return False, "can only assign an integer constant value of 0 or 1 to SC and SI"
        if self.constant:
            return False, "cannot assign to a constant"
        if isinstance(other, RegisterValue):
            if other.register in {"SI", "SC", "SZ"}:
                return False, "cannot explicitly assign from a status bit register alias"
            if len(self.register) < len(other.register):
                return False, "register size mismatch"
        if isinstance(other, StringValue) and self.register in REGISTER_BYTES | REGISTER_SBITS:
            return False, "string address requires 16 bits combined register"
        if isinstance(other, IntegerValue):
            if other.value is not None:
                range_error = check_value_in_range(self.datatype, self.register, 1, other.value)
                if range_error:
                    return False, range_error
                return True, ""
            if self.datatype == DataType.WORD:
                return True, ""
            return False, "cannot assign address to single register"
        if isinstance(other, FloatValue):
            range_error = check_value_in_range(self.datatype, self.register, 1, other.value)
            if range_error:
                return False, range_error
            return True, ""
        if self.datatype == DataType.BYTE:
            if other.datatype != DataType.BYTE:
                return False, "(unsigned) byte required"
            return True, ""
        if self.datatype == DataType.WORD:
            if other.datatype in (DataType.BYTE, DataType.WORD) or other.datatype in STRING_DATATYPES:
                return True, ""
            return False, "(unsigned) byte, word or string required"
        return False, "incompatible value for register assignment"


class MemMappedValue(Value):
    def __init__(self, address: Optional[int], datatype: DataType, length: int,
                 sourceref: SourceRef, name: str = None, constant: bool = False) -> None:
        super().__init__(datatype, sourceref, name, constant)
        self.address = address
        self.length = length
        assert address is None or type(address) is int

    def __hash__(self):
        return hash((self.datatype, self.address, self.length, self.name, self.constant))

    def __eq__(self, other: Any) -> bool:
        if not isinstance(other, MemMappedValue):
            return NotImplemented
        elif self is other:
            return True
        else:
            return (other.datatype, other.address, other.length, other.name, other.constant) == \
                   (self.datatype, self.address, self.length, self.name, self.constant)

    def __str__(self):
        addr = "" if self.address is None else "${:04x}".format(self.address)
        return "<MemMappedValue {:s} type={:s} #={:d} name={} constant={}>" \
            .format(addr, self.datatype, self.length, self.name, self.constant)

    def assignable_from(self, other: Value) -> Tuple[bool, str]:
        if self.constant:
            return False, "cannot assign to a constant"
        if isinstance(other, IndirectValue):
            return False, "can not yet assign memory mapped value from indirect value"  # @todo indirect v assign
        if self.datatype == DataType.BYTE:
            if isinstance(other, (IntegerValue, RegisterValue, MemMappedValue)):
                if other.datatype == DataType.BYTE:
                    return True, ""
                return False, "(unsigned) byte required"
            elif isinstance(other, FloatValue):
                range_error = check_value_in_range(self.datatype, "", 1, other.value)
                if range_error:
                    return False, range_error
                return True, ""
            else:
                return False, "(unsigned) byte required"
        elif self.datatype in (DataType.WORD, DataType.FLOAT):
            if isinstance(other, (IntegerValue, FloatValue)):
                range_error = check_value_in_range(self.datatype, "", 1, other.value)
                if range_error:
                    return False, range_error
                return True, ""
            elif isinstance(other, (RegisterValue, MemMappedValue)):
                if other.datatype in (DataType.BYTE, DataType.WORD, DataType.FLOAT):
                    return True, ""
                else:
                    return False, "byte or word or float required"
            elif isinstance(other, StringValue):
                if self.datatype == DataType.WORD:
                    return True, ""
                return False, "string address requires 16 bits (a word)"
            if self.datatype == DataType.BYTE:
                return False, "(unsigned) byte required"
            if self.datatype == DataType.WORD:
                return False, "(unsigned) word required"
        return False, "incompatible value for assignment"


class Comment(_AstNode):
    def __init__(self, text: str, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.text = text


class Label(_AstNode):
    def __init__(self, name: str, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.name = name


class AssignmentStmt(_AstNode):
    def __init__(self, leftvalues: List[Value], right: Value, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.leftvalues = leftvalues
        self.right = right

    def __str__(self):
        return "<Assign {:s} to {:s}>".format(str(self.right), ",".join(str(lv) for lv in self.leftvalues))

    _immediate_string_vars = {}  # type: Dict[str, Tuple[str, str]]

    def desugar_immediate_string(self, containing_block: Block) -> None:
        if self.right.name or not isinstance(self.right, StringValue):
            return
        if self.right.value in self._immediate_string_vars:
            blockname, stringvar_name = self._immediate_string_vars[self.right.value]
            if blockname:
                self.right.name = blockname + '.' + stringvar_name
            else:
                self.right.name = stringvar_name
        else:
            stringvar_name = "il65_str_{:d}".format(id(self))
            value = self.right.value
            containing_block.symbols.define_variable(stringvar_name, self.sourceref, DataType.STRING, value=value)
            self.right.name = stringvar_name
            self._immediate_string_vars[self.right.value] = (containing_block.name, stringvar_name)

    def remove_identity_lvalues(self) -> None:
        for lv in self.leftvalues:
            if lv == self.right:
                print("{}: removed identity assignment".format(self.sourceref))
        remaining_leftvalues = [lv for lv in self.leftvalues if lv != self.right]
        self.leftvalues = remaining_leftvalues

    def is_identity(self) -> bool:
        return all(lv == self.right for lv in self.leftvalues)


class AugmentedAssignmentStmt(AssignmentStmt):
    SUPPORTED_OPERATORS = {"+=", "-=", "&=", "|=", "^=", ">>=", "<<="}

    # full set: {"+=", "-=", "*=", "/=", "%=", "//=", "**=", "&=", "|=", "^=", ">>=", "<<="}

    def __init__(self, left: Value, operator: str, right: Value, sourceref: SourceRef) -> None:
        assert operator in self.SUPPORTED_OPERATORS
        super().__init__([left], right, sourceref)
        self.operator = operator

    def __str__(self):
        return "<AugAssign {:s} {:s} {:s}>".format(str(self.leftvalues[0]), self.operator, str(self.right))


class ReturnStmt(_AstNode):
    def __init__(self, sourceref: SourceRef, a: Optional[Value] = None,
                 x: Optional[Value] = None,
                 y: Optional[Value] = None) -> None:
        super().__init__(sourceref)
        self.a = a
        self.x = x
        self.y = y


class InplaceIncrStmt(_AstNode):
    def __init__(self, what: Value, howmuch: Union[int, float], sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        assert howmuch > 0
        self.what = what
        self.howmuch = howmuch


class InplaceDecrStmt(_AstNode):
    def __init__(self, what: Value, howmuch: Union[int, float], sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        assert howmuch > 0
        self.what = what
        self.howmuch = howmuch


class IfCondition(_AstNode):
    SWAPPED_OPERATOR = {"==": "==",
                        "!=": "!=",
                        "<=": ">=",
                        ">=": "<=",
                        "<": ">",
                        ">": "<"}
    IF_STATUSES = {"cc", "cs", "vc", "vs", "eq", "ne", "true", "not", "zero", "pos", "neg", "lt", "gt", "le", "ge"}

    def __init__(self, ifstatus: str, leftvalue: Optional[Value],
                 operator: str, rightvalue: Optional[Value], sourceref: SourceRef) -> None:
        assert ifstatus in self.IF_STATUSES
        assert operator in (None, "") or operator in self.SWAPPED_OPERATOR
        if operator:
            assert ifstatus in ("true", "not", "zero")
        super().__init__(sourceref)
        self.ifstatus = ifstatus
        self.lvalue = leftvalue
        self.comparison_op = operator
        self.rvalue = rightvalue

    def __str__(self):
        return "<IfCondition if_{:s} {} {:s} {}>".format(self.ifstatus, self.lvalue, self.comparison_op, self.rvalue)

    def make_if_true(self) -> bool:
        # makes a condition of the form if_not a < b  into: if a > b (gets rid of the not)
        # returns whether the change was made or not
        if self.ifstatus == "not" and self.comparison_op:
            self.ifstatus = "true"
            self.comparison_op = self.SWAPPED_OPERATOR[self.comparison_op]
            return True
        return False

    def swap(self) -> Tuple[Value, str, Value]:
        self.lvalue, self.comparison_op, self.rvalue = self.rvalue, self.SWAPPED_OPERATOR[self.comparison_op], self.lvalue
        return self.lvalue, self.comparison_op, self.rvalue


class CallStmt(_AstNode):
    def __init__(self, sourceref: SourceRef, target: Optional[Value] = None, *,
                 address: Optional[int] = None, arguments: List[Tuple[str, Any]] = None,
                 outputs: List[Tuple[str, Value]] = None, is_goto: bool = False,
                 preserve_regs: bool = True, condition: IfCondition = None) -> None:
        if not is_goto:
            assert condition is None
        super().__init__(sourceref)
        self.target = target
        self.address = address
        self.arguments = arguments
        self.outputvars = outputs
        self.is_goto = is_goto
        self.condition = condition
        self.preserve_regs = preserve_regs
        self.desugared_call_arguments = []  # type: List[AssignmentStmt]
        self.desugared_output_assignments = []  # type: List[AssignmentStmt]


class InlineAsm(_AstNode):
    def __init__(self, asmlines: List[str], sourceref: SourceRef) -> None:
        super().__init__(sourceref)
        self.asmlines = asmlines


class BreakpointStmt(_AstNode):
    def __init__(self, sourceref: SourceRef) -> None:
        super().__init__(sourceref)
