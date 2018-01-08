"""
Programming Language for 6502/6510 microprocessors
This is the code to optimize the parse tree.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

from typing import List
from .parse import ParseResult
from .symbols import Block, AugmentedAssignmentStmt, IntegerValue, FloatValue, AssignmentStmt, CallStmt, \
    Value, MemMappedValue, RegisterValue, AstNode


class Optimizer:
    def __init__(self, parseresult: ParseResult) -> None:
        self.parsed = parseresult

    def optimize(self) -> ParseResult:
        print("\noptimizing parse tree")
        for block in self.parsed.all_blocks():
            self.remove_augmentedassign_incrdecr_nops(block)
            self.remove_identity_assigns(block)
            self.combine_assignments_into_multi(block)
            self.optimize_multiassigns(block)
            self.remove_unused_subroutines(block)
            self.optimize_compare_with_zero(block)
        return self.parsed

    def remove_augmentedassign_incrdecr_nops(self, block: Block) -> None:
        have_removed_stmts = False
        for index, stmt in enumerate(list(block.statements)):
            if isinstance(stmt, AugmentedAssignmentStmt):
                if isinstance(stmt.right, (IntegerValue, FloatValue)):
                    if stmt.right.value == 0 and stmt.operator in ("+=", "-=", "|=", "<<=", ">>=", "^="):
                        print("{}: removed statement that has no effect".format(stmt.sourceref))
                        have_removed_stmts = True
                        block.statements[index] = None
                    if stmt.right.value >= 8 and stmt.operator in ("<<=", ">>="):
                        print("{}: shifting that many times always results in zero".format(stmt.sourceref))
                        new_stmt = AssignmentStmt(stmt.leftvalues, IntegerValue(0, stmt.sourceref), stmt.sourceref)
                        block.statements[index] = new_stmt
        if have_removed_stmts:
            # remove the Nones
            block.statements = [s for s in block.statements if s is not None]

    def optimize_compare_with_zero(self, block: Block) -> None:
        # a conditional goto that compares a value to zero will be simplified
        # the comparison operator and rvalue (0) will be removed and the if-status changed accordingly
        for stmt in block.statements:
            if isinstance(stmt, CallStmt):
                cond = stmt.condition
                if cond and isinstance(cond.rvalue, (IntegerValue, FloatValue)) and cond.rvalue.value == 0:
                    simplified = False
                    if cond.ifstatus in ("true", "ne"):
                        if cond.comparison_op == "==":
                            # if_true something == 0   ->  if_not something
                            cond.ifstatus = "not"
                            cond.comparison_op, cond.rvalue = "", None
                            simplified = True
                        elif cond.comparison_op == "!=":
                            # if_true something != 0  -> if_true something
                            cond.comparison_op, cond.rvalue = "", None
                            simplified = True
                    elif cond.ifstatus in ("not", "eq"):
                        if cond.comparison_op == "==":
                            # if_not something == 0   ->  if_true something
                            cond.ifstatus = "true"
                            cond.comparison_op, cond.rvalue = "", None
                            simplified = True
                        elif cond.comparison_op == "!=":
                            # if_not something != 0  -> if_not something
                            cond.comparison_op, cond.rvalue = "", None
                            simplified = True
                    if simplified:
                        print("{}: simplified comparison with zero".format(stmt.sourceref))

    def combine_assignments_into_multi(self, block: Block) -> None:
        # fold multiple consecutive assignments with the same rvalue into one multi-assignment
        statements = []   # type: List[AstNode]
        multi_assign_statement = None
        for stmt in block.statements:
            if isinstance(stmt, AssignmentStmt) and not isinstance(stmt, AugmentedAssignmentStmt):
                if multi_assign_statement and multi_assign_statement.right == stmt.right:
                    multi_assign_statement.leftvalues.extend(stmt.leftvalues)
                    print("{}: joined with previous line into multi-assign statement".format(stmt.sourceref))
                else:
                    if multi_assign_statement:
                        statements.append(multi_assign_statement)
                    multi_assign_statement = stmt
            else:
                if multi_assign_statement:
                    statements.append(multi_assign_statement)
                    multi_assign_statement = None
                statements.append(stmt)
        if multi_assign_statement:
            statements.append(multi_assign_statement)
        block.statements = statements

    def optimize_multiassigns(self, block: Block) -> None:
        # optimize multi-assign statements.
        for stmt in block.statements:
            if isinstance(stmt, AssignmentStmt) and len(stmt.leftvalues) > 1:
                # remove duplicates
                lvalues = list(set(stmt.leftvalues))
                if len(lvalues) != len(stmt.leftvalues):
                    print("{}: removed duplicate assignment targets".format(stmt.sourceref))
                # change order: first registers, then zp addresses, then non-zp addresses, then the rest (if any)
                stmt.leftvalues = list(sorted(lvalues, key=_value_sortkey))

    def remove_identity_assigns(self, block: Block) -> None:
        have_removed_stmts = False
        for index, stmt in enumerate(list(block.statements)):
            if isinstance(stmt, AssignmentStmt):
                stmt.remove_identity_lvalues()
                if not stmt.leftvalues:
                    print("{}: removed identity assignment statement".format(stmt.sourceref))
                    have_removed_stmts = True
                    block.statements[index] = None
        if have_removed_stmts:
            # remove the Nones
            block.statements = [s for s in block.statements if s is not None]

    def remove_unused_subroutines(self, block: Block) -> None:
        # some symbols are used by the emitted assembly code from the code generator,
        # and should never be removed or the assembler will fail
        never_remove = {"c64.FREADUY", "c64.FTOMEMXY", "c64.FADD", "c64.FSUB",
                        "c64flt.GIVUAYF", "c64flt.copy_mflt", "c64flt.float_add_one", "c64flt.float_sub_one",
                        "c64flt.float_add_SW1_to_XY", "c64flt.float_sub_SW1_from_XY"}
        discarded = []
        for sub in list(block.symbols.iter_subroutines()):
            usages = self.parsed.subroutine_usage[(sub.blockname, sub.name)]
            if not usages and sub.blockname + '.' + sub.name not in never_remove:
                block.symbols.remove_node(sub.name)
                discarded.append(sub.name)
        if discarded:
            print("{}: discarded {:d} unused subroutines from block '{:s}'".format(block.sourceref, len(discarded), block.name))


def _value_sortkey(value: Value) -> int:
    if isinstance(value, RegisterValue):
        num = 0
        for char in value.register:
            num *= 100
            num += ord(char)
        return num
    elif isinstance(value, MemMappedValue):
        if value.address is None:
            return 99999999
        if value.address < 0x100:
            return 10000 + value.address
        else:
            return 20000 + value.address
    else:
        return 99999999
