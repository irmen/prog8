#!/usr/bin/env python

program_description = """
This is a simple run-time profiler tool for X16 assembly programs.
It takes an assembly list file (as produced by 64tass/turbo assembler) and
a memory access statistics dump file (produced by the X16 emulator's -memorystats option)
and prints out what assembly lines and variables were read from and written to the most.
These may indicate hot paths or even bottlenecks in your program,
and what variables in system ram might be better placed in Zeropage.

Also see https://prog8.readthedocs.io/en/latest/technical.html#run-time-memory-profiling-with-the-x16-emulator
for an example of how to use this tool together with the X16 emulator.
"""


import argparse
import operator
from typing import Tuple


class AsmList:
    """parses a 64tass Turbo Assembler Macro listing file"""

    def __init__(self, filename: str) -> None:
        self.lines = []
        symbols = {}
        self.check_format(filename)
        for index, line in enumerate(open(filename, "rt"), 1):
            if not line or line == '\n' or line[0] == ';':
                continue
            if line[0] == '=':
                value, symbol = line.split(maxsplit=2)[:2]
                value = value[1:]
                if value:
                    try:
                        if value[0] == '$':
                            address = int(value[1:], 16)
                        else:
                            address = int(value)
                        symbols[symbol] = (address, index)
                    except ValueError:
                        pass
            elif line[0] == '>':
                value, rest = line.split(maxsplit=1)
                address = int(value[1:], 16)
                self.lines.append((address, rest.strip(), index))
            elif line[0] == '.':
                value, rest = line.split(maxsplit=1)
                address = int(value[1:], 16)
                self.lines.append((address, rest.strip(), index))
            else:
                raise ValueError("invalid syntax: " + line)
        for name, (address, index) in symbols.items():
            self.lines.append((address, name, index))
        self.lines.sort()

    def check_format(self, filename: str) -> None:
        with open(filename, "rt") as inf:
            firstline = inf.readline()
            if firstline.startswith(';') and "listing file" in firstline:
                pass
            else:
                secondline = inf.readline()
                if secondline.startswith(';') and "listing file" in secondline:
                    pass
                else:
                    raise IOError("listing file is not in recognised 64tass / turbo assembler format")

    def print_info(self) -> None:
        print("number of actual lines in the assembly listing:", len(self.lines))

    def find(self, address: int) -> list[Tuple[int, str, int]]:
        exact_result = [(line_addr, name, index) for line_addr, name, index in self.lines if line_addr == address]
        if exact_result:
            return exact_result
        fuzzy_result = [(line_addr, name, index) for line_addr, name, index in self.lines if line_addr == address - 1]
        if fuzzy_result:
            return fuzzy_result
        fuzzy_result = [(line_addr, name, index) for line_addr, name, index in self.lines if line_addr == address + 1]
        if fuzzy_result:
            return fuzzy_result
        return []


class MemoryStats:
    """parses the read and write counts in a x16emulator memory statistics file"""

    def __init__(self, filename: str) -> None:
        self.check_format(filename)
        self.reads = []
        self.writes = []

        def parse(rest: str) -> Tuple[int, int, int]:
            if ':' in rest:
                bank = int(rest[:2], 16)
                address = int(rest[3:7], 16)
                count = int(rest[8:])
            else:
                bank = 0  # regular system RAM, bank is irrellevant.
                address = int(rest[:4], 16)
                count = int(rest[5:])
            return bank, address, count

        for line in open(filename, "rt"):
            if line.startswith("r "):
                bank, address, count = parse(line[2:])
                self.reads.append(((bank, address), count))
            elif line.startswith("w "):
                bank, address, count = parse(line[2:])
                self.writes.append(((bank, address), count))
        self.reads.sort(reverse=True, key=operator.itemgetter(1))
        self.writes.sort(reverse=True, key=operator.itemgetter(1))

    def check_format(self, filename: str) -> None:
        with open(filename, "rt") as inf:
            firstline = inf.readline()
            if not firstline.startswith("Usage counts "):
                raise IOError("memory statistics file is not recognised as a X16 emulator memorystats file")

    def print_info(self) -> None:
        print("number of distinct addresses read from  :", len(self.reads))
        print("number of distinct addresses written to :", len(self.writes))
        counts = sum(c for _, c in self.reads)
        print(f"total number of reads  : {counts} ({counts//1_000_000}M)")
        counts = sum(c for _, c in self.writes)
        print(f"total number of writes : {counts} ({counts//1_000_000}M)")


def profile(number_of_lines: int, asmlist: str, memstats: str) -> None:
    """performs profiling analysis of the given assembly listing file based on the given memory stats file"""
    asm = AsmList(asmlist)
    stats = MemoryStats(memstats)
    asm.print_info()
    stats.print_info()

    def print_unknown(address: int) -> None:
        if address < 0x100:
            print("unknown zp")
        elif address < 0x200:
            print("cpu stack")
        elif address in range(0x9f00, 0xa000):
            print("io")
        else:
            print("unknown")

    print(f"\ntop {number_of_lines} most reads:")
    for (bank, address), count in stats.reads[:number_of_lines]:
        print(f"${address:04x} ({count}) : ", end="")
        if bank == 0 and address < 0xa000:
            result = asm.find(address)
            if result:
                lines = [f"${address:04x} '{line}' (line {line_number})" for address, line, line_number in result]
                print(", ".join(lines))
            else:
                print_unknown(address)
        else:
            print(f"banked memory: {bank:02x}:{address:04x}")
    print(f"\ntop {number_of_lines} most writes:")
    for (bank, address), count in stats.writes[:number_of_lines]:
        print(f"${address:04x} ({count}) : ", end="")
        if bank == 0 and address < 0xa000:
            result = asm.find(address)
            if result:
                lines = [f"${address:04x} '{line}' (line {line_number})" for address, line, line_number in result]
                print(", ".join(lines))
            else:
                print_unknown(address)
        else:
            print(f"banked memory: {bank:02x}:{address:04x}")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=program_description)
    parser.add_argument("-n", dest="number", type=int, default=20, help="amount of reads and writes to print (default 20)")
    parser.add_argument("asmlistfile", type=str, help="the 64tass/turbo assembler listing file to read")
    parser.add_argument("memorystatsfile", type=str, help="the X16 emulator memstats dump file to read")
    args = parser.parse_args()
    profile(args.number, args.asmlistfile, args.memorystatsfile)
