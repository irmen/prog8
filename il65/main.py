#! /usr/bin/env python3

"""
Programming Language for 6502/6510 microprocessors, codename 'Sick'
This is the main program that drives the rest.

Written by Irmen de Jong (irmen@razorvine.net)
License: GNU GPL 3.0, see LICENSE
"""

import time
import os
import argparse
import subprocess
from .parse import Parser, Optimizer
from .preprocess import PreprocessingParser
from .codegen import CodeGenerator, Assembler64Tass


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
    pp = PreprocessingParser(args.sourcefile, )
    sourcelines, symbols = pp.preprocess()
    # symbols.print_table()

    p = Parser(args.sourcefile, args.output, sourcelines, ppsymbols=symbols, sub_usage=pp.result.subroutine_usage)
    parsed = p.parse()
    if parsed:
        if args.nooptimize:
            p.print_bold("not optimizing the parse tree!")
        else:
            opt = Optimizer(parsed)
            parsed = opt.optimize()
        cg = CodeGenerator(parsed)
        cg.generate()
        cg.optimize()
        with open(assembly_filename, "wt") as out:
            cg.write_assembly(out)
        assembler = Assembler64Tass(parsed.format)
        assembler.assemble(assembly_filename, program_filename)
        mon_command_file = assembler.generate_breakpoint_list(program_filename)
        duration_total = time.perf_counter() - start
        print("Compile duration:  {:.2f} seconds".format(duration_total))
        p.print_bold("Output file:       " + program_filename)
        print()
        if args.startvice:
            print("Autostart vice emulator...")
            cmdline = ["x64", "-remotemonitor", "-moncommands", mon_command_file,
                       "-autostartprgmode", "1", "-autostart-warp", "-autostart", program_filename]
            with open(os.devnull, "wb") as shutup:
                subprocess.call(cmdline, stdout=shutup)
