"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the code generator for gotos and subroutine calls.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

from typing import Callable
from ..plyparse import Goto, SubCall


def generate_goto(out: Callable, stmt: Goto) -> None:
    pass  # @todo


def generate_subcall(out: Callable, stmt: SubCall) -> None:
    pass  # @todo

