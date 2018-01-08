"""
Programming Language for 6502/6510 microprocessors
This is the code to optimize the parse tree.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

from .plyparser import Module, Subroutine, Block, Directive, Assignment, AugAssignment
from .plylexer import print_warning, print_bold


class Optimizer:
    def __init__(self, mod: Module) -> None:
        self.num_warnings = 0
        self.module = mod

    def optimize(self) -> None:
        self.num_warnings = 0
        # self.remove_augmentedassign_incrdecr_nops(block)   # @todo
        self.remove_useless_assigns()
        # self.combine_assignments_into_multi(block)   # @todo
        self.optimize_multiassigns()
        self.remove_unused_subroutines()
        # self.optimize_compare_with_zero(block)  # @todo
        self.remove_empty_blocks()

    def remove_useless_assigns(self) -> None:
        # remove assignment statements that do nothing (A=A)
        for mnode, parent in self.module.all_scopes():
            if mnode.scope:
                for assignment in list(mnode.scope.nodes):
                    if isinstance(assignment, Assignment):
                        assignment.left = [lv for lv in assignment.left if lv != assignment.right]
                        if not assignment.left:
                            mnode.scope.remove_node(assignment)
                            self.num_warnings += 1
                            print_warning("{}: removed assignment statement that has no effect".format(assignment.sourceref))

    def optimize_multiassigns(self) -> None:
        # optimize multi-assign statements (remove duplicate targets, optimize order)
        for mnode, parent in self.module.all_scopes():
            if mnode.scope:
                for assignment in mnode.scope.nodes:
                    if isinstance(assignment, Assignment) and len(assignment.left) > 1:
                        # remove duplicates
                        lvalues = set(assignment.left)
                        if len(lvalues) != len(assignment.left):
                            self.num_warnings += 1
                            print_warning("{}: removed duplicate assignment targets".format(assignment.sourceref))
                        # @todo change order: first registers, then zp addresses, then non-zp addresses, then the rest (if any)
                        assignment.left = list(lvalues)

    def remove_unused_subroutines(self) -> None:
        # some symbols are used by the emitted assembly code from the code generator,
        # and should never be removed or the assembler will fail
        never_remove = {"c64.FREADUY", "c64.FTOMEMXY", "c64.FADD", "c64.FSUB",
                        "c64flt.GIVUAYF", "c64flt.copy_mflt", "c64flt.float_add_one", "c64flt.float_sub_one",
                        "c64flt.float_add_SW1_to_XY", "c64flt.float_sub_SW1_from_XY"}
        num_discarded = 0
        for sub, parent in self.module.all_scopes():
            if isinstance(sub, Subroutine):
                usages = self.module.subroutine_usage[(parent.name, sub.name)]
                if not usages and parent.name + '.' + sub.name not in never_remove:
                    parent.scope.remove_node(sub)
                    num_discarded += 1
        print("discarded {:d} unused subroutines".format(num_discarded))

    def remove_empty_blocks(self) -> None:
        # remove blocks without name and without address, or that are empty
        for node, parent in self.module.all_scopes():
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
                        assert isinstance(parent, (Block, Module))
                        parent.scope.nodes.remove(node)
            if isinstance(node, Block):
                if not node.name and node.address is None:
                    self.num_warnings += 1
                    print_warning("ignoring block without name and address", node.sourceref)
                    assert isinstance(parent, Module)
                    parent.scope.nodes.remove(node)


def optimize(mod: Module) -> None:
    opt = Optimizer(mod)
    opt.optimize()
    if opt.num_warnings:
        if opt.num_warnings == 1:
            print_bold("there is one optimization warning.")
        else:
            print_bold("there are {:d} optimization warnings.".format(opt.num_warnings))
