"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the optimizer that applies various optimizations to the parse tree,
eliminates statements that have no effect, optimizes calculations etc.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import sys
from typing import List, no_type_check, Union, Any
from .plyparse import *
from .plylex import print_warning, print_bold, SourceRef
from .datatypes import DataType, VarType


class Optimizer:
    def __init__(self, mod: Module) -> None:
        self.num_warnings = 0
        self.module = mod
        self.optimizations_performed = False

    def optimize(self) -> None:
        self.num_warnings = 0
        self.optimizations_performed = True
        # keep optimizing as long as there were changes made
        while self.optimizations_performed:
            self.optimizations_performed = False
            self._optimize()
        # remaining optimizations that have to be done just once:
        self.remove_unused_subroutines()
        self.remove_empty_blocks()

    def _optimize(self) -> None:
        self.constant_folding()
        # @todo expression optimization: reduce expression nesting
        # @todo expression optimization: simplify logical expression when a term makes it always true or false
        self.create_aug_assignments()
        self.optimize_assignments()
        self.remove_superfluous_assignments()
        self.combine_assignments_into_multi()
        self.optimize_multiassigns()
        # @todo optimize some simple multiplications into shifts  (A*=8 -> A<<3)
        # @todo optimize addition with self into shift 1  (A+=A -> A<<=1)
        self.optimize_goto_compare_with_zero()
        self.join_incrdecrs()
        # @todo analyse for unreachable code and remove that (f.i. code after goto or return that has no label so can never be jumped to)

    def handle_internal_error(self, exc: Exception, msg: str="") -> None:
        out = sys.stdout
        if out.isatty():
            print("\x1b[1m", file=out)
        print("\nERROR: internal parser/optimizer error: ", exc, file=out)
        if msg:
            print("    Message:", msg, end="\n\n")
        if out.isatty():
            print("\x1b[0m", file=out, end="", flush=True)
        raise exc

    def constant_folding(self) -> None:
        for expression in self.module.all_nodes(Expression):
            if isinstance(expression, LiteralValue):
                continue
            try:
                evaluated = process_expression(expression)    # type: ignore
                if evaluated is not expression:
                    # replace the node with the newly evaluated result
                    parent = expression.parent
                    parent.replace_node(expression, evaluated)
                    self.optimizations_performed = True
            except ParseError:
                raise
            except Exception as x:
                self.handle_internal_error(x, "process_expressions of node {}".format(expression))

    def join_incrdecrs(self) -> None:
        for scope in self.module.all_nodes(Scope):
            incrdecrs = []      # type: List[IncrDecr]
            target = None
            for node in list(scope.nodes):
                if isinstance(node, IncrDecr):
                    if target is None:
                        target = node.target
                        incrdecrs.append(node)
                        continue
                    if self._same_target(target, node.target):
                        incrdecrs.append(node)
                        continue
                if len(incrdecrs) > 1:
                    # optimize...
                    replaced = False
                    total = 0
                    for i in incrdecrs:
                        if i.operator == "++":
                            total += i.howmuch
                        else:
                            total -= i.howmuch
                    if total == 0:
                        replaced = True
                        for x in incrdecrs:
                            scope.remove_node(x)
                    else:
                        is_float = False
                        if isinstance(target, SymbolName):
                            symdef = target.my_scope().lookup(target.name)
                            if isinstance(symdef, VarDef) and symdef.datatype == DataType.FLOAT:
                                is_float = True
                        elif isinstance(target, Dereference):
                            is_float = target.datatype == DataType.FLOAT
                        if is_float:
                            replaced = True
                            for x in incrdecrs[1:]:
                                scope.remove_node(x)
                            incrdecr = self._make_incrdecr(incrdecrs[0], target, abs(total), "++" if total >= 0 else "--")
                            scope.replace_node(incrdecrs[0], incrdecr)
                        elif 0 < total <= 255:
                            replaced = True
                            for x in incrdecrs[1:]:
                                scope.remove_node(x)
                            incrdecr = self._make_incrdecr(incrdecrs[0], target, total, "++")
                            scope.replace_node(incrdecrs[0], incrdecr)
                        elif -255 <= total < 0:
                            replaced = True
                            total = -total
                            for x in incrdecrs[1:]:
                                scope.remove_node(x)
                            incrdecr = self._make_incrdecr(incrdecrs[0], target, total, "--")
                            scope.replace_node(incrdecrs[0], incrdecr)
                    if replaced:
                        self.optimizations_performed = True
                        self.num_warnings += 1
                        print_warning("{}: merged a sequence of incr/decrs or augmented assignments".format(incrdecrs[0].sourceref))
                incrdecrs.clear()
                target = None
                if isinstance(node, IncrDecr):
                    incrdecrs.append(node)
                    target = node.target

    def _same_target(self, node1: Union[TargetRegisters, Register, SymbolName, Dereference],
                     node2: Union[TargetRegisters, Register, SymbolName, Dereference]) -> bool:
        if isinstance(node1, Register) and isinstance(node2, Register) and node1.name == node2.name:
            return True
        if isinstance(node1, SymbolName) and isinstance(node2, SymbolName) and node1.name == node2.name:
            return True
        if isinstance(node1, Dereference) and isinstance(node2, Dereference) and node1.operand == node2.operand:
            return True
        return False

    @no_type_check
    def create_aug_assignments(self) -> None:
        # create augmented assignments from regular assignment that only refers to the lvalue
        # A=A+10, A=10+A -> A+=10,  A=A*4, A=4*A -> A*=4,  etc
        for assignment in self.module.all_nodes(Assignment):
            if len(assignment.left.nodes) > 1:
                continue
            if not isinstance(assignment.right, ExpressionWithOperator) or assignment.right.unary:
                continue
            expr = assignment.right
            if expr.operator in ('-', '/', '//', '**', '<<', '>>', '&'):   # non-associative operators
                if isinstance(expr.right, (LiteralValue, SymbolName)) and self._same_target(assignment.left.nodes[0], expr.left):
                    num_val = expr.right.const_value()
                    operator = expr.operator + '='
                    aug_assign = self._make_aug_assign(assignment, assignment.left.nodes[0], num_val, operator)
                    assignment.my_scope().replace_node(assignment, aug_assign)
                    self.optimizations_performed = True
                continue
            if expr.operator not in ('+', '*', '|', '^'):  # associative operators
                continue
            if isinstance(expr.right, (LiteralValue, SymbolName)) and self._same_target(assignment.left.nodes[0], expr.left):
                num_val = expr.right.const_value()
                operator = expr.operator + '='
                aug_assign = self._make_aug_assign(assignment, assignment.left.nodes[0], num_val, operator)
                assignment.my_scope().replace_node(assignment, aug_assign)
                self.optimizations_performed = True
            elif isinstance(expr.left, (LiteralValue, SymbolName)) and self._same_target(assignment.left.nodes[0], expr.right):
                num_val = expr.left.const_value()
                operator = expr.operator + '='
                aug_assign = self._make_aug_assign(assignment, assignment.left.nodes[0], num_val, operator)
                assignment.my_scope().replace_node(assignment, aug_assign)
                self.optimizations_performed = True

    def remove_superfluous_assignments(self) -> None:
        # remove consecutive assignment statements to the same target, only keep the last value (only if its a constant!)
        # this is NOT done for memory mapped variables because these often represent a volatile register of some sort!
        for scope in self.module.all_nodes(Scope):
            prev_node = None    # type: AstNode
            for node in list(scope.nodes):
                if isinstance(node, Assignment) and isinstance(prev_node, Assignment):
                    if isinstance(node.right, (LiteralValue, Register)) and node.left.same_targets(prev_node.left):
                        if not node.left.has_memvalue():
                            scope.remove_node(prev_node)
                            self.optimizations_performed = True
                            self.num_warnings += 1
                            print_warning("{}: removed superfluous assignment".format(prev_node.sourceref))
                prev_node = node

    @no_type_check
    def optimize_assignments(self) -> None:
        # remove assignment statements that do nothing (A=A)
        # remove augmented assignments that have no effect (x+=0, x-=0, x/=1, x//=1, x*=1)
        # convert augmented assignments to simple incr/decr if possible (A+=10 =>  A++ by 10)
        # simplify some calculations (x*=0, x**=0) to simple constant value assignment
        # @todo remove or simplify logical aug assigns like A |= 0, A |= true, A |= false  (or perhaps turn them into byte values first?)
        for assignment in self.module.all_nodes():
            if isinstance(assignment, Assignment):
                if all(lv == assignment.right for lv in assignment.left.nodes):
                    assignment.my_scope().remove_node(assignment)
                    self.optimizations_performed = True
                    self.num_warnings += 1
                    print_warning("{}: removed statement that has no effect".format(assignment.sourceref))
            elif isinstance(assignment, AugAssignment):
                if isinstance(assignment.right, LiteralValue) and isinstance(assignment.right.value, (int, float)):
                    if assignment.right.value == 0:
                        if assignment.operator in ("+=", "-=", "|=", "<<=", ">>=", "^="):
                            self.num_warnings += 1
                            print_warning("{}: removed statement that has no effect".format(assignment.sourceref))
                            assignment.my_scope().remove_node(assignment)
                            self.optimizations_performed = True
                        elif assignment.operator == "*=":
                            self.num_warnings += 1
                            print_warning("{}: statement replaced by = 0".format(assignment.sourceref))
                            new_assignment = self._make_new_assignment(assignment, 0)
                            assignment.my_scope().replace_node(assignment, new_assignment)
                            self.optimizations_performed = True
                        elif assignment.operator == "**=":
                            self.num_warnings += 1
                            print_warning("{}: statement replaced by = 1".format(assignment.sourceref))
                            new_assignment = self._make_new_assignment(assignment, 1)
                            assignment.my_scope().replace_node(assignment, new_assignment)
                            self.optimizations_performed = True
                    if assignment.right.value >= 8 and assignment.operator in ("<<=", ">>="):
                        print("{}: shifting result is always zero".format(assignment.sourceref))
                        new_stmt = Assignment(sourceref=assignment.sourceref)
                        new_stmt.nodes.append(AssignmentTargets(nodes=[assignment.left], sourceref=assignment.sourceref))
                        new_stmt.nodes.append(LiteralValue(value=0, sourceref=assignment.sourceref))
                        assignment.my_scope().replace_node(assignment, new_stmt)
                        self.optimizations_performed = True
                    if assignment.operator in ("+=", "-=") and 0 < assignment.right.value < 256:
                        howmuch = assignment.right
                        if howmuch.value not in (0, 1):
                            _, howmuch = coerce_constant_value(datatype_of(assignment.left, assignment.my_scope()),
                                                               howmuch, assignment.sourceref)
                        new_stmt = IncrDecr(operator="++" if assignment.operator == "+=" else "--",
                                            howmuch=howmuch.value, sourceref=assignment.sourceref)
                        new_stmt.target = assignment.left
                        new_stmt.target.parent = new_stmt
                        assignment.my_scope().replace_node(assignment, new_stmt)
                        self.optimizations_performed = True
                    if assignment.right.value == 1 and assignment.operator in ("/=", "//=", "*="):
                        self.num_warnings += 1
                        print_warning("{}: removed statement that has no effect".format(assignment.sourceref))
                        assignment.my_scope().remove_node(assignment)
                        self.optimizations_performed = True

    @no_type_check
    def _make_new_assignment(self, old_aug_assignment: AugAssignment, constantvalue: int) -> Assignment:
        new_assignment = Assignment(sourceref=old_aug_assignment.sourceref)
        new_assignment.parent = old_aug_assignment.parent
        left = AssignmentTargets(nodes=[old_aug_assignment.left], sourceref=old_aug_assignment.sourceref)
        left.parent = new_assignment
        new_assignment.nodes.append(left)
        value = LiteralValue(value=constantvalue, sourceref=old_aug_assignment.sourceref)
        value.parent = new_assignment
        new_assignment.nodes.append(value)
        return new_assignment

    @no_type_check
    def _make_aug_assign(self, old_assign: Assignment, target: Union[TargetRegisters, Register, SymbolName, Dereference],
                         value: Union[int, float], operator: str) -> AugAssignment:
        assert isinstance(target, (TargetRegisters, Register, SymbolName, Dereference))
        a = AugAssignment(operator=operator, sourceref=old_assign.sourceref)
        a.nodes.append(target)
        target.parent = a
        lv = LiteralValue(value=value, sourceref=old_assign.sourceref)
        a.nodes.append(lv)
        lv.parent = a
        a.parent = old_assign.parent
        return a

    @no_type_check
    def _make_incrdecr(self, old_stmt: AstNode, target: Union[TargetRegisters, Register, SymbolName, Dereference],
                       howmuch: Union[int, float], operator: str) -> IncrDecr:
        assert isinstance(target, (TargetRegisters, Register, SymbolName, Dereference))
        a = IncrDecr(operator=operator, howmuch=howmuch, sourceref=old_stmt.sourceref)
        a.nodes.append(target)
        target.parent = a
        a.parent = old_stmt.parent
        return a

    def combine_assignments_into_multi(self) -> None:
        # fold multiple consecutive assignments with the same rvalue into one multi-assignment
        for scope in self.module.all_nodes(Scope):
            rvalue = None
            assignments = []    # type: List[Assignment]
            for stmt in list(scope.nodes):
                if isinstance(stmt, Assignment):
                    if assignments:
                        if stmt.right == rvalue:
                            assignments.append(stmt)
                            continue
                        elif len(assignments) > 1:
                            # replace the first assignment by a multi-assign with all the others
                            for assignment in assignments[1:]:
                                print("{}: joined with previous assignment".format(assignment.sourceref))
                                assignments[0].left.nodes.extend(assignment.left.nodes)
                                scope.remove_node(assignment)
                                self.optimizations_performed = True
                            rvalue = None
                            assignments.clear()
                    else:
                        rvalue = stmt.right
                        assignments.append(stmt)
                else:
                    rvalue = None
                    assignments.clear()

    @no_type_check
    def optimize_multiassigns(self) -> None:
        # optimize multi-assign statements (remove duplicate targets, optimize order)
        for assignment in self.module.all_nodes(Assignment):
            if len(assignment.left.nodes) > 1:
                # remove duplicates
                lvalues = set(assignment.left.nodes)
                if len(lvalues) != len(assignment.left.nodes):
                    print("{}: removed duplicate assignment targets".format(assignment.sourceref))
                    # @todo change order: first registers, then zp addresses, then non-zp addresses, then the rest (if any)
                    assignment.left.nodes = list(lvalues)
                    self.optimizations_performed = True

    @no_type_check
    def remove_unused_subroutines(self) -> None:
        # some symbols are used by the emitted assembly code from the code generator,
        # and should never be removed or the assembler will fail
        never_remove = {"c64.FREADUY", "c64.FTOMEMXY", "c64.FADD", "c64.FSUB",
                        "c64flt.GIVUAYF", "c64flt.copy_mflt", "c64flt.float_add_one", "c64flt.float_sub_one",
                        "c64flt.float_add_SW1_to_XY", "c64flt.float_sub_SW1_from_XY"}
        num_discarded = 0
        for sub in self.module.all_nodes(Subroutine):
            usages = self.module.subroutine_usage[(sub.parent.name, sub.name)]
            if not usages and sub.parent.name + '.' + sub.name not in never_remove:
                sub.parent.remove_node(sub)
                num_discarded += 1
        # if num_discarded:
        #     print("discarded {:d} unused subroutines".format(num_discarded))

    @no_type_check
    def optimize_goto_compare_with_zero(self) -> None:
        # a conditional goto that compares a value with zero will be simplified
        # the comparison operator and rvalue (0) will be removed and the if-status changed accordingly
        for goto in self.module.all_nodes(Goto):
            if isinstance(goto.condition, Expression):
                pass  # @todo optimize goto conditionals
                # if cond and isinstance(cond.rvalue, (int, float)) and cond.rvalue.value == 0:
                #     simplified = False
                #     if cond.ifstatus in ("true", "ne"):
                #         if cond.comparison_op == "==":
                #             # if_true something == 0   ->  if_not something
                #             cond.ifstatus = "not"
                #             cond.comparison_op, cond.rvalue = "", None
                #             simplified = True
                #         elif cond.comparison_op == "!=":
                #             # if_true something != 0  -> if_true something
                #             cond.comparison_op, cond.rvalue = "", None
                #             simplified = True
                #     elif cond.ifstatus in ("not", "eq"):
                #         if cond.comparison_op == "==":
                #             # if_not something == 0   ->  if_true something
                #             cond.ifstatus = "true"
                #             cond.comparison_op, cond.rvalue = "", None
                #             simplified = True
                #         elif cond.comparison_op == "!=":
                #             # if_not something != 0  -> if_not something
                #             cond.comparison_op, cond.rvalue = "", None
                #             simplified = True
                #     if simplified:
                #         print("{}: simplified comparison with zero".format(stmt.sourceref))

    def remove_empty_blocks(self) -> None:
        # remove blocks without name and without address, or that are empty
        for node in self.module.all_nodes():
            if isinstance(node, (Subroutine, Block)):
                if not node.scope:
                    continue
                if all(isinstance(n, Directive) for n in node.scope.nodes):
                    empty = True
                    for n in node.scope.nodes:
                        empty = empty and n.name not in {"asmbinary", "asminclude"}
                    if empty:
                        self.num_warnings += 1
                        print_warning("ignoring empty block or subroutine", node.sourceref)
                        assert isinstance(node.parent, (Block, Module))
                        node.my_scope().nodes.remove(node)
            if isinstance(node, Block):
                if not node.name and node.address is None:
                    self.num_warnings += 1
                    print_warning("ignoring block without name and address", node.sourceref)
                    assert isinstance(node.parent, Module)
                    node.my_scope().nodes.remove(node)


