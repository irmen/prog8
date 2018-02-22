"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the optimizer that applies various optimizations to the parse tree,
eliminates statements that have no effect, optimizes calculations etc.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from typing import List, no_type_check, Union
from .datatypes import DataType, VarType
from .plyparse import *
from .plylex import print_warning, print_bold
from .constantfold import ConstantFold


class Optimizer:
    def __init__(self, mod: Module) -> None:
        self.num_warnings = 0
        self.module = mod
        self.optimizations_performed = False
        self.constant_folder = ConstantFold(self.module)

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
        self.constant_folder.fold_constants(True)    # perform constant folding and simple expression optimization
        # @todo expression optimization: reduce expression nesting / flattening of parenthesis
        # @todo expression optimization: simplify logical expression when a term makes it always true or false
        # @todo expression optimization: optimize some simple multiplications into shifts  (A*=8 -> A<<3)
        self.create_aug_assignments()
        self.optimize_assignments()
        self.remove_superfluous_assignments()
        # @todo optimize addition with self into shift 1  (A+=A -> A<<=1)
        self.optimize_goto_compare_with_zero()
        self.join_incrdecrs()
        # @todo remove gotos with conditions that are always false
        # @todo remove loops with conditions that are always empty/false
        # @todo analyse for unreachable code and remove that (f.i. code after goto or return that has no label so can never be jumped to)

    def join_incrdecrs(self) -> None:
        def combine(incrdecrs: List[IncrDecr], scope: Scope) -> None:
            # combine the separate incrdecrs
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
                    is_float = isinstance(symdef, VarDef) and symdef.datatype == DataType.FLOAT
                elif isinstance(target, Dereference):
                    is_float = target.datatype == DataType.FLOAT
                if is_float or -255 <= total <= 255:
                    replaced = True
                    for x in incrdecrs[1:]:
                        scope.remove_node(x)
                    incrdecr = self._make_incrdecr(incrdecrs[0], target, abs(total), "++" if total >= 0 else "--")
                    scope.replace_node(incrdecrs[0], incrdecr)
                else:
                    # total is > 255 or < -255, make an augmented assignment out of it instead of an incrdecr
                    aug_assign = AugAssignment(operator="-=" if total < 0 else "+=", sourceref=incrdecrs[0].sourceref)  # type: ignore
                    left = incrdecrs[0].target
                    right = LiteralValue(value=abs(total), sourceref=incrdecrs[0].sourceref)  # type: ignore
                    left.parent = aug_assign
                    right.parent = aug_assign
                    aug_assign.nodes.append(left)
                    aug_assign.nodes.append(right)
                    aug_assign.mark_lhs()
                    replaced = True
                    for x in incrdecrs[1:]:
                        scope.remove_node(x)
                    scope.replace_node(incrdecrs[0], aug_assign)
            if replaced:
                self.optimizations_performed = True
                self.num_warnings += 1
                print_warning("{}: merged a sequence of incr/decrs or augmented assignments".format(incrdecrs[0].sourceref))

        for scope in self.module.all_nodes(Scope):
            target = None
            incrdecrs = []  # type: List[IncrDecr]
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
                    combine(incrdecrs, scope)   # type: ignore
                incrdecrs.clear()
                target = None
                if isinstance(node, IncrDecr):
                    # it was an incrdecr with a different target than what we had gathered so far.
                    if target is None:
                        target = node.target
                        incrdecrs.append(node)
            if len(incrdecrs) > 1:
                # combine remaining incrdecrs at the bottom of the block
                combine(incrdecrs, scope)   # type: ignore

    def _same_target(self, node1: Union[Register, SymbolName, Dereference],
                     node2: Union[Register, SymbolName, Dereference]) -> bool:
        if isinstance(node1, Register) and isinstance(node2, Register) and node1.name == node2.name:
            return True
        if isinstance(node1, SymbolName) and isinstance(node2, SymbolName) and node1.name == node2.name:
            return True
        if isinstance(node1, Dereference) and isinstance(node2, Dereference):
            if type(node1.operand) is not type(node2.operand):
                return False
            if isinstance(node1.operand, (SymbolName, LiteralValue, Register)):
                return node1.operand == node2.operand
        if not isinstance(node1, AstNode) or not isinstance(node2, AstNode):
            raise TypeError("same_target called with invalid type(s)", node1, node2)
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
                if isinstance(expr.right, (LiteralValue, SymbolName)) and self._same_target(assignment.left, expr.left):
                    num_val = expr.right.const_value()
                    operator = expr.operator + '='
                    aug_assign = self._make_aug_assign(assignment, assignment.left.nodes[0], num_val, operator)
                    assignment.my_scope().replace_node(assignment, aug_assign)
                    self.optimizations_performed = True
                continue
            if expr.operator not in ('+', '*', '|', '^'):  # associative operators
                continue
            if isinstance(expr.right, (LiteralValue, SymbolName)) and self._same_target(assignment.left, expr.left):
                num_val = expr.right.const_value()
                operator = expr.operator + '='
                aug_assign = self._make_aug_assign(assignment, assignment.left.nodes[0], num_val, operator)
                assignment.my_scope().replace_node(assignment, aug_assign)
                self.optimizations_performed = True
            elif isinstance(expr.left, (LiteralValue, SymbolName)) and self._same_target(assignment.left, expr.right):
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
                    if isinstance(node.right, (LiteralValue, Register)) and self._same_target(node.left, prev_node.left):
                        if isinstance(node.left, SymbolName):
                            # only optimize if the symbol is not a memory mapped address (volatile memory!)
                            symdef = node.left.my_scope().lookup(node.left.name)
                            if isinstance(symdef, VarDef) and symdef.vartype == VarType.MEMORY:
                                continue
                        scope.remove_node(prev_node)
                        self.optimizations_performed = True
                        self.num_warnings += 1
                        print_warning("{}: removed superfluous assignment".format(prev_node.sourceref))
                prev_node = node

    @no_type_check
    def optimize_assignments(self) -> None:
        # remove assignment statements that do nothing (A=A)
        # remove augmented assignments that have no effect (x+=0, x-=0, x/=1, x//=1, x*=1)
        # convert augmented assignments to simple incr/decr if value allows it (A+=10 =>  incr A by 10)
        # simplify some calculations (x*=0, x**=0) to simple constant value assignment
        # @todo remove or simplify logical aug assigns like A |= 0, A |= true, A |= false  (or perhaps turn them into byte values first?)
        for assignment in self.module.all_nodes():
            if isinstance(assignment, Assignment):
                if self._same_target(assignment.left, assignment.right):
                    assignment.my_scope().remove_node(assignment)
                    self.optimizations_performed = True
                    self.num_warnings += 1
                    print_warning("{}: removed statement that has no effect (left=right)".format(assignment.sourceref))
            elif isinstance(assignment, AugAssignment):
                if isinstance(assignment.right, LiteralValue) and isinstance(assignment.right.value, (int, float)):
                    if assignment.right.value == 0:
                        if assignment.operator in ("+=", "-=", "|=", "<<=", ">>=", "^="):
                            self.num_warnings += 1
                            print_warning("{}: removed statement that has no effect (aug.assign zero)".format(assignment.sourceref))
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
                    elif assignment.right.value >= 8 and assignment.operator in ("<<=", ">>="):
                        print("{}: shifting result is always zero".format(assignment.sourceref))
                        new_stmt = Assignment(sourceref=assignment.sourceref)
                        new_stmt.nodes.append(assignment.left)
                        new_stmt.nodes.append(LiteralValue(value=0, sourceref=assignment.sourceref))
                        assignment.my_scope().replace_node(assignment, new_stmt)
                        assignment.mark_lhs()
                        self.optimizations_performed = True
                    elif assignment.operator in ("+=", "-=") and 0 < assignment.right.value < 256:
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
                    elif assignment.right.value == 1 and assignment.operator in ("/=", "//=", "*="):
                        self.num_warnings += 1
                        print_warning("{}: removed statement that has no effect (aug.assign identity)".format(assignment.sourceref))
                        assignment.my_scope().remove_node(assignment)
                        self.optimizations_performed = True

    @no_type_check
    def _make_new_assignment(self, old_aug_assignment: AugAssignment, constantvalue: int) -> Assignment:
        new_assignment = Assignment(sourceref=old_aug_assignment.sourceref)
        new_assignment.parent = old_aug_assignment.parent
        left = old_aug_assignment.left
        left.parent = new_assignment
        new_assignment.nodes.append(left)
        value = LiteralValue(value=constantvalue, sourceref=old_aug_assignment.sourceref)
        value.parent = new_assignment
        new_assignment.nodes.append(value)
        new_assignment.mark_lhs()
        return new_assignment

    @no_type_check
    def _make_aug_assign(self, old_assign: Assignment, target: Union[Register, SymbolName, Dereference],
                         value: Union[int, float], operator: str) -> AugAssignment:
        assert isinstance(target, (Register, SymbolName, Dereference))
        a = AugAssignment(operator=operator, sourceref=old_assign.sourceref)
        a.nodes.append(target)
        target.parent = a
        lv = LiteralValue(value=value, sourceref=old_assign.sourceref)
        a.nodes.append(lv)
        lv.parent = a
        a.parent = old_assign.parent
        a.mark_lhs()
        return a

    @no_type_check
    def _make_incrdecr(self, old_stmt: AstNode, target: Union[Register, SymbolName, Dereference],
                       howmuch: Union[int, float], operator: str) -> IncrDecr:
        assert isinstance(target, (Register, SymbolName, Dereference))
        a = IncrDecr(operator=operator, howmuch=howmuch, sourceref=old_stmt.sourceref)
        a.nodes.append(target)
        target.parent = a
        a.parent = old_stmt.parent
        return a

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


def optimize(mod: Module) -> None:
    opt = Optimizer(mod)
    opt.optimize()
    if opt.num_warnings:
        print_bold("There are {:d} optimization warnings.".format(opt.num_warnings))
