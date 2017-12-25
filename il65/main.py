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
from .parse import Parser, Optimizer
from .preprocess import PreprocessingParser
from .codegen import CodeGenerator, Assembler64Tass


def main() -> None:
    description = "Compiler for IL65 language, code name 'Sick'"
    ap = argparse.ArgumentParser(description=description)
    ap.add_argument("-o", "--output", help="output directory")
    ap.add_argument("--noopt", action="store_true", help="do not optimize the parse tree")
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
    symbols.print_table(True)

    p = Parser(args.sourcefile, args.output, sourcelines, ppsymbols=symbols, sub_usage=pp.result.subroutine_usage)
    parsed = p.parse()
    if parsed:
        if args.noopt:
            print("not optimizing the parse tree!")
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
        duration_total = time.perf_counter() - start
        print("Compile duration:  {:.2f} seconds".format(duration_total))
        print("Output file:      ", program_filename)
        print()
