"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the main program that drives the rest.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import time
import os
import re
import argparse
import subprocess
from .compile import PlyParser
from .optimize import optimize
from .plylex import print_bold
from .plyparse import ProgramFormat, Module


class Assembler64Tass:
    def __init__(self, format: ProgramFormat) -> None:
        self.format = format

    def assemble(self, inputfilename: str, outputfilename: str) -> None:
        args = ["64tass", "--ascii", "--case-sensitive", "-Wall", "-Wno-strict-bool",
                "--dump-labels", "--vice-labels", "-l", outputfilename+".vice-mon-list",
                "--no-monitor", "--output", outputfilename, inputfilename]
        if self.format in (ProgramFormat.PRG, ProgramFormat.BASIC):
            args.append("--cbm-prg")
        elif self.format == ProgramFormat.RAW:
            args.append("--nostart")
        else:
            raise ValueError("don't know how to create code format "+str(self.format))
        try:
            if self.format == ProgramFormat.PRG:
                print("\nCreating C-64 prg.")
            elif self.format == ProgramFormat.RAW:
                print("\nCreating raw binary.")
            try:
                subprocess.check_call(args)
            except FileNotFoundError as x:
                raise SystemExit("ERROR: cannot run assembler program: "+str(x))
        except subprocess.CalledProcessError as x:
            raise SystemExit("assembler failed with returncode " + str(x.returncode))

    def generate_breakpoint_list(self, program_filename: str) -> str:
        breakpoints = []
        vice_mon_file = program_filename + ".vice-mon-list"
        with open(vice_mon_file, "rU") as f:
            for line in f:
                match = re.fullmatch(r"al (?P<address>\w+) \S+_il65_breakpoint_\d+.?", line, re.DOTALL)
                if match:
                    breakpoints.append("$" + match.group("address"))
        with open(vice_mon_file, "at") as f:
            print("; vice monitor breakpoint list now follows", file=f)
            print("; {:d} breakpoints have been defined here".format(len(breakpoints)), file=f)
            print("del", file=f)
            for b in breakpoints:
                print("break", b, file=f)
        return vice_mon_file


def main() -> None:
    description = "Compiler for IL65 language, code name 'Sick'"
    ap = argparse.ArgumentParser(description=description)
    ap.add_argument("-o", "--output", help="output directory")
    ap.add_argument("-c", "--codegenerator", choices=["6502", "tinyvm"], default="tinyvm", help="what code generator to use")
    ap.add_argument("-f", "--enablefloat", action="store_true", help="enable C64 (mflpt5) floating point operations")
    ap.add_argument("-no", "--nooptimize", action="store_true", help="do not optimize the parse tree")
    ap.add_argument("-sv", "--startvice", action="store_true", help="autostart vice x64 emulator after compilation")
    ap.add_argument("sourcefile", help="the source .ill/.il65 file to compile")
    args = ap.parse_args()
    assembly_filename = os.path.splitext(args.sourcefile)[0] + ".asm"
    program_filename = os.path.splitext(args.sourcefile)[0] + ".prg"
    if args.output:
        os.makedirs(args.output, mode=0o700, exist_ok=True)
        assembly_filename = os.path.join(args.output, os.path.split(assembly_filename)[1])
        program_filename = os.path.join(args.output, os.path.split(program_filename)[1])

    print("\n" + description)

    start = time.perf_counter()
    print("\nParsing program source code.")
    parser = PlyParser(enable_floats=args.enablefloat)
    parsed_module = parser.parse_file(args.sourcefile)
    if parsed_module:
        if args.nooptimize:
            print_bold("Optimizations disabled!")
        else:
            print("\nOptimizing code.")
            optimize(parsed_module)
        if args.codegenerator == "tinyvm":
            generate_tinyvm_code(parsed_module, args.enablefloat, assembly_filename, start)
        else:
            generate_6502_code(parsed_module, args.enablefloat, args.startvice, program_filename, assembly_filename, start)


def generate_tinyvm_code(module: Module, float_enabled: bool, assembly_filename: str, compilationstart: float) -> None:
    print("\nGenerating tinyvm code.")
    from il65.codegen.tinyvm.generate import AssemblyGenerator
    cg = AssemblyGenerator(module, float_enabled)
    cg.generate(assembly_filename)
    duration_total = time.perf_counter() - compilationstart
    print("Compile duration:  {:.2f} seconds".format(duration_total))
    print()


def generate_6502_code(module: Module, float_enabled: bool, startvice: bool,
                       program_filename: str, assembly_filename: str, compilationstart: float) -> None:
    print("\nGenerating 6502 assembly code.")
    from il65.codegen.mos6502.generate import AssemblyGenerator
    cg = AssemblyGenerator(module, float_enabled)
    cg.generate(assembly_filename)
    assembler = Assembler64Tass(module.format)
    assembler.assemble(assembly_filename, program_filename)
    mon_command_file = assembler.generate_breakpoint_list(program_filename)
    duration_total = time.perf_counter() - compilationstart
    print("Compile duration:  {:.2f} seconds".format(duration_total))
    size = os.path.getsize(program_filename)
    print("Output size:       {:d} bytes".format(size))
    print_bold("Output file:       " + program_filename)
    print()
    if startvice:
        print("Autostart vice emulator...")
        # "-remotemonitor"
        cmdline = ["x64", "-moncommands", mon_command_file,
                   "-autostartprgmode", "1", "-autostart-warp", "-autostart", program_filename]
        with open(os.devnull, "wb") as shutup:
            subprocess.call(cmdline, stdout=shutup)
