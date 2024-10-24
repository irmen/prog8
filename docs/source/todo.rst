TODO
====

Improve register load order in subroutine call args assignments:
in certain situations, the "wrong" order of evaluation of function call arguments is done which results
in overwriting registers that already got their value, which requires a lot of stack juggling (especially on plain 6502 cpu!)
Maybe this routine can be made more intelligent.  See usesOtherRegistersWhileEvaluating() and argumentsViaRegisters().


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
- Improve the SublimeText syntax file for prog8, you can also install this for 'bat': https://github.com/sharkdp/bat?tab=readme-ov-file#adding-new-syntaxes--language-definitions
- Can we support signed % (remainder) somehow?
- Don't add "random" rts to %asm blocks but instead give a warning about it? (but this breaks existing behavior that others already depend on... command line switch? block directive?)
- IR: implement missing operators in AssignmentGen  (array shifts etc)
- instead of copy-pasting inline asmsubs, make them into a 64tass macro and use that instead.
  that will allow them to be reused from custom user written assembly code as well.
- Multidimensional arrays and chained indexing, purely as syntactic sugar over regular arrays. Probaby only useful if we have typed pointers.
- make a form of "manual generics" possible like: varsub routine(T arg)->T  where T is expanded to a specific type
  (this is already done hardcoded for several of the builtin functions)

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
- ir: related to the one above: block alignment doesn't translate well to variables in the block (the actual stuff that needs to be aligned in memory)  but: need variable alignment tag instead of block alignment tag, really
- ir: fix call() return value handling
- ir: proper code gen for the CALLI instruction and that it (optionally) returns a word value that needs to be assigned to a reg
- ir: idea: (but LLVM IR simply keeps the variables, so not a good idea then?...): replace all scalar variables by an allocated register. Keep a table of the variable to register mapping (including the datatype)
  global initialization values are simply a list of LOAD instructions.
  Variables replaced include all subroutine parameters!  So the only variables that remain as variables are arrays and strings.
- ir: add more optimizations in IRPeepholeOptimizer
- ir: the @split arrays are currently also split in _lsb/_msb arrays in the IR, and operations take multiple (byte) instructions that may lead to verbose and slow operation and machine code generation down the line.
  maybe another representation is needed once actual codegeneration is done from the IR...?
- ir: getting it in shape for code generation...
- ir: make optimizeBitTest work for IR too to use the BIT instruction?
- ir: make sure that a 6502 codegen based off the IR, still generates BIT instructions when testing bit 7 or 6 of a byte var.
- [problematic due to using 64tass:] better support for building library programs, where unused .proc are NOT deleted from the assembly.
  Perhaps replace all uses of .proc/.pend/.endproc by .block/.bend will fix that with a compiler flag?
  But all library code written in asm uses .proc already..... (textual search/replace when writing the actual asm?)
  Once new codegen is written that is based on the IR, this point is mostly moot anyway as that will have its own dead code removal on the IR level.
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions


Libraries:

- gfx2: add EOR mode support like in monogfx and see PAINT for inspiration.  Self modifying code to keep it optimized?
- fix the problems in atari target, and flesh out its libraries.
- c128 target: make syslib more complete (missing kernal routines)?
- pet32 target: make syslib more complete (missing kernal routines)?
- VM: implement more diskio support


Optimizations:

- Optimize the IfExpression code generation to be more like regular if-else code.  (both 6502 and IR) search for "TODO don't store condition as expression"
- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers (or these first??), then the rest
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code,
  those checks should probably be removed, or be made permanent


STRUCTS?
--------

- declare struct *type*, or directly declare the variable itself?  Problem with the latter is: you cannot easily define multiple variables of the same struct type.
- can contain only numeric types (byte,word,float) - no nested structs, no reference types (strings, arrays) inside structs
- only as a reference type (uword pointer). This removes a lot of the problems related to introducing a variable length value type.
- arrays of struct is just an array of uword pointers. Can even be @split?
- need to introduce typed pointer datatype in prog8
- STR remains the type for a string literal (so we can keep doing register-indexed addressing directly on it)
- ARRAY remains the type for an array literal (so we can keep doing register-indexed addressing directly on it)
- we probably need to have a STRBYREF and ARRAYBYREF if we deal with a pointer to a string / array (such as when passing it to a function)
  the subtype of those should include the declared element type and the declared length of the string / array


Other language/syntax features to think about
---------------------------------------------

- add (rom/ram)bank support to romsub.   A call will then automatically switch banks, use callfar and something else when in banked ram.
  challenges: how to not make this too X16 specific? How does the compiler know what bank to switch (ram/rom)?
  How to make it performant when we want to (i.e. NOT have it use callfar/auto bank switching) ?
  Maybe by having a %option rombank=4 rambank=22   to set that as fixed rombank/rambank for that subroutine/block (and pray the user doesn't change it themselves)
  and then only do bank switching if the bank of the routine is different from the configured rombank/rambank.
