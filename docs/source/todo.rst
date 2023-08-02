TODO
====

- add command line option to enable/disable the inclusion of p8 source lines into the generated assembly / p8ir  see outputSourceLine()
- add a mechanism to pass the original p8 source lines into the p8ir file as comments. Remove the position xml tags.
- try to reduce the number of uses of temp variables for example in array[idx] -= amount   /
- investigate McCarthy evaluation again? this may also reduce code size perhaps for things like if a>4 or a<2 .... / - investigate McCarthy evaluation again? this may also reduce code size perhaps for things like if a>4 or a<2 ....

- IR: reduce the number of branch instructions such as BEQ, BEQR, etc (gradually), replace with CMP(I) + status branch instruction
- IR: reduce amount of CMP/CMPI after instructions that set the status bits correctly (LOADs? INC? etc), but only after setting the status bits is verified!

...


Need help with
^^^^^^^^^^^^^^
- getting the IR in shape for code generation
- atari target: more details details about the machine, fixing library routines. I have no clue whatsoever.
- see the :ref:`portingguide` for details on what information is needed.


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
Compiler:

- [much work:] more support for (64tass) SEGMENTS ?
    - (What, how, isn't current BSS support enough?)
    - Add a mechanism to allocate variables into golden ram (or segments really) (see GoldenRam class)
    - maybe treat block "golden" in a special way: can only contain vars, every var will be allocated in the Golden ram area?
    - maybe or may not needed: the variables can NOT have initialization values, they will all be set to zero on startup (simple memset)
      just initialize them yourself in start() if you need a non-zero value .
    - OR.... do all this automatically if 'golden' is enabled as a compiler option? So compiler allocates in ZP first, then Golden Ram, then regular ram
    - OR.... make all this more generic and use some %segment option to create real segments for 64tass?
    - (need separate step in codegen and IR to write the "golden" variables)

- ir: idea: (but LLVM IR simply keeps the variables, so not a good idea then?...): replace all scalar variables by an allocated register. Keep a table of the variable to register mapping (including the datatype)
  global initialization values are simply a list of LOAD instructions.
  Variables replaced include all subroutine parameters!  So the only variables that remain as variables are arrays and strings.
- ir: add more optimizations in IRPeepholeOptimizer
- ir: the @split arrays are currently also split in _lsb/_msb arrays in the IR, and operations take multiple (byte) instructions that may lead to verbose and slow operation and machine code generation down the line.
- ir: for expressions with array indexes that occur multiple times, can we avoid loading them into new virtualregs everytime and just reuse a single virtualreg as indexer? (simple form of common subexpression elimination)
- PtAst/IR: more complex common subexpression eliminations
- [problematic due to using 64tass:] better support for building library programs, where unused .proc shouldn't be deleted from the assembly?
  Perhaps replace all uses of .proc/.pend/.endproc by .block/.bend will fix that with a compiler flag?
  But all library code written in asm uses .proc already..... (textual search/replace when writing the actual asm?)
  Once new codegen is written that is based on the IR, this point is mostly moot anyway as that will have its own dead code removal on the IR level.
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions
- generate WASM to eventually run prog8 on a browser canvas? Use binaryen toolkit or my binaryen kotlin library?


Libraries:

- fix the problems in atari target, and flesh out its libraries.
- c128 target: make syslib more complete (missing kernal routines)?
- c64: make the graphics.BITMAP_ADDRESS configurable (VIC banking)


Optimizations:

- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers, then the rest
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code,
  those checks should probably be removed, or be made permanent


STRUCTS again?
--------------

What if we were to re-introduce Structs in prog8? Some thoughts:

- can contain only numeric types (byte,word,float) - no nested structs, no reference types (strings, arrays) inside structs
- only as a reference type (uword pointer). This removes a lot of the problems related to introducing a variable length value type.
- arrays of struct is just an array of uword pointers. Can even be @split?
- need to introduce typed pointer datatype in prog8
- str is then syntactic sugar for pointer to character/byte?
- arrays are then syntactic sugar for pointer to byte/word/float?
