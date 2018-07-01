IL65 / 'Sick' - Experimental Programming Language for 8-bit 6502/6510 microprocessors
=====================================================================================

*Written by Irmen de Jong (irmen@razorvine.net)*

*Software license: GNU GPL 3.0, see file LICENSE*


This is an experimental programming language for the 8-bit 6502/6510 microprocessor from the late 1970's and 1980's
as used in many home computers from that era. IL65 is a medium to low level programming language,
which aims to provide many conveniences over raw assembly code (even when using a macro assembler):

- reduction of source code length
- easier program understanding (because it's higher level, and more terse)
- option to automatically run the compiled program in the Vice emulator  
- modularity, symbol scoping, subroutines
- subroutines have enforced input- and output parameter definitions
- various data types other than just bytes (16-bit words, floats, strings, 16-bit register pairs)
- automatic variable allocations, automatic string variables and string sharing
- constant folding in expressions (compile-time evaluation)
- automatic type conversions
- floating point operations
- optional automatic preserving and restoring CPU registers state, when calling routines that otherwise would clobber these 
- abstracting away low level aspects such as ZeroPage handling, program startup, explicit memory addresses
- breakpoints, that let the Vice emulator drop into the monitor if execution hits them
- source code labels automatically loaded in Vice emulator so it can show them in disassembly
- conditional gotos
- various code optimizations (code structure, logical and numerical expressions, ...) 


It still allows for low level programming however and inline assembly blocks
to write performance critical pieces of code, but otherwise compiles fairly straightforwardly
into 6502 assembly code. This resulting code is assembled into a binary program by using
an external macro assembler, [64tass](https://sourceforge.net/projects/tass64/).
It can be compiled pretty easily for various platforms (Linux, Mac OS, Windows) or just ask me
to provide a small precompiled executable if you need that. 
You need [Python 3.5](https://www.python.org/downloads/) or newer to run IL65 itself.

IL65 is mainly targeted at the Commodore-64 machine, but should be mostly system independent.


See [the reference document](reference.md) for detailed information.
