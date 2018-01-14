"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the code generator for assignment statements.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from typing import Callable
from ..plyparse import LiteralValue, Assignment, AugAssignment


def generate_assignment(out: Callable, stmt: Assignment) -> None:
    assert stmt.right is not None
    rvalue = stmt.right
    if isinstance(stmt.right, LiteralValue):
        rvalue = stmt.right.value
    # @todo


def generate_aug_assignment(out: Callable, stmt: AugAssignment) -> None:
    assert stmt.right is not None
    pass  # @todo
