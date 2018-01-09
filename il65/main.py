"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the main program that drives the rest.

Written by Irmen de Jong (irmen@razorvine.net) - license: GNU GPL 3.0
"""

import time
import os
import argparse
import subprocess
from .compile import PlyParser
from .optimize import optimize
from .generateasm import AssemblyGenerator, Assembler64Tass
from .plylex import print_bold


def main() -> None:
    description = "Compiler for IL65 language, code name 'Sick'"
    ap = argparse.ArgumentParser(description=description)
    ap.add_argument("-o", "--output", help="output directory")
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
    parser = PlyParser()
    parsed_module = parser.parse_file(args.sourcefile)
    if parsed_module:
        if args.nooptimize:
            print_bold("not optimizing the parse tree!")
        else:
            print("\nOptimizing code.")
            optimize(parsed_module)
        print("\nGenerating assembly code.")
        cg = AssemblyGenerator(parsed_module)
        cg.generate(assembly_filename)
        assembler = Assembler64Tass(parsed_module.format)
        assembler.assemble(assembly_filename, program_filename)
        mon_command_file = assembler.generate_breakpoint_list(program_filename)
        duration_total = time.perf_counter() - start
        print("Compile duration:  {:.2f} seconds".format(duration_total))
        print_bold("Output file:       " + program_filename)
        print()
        if args.startvice:
            print("Autostart vice emulator...")
            cmdline = ["x64", "-remotemonitor", "-moncommands", mon_command_file,
                       "-autostartprgmode", "1", "-autostart-warp", "-autostart", program_filename]
            with open(os.devnull, "wb") as shutup:
                subprocess.call(cmdline, stdout=shutup)
