"""
Simplistic 8/16 bit Virtual Machine to execute a stack based instruction language.
Core data structures and definitions.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import enum
import struct
from typing import Callable
from il65.codegen.shared import mflpt5_to_float, to_mflpt5


class DataType(enum.IntEnum):
    BOOL = 1
    BYTE = 2
    SBYTE = 3
    WORD = 4
    SWORD = 5
    FLOAT = 6
    ARRAY_BYTE = 7
    ARRAY_SBYTE = 8
    ARRAY_WORD = 9
    ARRAY_SWORD = 10
    MATRIX_BYTE = 11
    MATRIX_SBYTE = 12


class ExecutionError(Exception):
    pass


class TerminateExecution(SystemExit):
    pass


class MemoryAccessError(Exception):
    pass


class Memory:
    def __init__(self):
        self.mem = bytearray(65536)
        self.readonly = bytearray(65536)
        self.mmap_io_charout_addr = -1
        self.mmap_io_charout_callback = None
        self.mmap_io_charin_addr = -1
        self.mmap_io_charin_callback = None

    def mark_readonly(self, start: int, end: int) -> None:
        self.readonly[start:end+1] = [1] * (end-start+1)

    def memmapped_io_charout(self, address: int, callback: Callable) -> None:
        self.mmap_io_charout_addr = address
        self.mmap_io_charout_callback = callback

    def memmapped_io_charin(self, address: int, callback: Callable) -> None:
        self.mmap_io_charin_addr = address
        self.mmap_io_charin_callback = callback

    def get_byte(self, index: int) -> int:
        if self.mmap_io_charin_addr == index:
            self.mem[index] = self.mmap_io_charin_callback()
        return self.mem[index]

    def get_bytes(self, startindex: int, amount: int) -> bytearray:
        return self.mem[startindex: startindex+amount]

    def get_sbyte(self, index: int) -> int:
        if self.mmap_io_charin_addr == index:
            self.mem[index] = self.mmap_io_charin_callback()
        return struct.unpack("b", self.mem[index:index+1])[0]

    def get_word(self, index: int) -> int:
        return self.mem[index] + 256 * self.mem[index+1]

    def get_sword(self, index: int) -> int:
        return struct.unpack("<h", self.mem[index:index+2])[0]

    def get_float(self, index: int) -> float:
        return mflpt5_to_float(self.mem[index: index+5])

    def set_byte(self, index: int, value: int) -> None:
        if self.readonly[index]:
            raise MemoryAccessError("read-only", index)
        self.mem[index] = value
        if self.mmap_io_charout_addr == index:
            self.mmap_io_charout_callback(value)

    def set_sbyte(self, index: int, value: int) -> None:
        if self.readonly[index]:
            raise MemoryAccessError("read-only", index)
        self.mem[index] = struct.pack("b", bytes([value]))[0]
        if self.mmap_io_charout_addr == index:
            self.mmap_io_charout_callback(self.mem[index])

    def set_word(self, index: int, value: int) -> None:
        if self.readonly[index] or self.readonly[index+1]:
            raise MemoryAccessError("read-only", index)
        self.mem[index], self.mem[index + 1] = struct.pack("<H", value)

    def set_sword(self, index: int, value: int) -> None:
        if self.readonly[index] or self.readonly[index+1]:
            raise MemoryAccessError("read-only", index)
        self.mem[index], self.mem[index + 1] = struct.pack("<h", value)

    def set_float(self, index: int, value: float) -> None:
        if any(self.readonly[index:index+5]):
            raise MemoryAccessError("read-only", index)
        self.mem[index: index+5] = to_mflpt5(value)
