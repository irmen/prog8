
TODO
====

- IRQ callback should return boolean to tell Prog8 to call the kernal ISR or not.

- [on branch: shortcircuit] investigate McCarthy evaluation again? this may also reduce code size perhaps for things like if a>4 or a<2 ....

...


Need help with
^^^^^^^^^^^^^^
- getting the IR in shape for code generation
- atari target: more details details about the machine, fixing library routines. I have no clue whatsoever.
- see the :ref:`portingguide` for details on what information is needed.


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
Compiler:

- Currently "320*240/8/8" gives integer overflow, so: allow constant integer subexpressions to contain out of range integers (>65535 etc) as long as the final constant value is within byte/word range.
- Multidimensional arrays and chained indexing, purely as syntactic sugar over regular arrays.
- fix the other cases of "TODO index could also be a binexpr" (in AssignmentAsmGen), but these are for float arrays so rarely used.

- [much work:] more support for (64tass) SEGMENTS ?
    - (What, how, isn't current BSS support enough?)
    - Add a mechanism to allocate variables into golden ram (or segments really) (see GoldenRam class)
    - maybe treat block "golden" in a special way: can only contain vars, every var will be allocated in the Golden ram area?
    - maybe or may not needed: the variables can NOT have initialization values, they will all be set to zero on startup (simple memset)
      just initialize them yourself in start() if you need a non-zero value .
    - OR.... do all this automatically if 'golden' is enabled as a compiler option? So compiler allocates in ZP first, then Golden Ram, then regular ram
    - OR.... make all this more generic and use some %segment option to create real segments for 64tass?
    - (need separate step in codegen and IR to write the "golden" variables)

- do we need (array)variable alignment tag instead of block alignment tag? You want to align the data, not the code in the block?
- ir: block alignment doesn't translate well to variables in the block (the actual stuff that needs to be aligned in memory)  but: need variable alignment tag instead of block alignment tag, really
- ir: idea: (but LLVM IR simply keeps the variables, so not a good idea then?...): replace all scalar variables by an allocated register. Keep a table of the variable to register mapping (including the datatype)
  global initialization values are simply a list of LOAD instructions.
  Variables replaced include all subroutine parameters!  So the only variables that remain as variables are arrays and strings.
- ir: add more optimizations in IRPeepholeOptimizer
- ir: for expressions with array indexes that occur multiple times, can we avoid loading them into new virtualregs everytime and just reuse a single virtualreg as indexer? (simple form of common subexpression elimination)
- ir: the @split arrays are currently also split in _lsb/_msb arrays in the IR, and operations take multiple (byte) instructions that may lead to verbose and slow operation and machine code generation down the line.
  maybe another representation is needed once actual codegeneration is done from the IR...?
- PtAst/IR: more complex common subexpression eliminations
- [problematic due to using 64tass:] better support for building library programs, where unused .proc shouldn't be deleted from the assembly?
  Perhaps replace all uses of .proc/.pend/.endproc by .block/.bend will fix that with a compiler flag?
  But all library code written in asm uses .proc already..... (textual search/replace when writing the actual asm?)
  Once new codegen is written that is based on the IR, this point is mostly moot anyway as that will have its own dead code removal on the IR level.
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions
- generate WASM to eventually run prog8 on a browser canvas? Use binaryen toolkit or my binaryen kotlin library?


Libraries:

- gfx2 cs_innerloop640() extend number of bytes cleared, and use vera fx cached writes (speed up screen clear)
- gfx2 horizontal_line and vertical_line: use vera fx cached writes
- use verafx transparent writes to speed up pixel plotting in gfx2 and monogfx modules (avoids read/mask/write)
- port gfx2 to the vm?  First add colors to the vm?
- fix the problems in atari target, and flesh out its libraries.
- c128 target: make syslib more complete (missing kernal routines)?
- pet32 target: make syslib more complete (missing kernal routines)?


Optimizations:

- give a warning for variables that could be a const - or even make them a const (if not @shared)?
- treat every scalar variable decl with initialization value, as const by default, unless the variable gets assigned to somewhere (or has its address taken, or is @shared)
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
