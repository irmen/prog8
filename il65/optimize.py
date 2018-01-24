"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the optimizer that applies various optimizations to the parse tree,
eliminates statements that have no effect, optimizes calculations etc.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from typing import List, no_type_check
from .plyparse import Module, Subroutine, Block, Directive, Assignment, AugAssignment, Goto, Expression, IncrDecr,\
    datatype_of, coerce_constant_value, AssignmentTargets, LiteralValue, Scope, Register
from .plylex import print_warning, print_bold


class Optimizer:
    def __init__(self, mod: Module) -> None:
        self.num_warnings = 0
        self.module = mod

    def optimize(self) -> None:
        self.num_warnings = 0
        self.optimize_assignments()
        self.remove_superfluous_assignments()
        self.combine_assignments_into_multi()
        self.optimize_multiassigns()
        self.remove_unused_subroutines()
        self.optimize_goto_compare_with_zero()
        # @todo join multiple incr/decr of same var into one (if value stays < 256)
        # @todo analyse for unreachable code and remove that (f.i. code after goto or return that has no label so can never be jumped to)
        self.remove_empty_blocks()

    def remove_superfluous_assignments(self) -> None:
        # remove consecutive assignment statements to the same target, only keep the last value (only if its a constant!)
        # this is NOT done for memory mapped variables because these often represent a volatile register of some sort!
        for scope in self.module.all_nodes(Scope):
            prev_node = None
            for node in list(scope.nodes):
                if isinstance(node, Assignment) and isinstance(prev_node, Assignment):
                    if isinstance(node.right, (LiteralValue, Register)) and node.left.same_targets(prev_node.left):
                        if not node.left.has_memvalue():
                            scope.remove_node(prev_node)
                            self.num_warnings += 1
                            print_warning("{}: removed superfluous assignment".format(prev_node.sourceref))
                prev_node = node

    def optimize_assignments(self) -> None:
        # remove assignment statements that do nothing (A=A)
        # and augmented assignments that have no effect (x+=0, x-=0, x/=1, x//=1, x*=1)
        # convert augmented assignments to simple incr/decr if possible (A+=10 =>  A++ by 10)
        # simplify some calculations (x*=0, x**=0) to simple constant value assignment
        # @todo remove or simplify logical aug assigns like A |= 0, A |= true, A |= false  (or perhaps turn them into byte values first?)
        for assignment in self.module.all_nodes():
            if isinstance(assignment, Assignment):
                if any(lv != assignment.right for lv in assignment.left.nodes):
                    assignment.left.nodes = [lv for lv in assignment.left.nodes if lv != assignment.right]
                if not assignment.left:
                    assignment.my_scope().remove_node(assignment)
                    self.num_warnings += 1
                    print_warning("{}: removed statement that has no effect".format(assignment.sourceref))
            if isinstance(assignment, AugAssignment):
                if isinstance(assignment.right, LiteralValue) and isinstance(assignment.right.value, (int, float)):
                    if assignment.right.value == 0:
                        if assignment.operator in ("+=", "-=", "|=", "<<=", ">>=", "^="):
                            self.num_warnings += 1
                            print_warning("{}: removed statement that has no effect".format(assignment.sourceref))
                            assignment.my_scope().remove_node(assignment)
                        elif assignment.operator == "*=":
                            self.num_warnings += 1
                            print_warning("{}: statement replaced by = 0".format(assignment.sourceref))
                            new_assignment = self._make_new_assignment(assignment, 0)
                            assignment.my_scope().replace_node(assignment, new_assignment)
                        elif assignment.operator == "**=":
                            self.num_warnings += 1
                            print_warning("{}: statement replaced by = 1".format(assignment.sourceref))
                            new_assignment = self._make_new_assignment(assignment, 1)
                            assignment.my_scope().replace_node(assignment, new_assignment)
                    if assignment.right.value >= 8 and assignment.operator in ("<<=", ">>="):
                        print("{}: shifting result is always zero".format(assignment.sourceref))
                        new_stmt = Assignment(sourceref=assignment.sourceref)
                        new_stmt.nodes.append(AssignmentTargets(nodes=[assignment.left], sourceref=assignment.sourceref))
                        new_stmt.nodes.append(0)    # XXX literalvalue?
                        assignment.my_scope().replace_node(assignment, new_stmt)
                    if assignment.operator in ("+=", "-=") and 0 < assignment.right.value < 256:
                        howmuch = assignment.right
                        if howmuch.value not in (0, 1):
                            _, howmuch = coerce_constant_value(datatype_of(assignment.left, assignment.my_scope()),
                                                               howmuch, assignment.sourceref)
                        new_stmt = IncrDecr(operator="++" if assignment.operator == "+=" else "--",
                                            howmuch=howmuch.value, sourceref=assignment.sourceref)
                        new_stmt.target = assignment.left
                        assignment.my_scope().replace_node(assignment, new_stmt)
                    if assignment.right.value == 1 and assignment.operator in ("/=", "//=", "*="):
                        self.num_warnings += 1
                        print_warning("{}: removed statement that has no effect".format(assignment.sourceref))
                        assignment.my_scope().remove_node(assignment)

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
        if num_discarded:
            print("discarded {:d} unused subroutines".format(num_discarded))

    @no_type_check
    def optimize_goto_compare_with_zero(self) -> None:
        # a conditional goto that compares a value with zero will be simplified
        # the comparison operator and rvalue (0) will be removed and the if-status changed accordingly
        for goto in self.module.all_nodes(Goto):
            if isinstance(goto.condition, Expression):
                print("NOT IMPLEMENTED YET: optimize goto conditionals", goto.condition)   # @todo
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