def process_expression(expr: Expression) -> Expression:
    # process/simplify all expressions (constant folding etc)
    result = None   # type: Expression
    if expr.is_compile_constant() or isinstance(expr, ExpressionWithOperator) and expr.must_be_constant:
        result = _process_constant_expression(expr, expr.sourceref)
    else:
        result = _process_dynamic_expression(expr, expr.sourceref)
    result.parent = expr.parent
    return result


def _process_constant_expression(expr: Expression, sourceref: SourceRef) -> LiteralValue:
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
                for a in (_process_constant_expression(callarg.value, sourceref) for callarg in list(expr.arguments.nodes)):
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
            expr.left = _process_constant_expression(expr.left, left_sourceref)
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
            expr.left = _process_constant_expression(expr.left, left_sourceref)
            expr.left.parent = expr
            right_sourceref = expr.right.sourceref if isinstance(expr.right, AstNode) else sourceref
            expr.right = _process_constant_expression(expr.right, right_sourceref)
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


def _process_dynamic_expression(expr: Expression, sourceref: SourceRef) -> Expression:
    # constant-fold a dynamic expression
    if isinstance(expr, LiteralValue):
        return expr
    if expr.is_compile_constant():
        return LiteralValue(value=expr.const_value(), sourceref=sourceref)  # type: ignore
    elif isinstance(expr, SymbolName):
        if expr.is_compile_constant():
            try:
                return _process_constant_expression(expr, sourceref)
            except ExpressionEvaluationError:
                pass
        return expr
    elif isinstance(expr, AddressOf):
        if expr.is_compile_constant():
            try:
                return _process_constant_expression(expr, sourceref)
            except ExpressionEvaluationError:
                pass
        return expr
    elif isinstance(expr, SubCall):
        try:
            return _process_constant_expression(expr, sourceref)
        except ExpressionEvaluationError:
            if isinstance(expr.target, SymbolName):
                check_symbol_definition(expr.target.name, expr.my_scope(), expr.target.sourceref)
            return expr
    elif isinstance(expr, (Register, Dereference)):
        return expr
    elif isinstance(expr, ExpressionWithOperator):
        if expr.unary:
            left_sourceref = expr.left.sourceref if isinstance(expr.left, AstNode) else sourceref
            expr.left = _process_dynamic_expression(expr.left, left_sourceref)
            expr.left.parent = expr
            if expr.is_compile_constant():
                try:
                    return _process_constant_expression(expr, sourceref)
                except ExpressionEvaluationError:
                    pass
            return expr
        else:
            left_sourceref = expr.left.sourceref if isinstance(expr.left, AstNode) else sourceref
            expr.left = _process_dynamic_expression(expr.left, left_sourceref)
            expr.left.parent = expr
            right_sourceref = expr.right.sourceref if isinstance(expr.right, AstNode) else sourceref
            expr.right = _process_dynamic_expression(expr.right, right_sourceref)
            expr.right.parent = expr
            if expr.is_compile_constant():
                try:
                    return _process_constant_expression(expr, sourceref)
                except ExpressionEvaluationError:
                    pass
            return expr
    else:
        raise ParseError("expression required, not {}".format(expr.__class__.__name__), expr.sourceref)


def optimize(mod: Module) -> None:
    opt = Optimizer(mod)
    opt.optimize()
    if opt.num_warnings:
        print_bold("There are {:d} optimization warnings.".format(opt.num_warnings))
