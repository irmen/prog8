TODO
====

- DONE: make word arrays split by default and add new @nosplit tag to make an array use the old linear storage format
- DONE: &splitarray  will give you the start address of the lsb-array (which is immediately followed by the msb-array)
- DONE: add &< and &> operators to get the address of the lsb-array and msb-array, respectively.  (&< is just syntactic sugar for &)
- DONE: invert -splitarrays command line option: -dontsplitarrays   and remove "splitarrays" %option switch
- DONE: added sprites.pos_batch_nosplit  when the x/y arrays are linear instead of split word arrays
- DONE: add palette.set_rgb_nosplit() and set_rbg_be_nosplit()  for linear instead of split word arrays
- DONE: removed anyall module (unoptimized and didn't work on split arrays)
- DONE: @split does now always splits a word array even when the dontsplit option is enabled (and @nosplit does the inverse)

- bump version and renegerate symbol dumps

- announce prog8 on the 6502.org site?

...


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^

- SourceLineCache should be removed as a separate thing, it reloads all source files again and splits them by line. It should re-use the already loaded Sources. Wrap it all in a ImporterFileSystem?
- Compiling Libraries: improve ability to create library files in prog8; for instance there's still stuff injected into the start of the start() routine AND there is separate setup logic going on before calling it.
  Make up our mind! Maybe all setup does need to be put into start() ? because the program cannot function correctly when the variables aren't initialized properly bss is not cleared etc. etc.
  Add a -library $xxxx command line option (and/or some directive) to preselect every setting that is required to make a library at $xxxx rather than a normal loadable and runnable program?
  Need to add some way to generate a stable jump table at a given address.
  Need library to not call init_system AND init_system_phase2 not either.
  Library must not include prog8_program_start stuff either.
- Fix missing cases where regular & has to return the start of the split array in memory whatever byte comes first. Search TODO("address of split word array")
- Add a syntax to access specific bits in a variable, to avoid manually shifts&ands, something like  variable[4:8] ?  (or something else this may be too similar to regular array indexing)
- something to reduce the need to use fully qualified names all the time. 'with' ?  Or 'using <prefix>'?
- Improve register load order in subroutine call args assignments:
  in certain situations, the "wrong" order of evaluation of function call arguments is done which results
  in overwriting registers that already got their value, which requires a lot of stack juggling (especially on plain 6502 cpu!)
  Maybe this routine can be made more intelligent.  See usesOtherRegistersWhileEvaluating() and argumentsViaRegisters().
- Improve the SublimeText syntax file for prog8, you can also install this for 'bat': https://github.com/sharkdp/bat?tab=readme-ov-file#adding-new-syntaxes--language-definitions
- Does it make codegen easier if everything is an expression?  Start with the PtProgram ast , get rid of the statements there -> expressions that have Void data type
- Can we support signed % (remainder) somehow?
- instead of copy-pasting inline asmsubs, make them into a 64tass macro and use that instead.
  that will allow them to be reused from custom user written assembly code as well.
- Multidimensional arrays and chained indexing, purely as syntactic sugar over regular arrays. Probaby only useful if we have typed pointers.
- make a form of "manual generics" possible like: varsub routine(T arg)->T  where T is expanded to a specific type
  (this is already done hardcoded for several of the builtin functions)
- [much work:] more support for (64tass) SEGMENTS ?
- [problematic due to using 64tass:] better support for building library programs, where unused .proc are NOT deleted from the assembly.
  Perhaps replace all uses of .proc/.pend/.endproc by .block/.bend will fix that with a compiler flag?
  But all library code written in asm uses .proc already..... (textual search/replace when writing the actual asm?)
  Once new codegen is written that is based on the IR, this point is mostly moot anyway as that will have its own dead code removal on the IR level.
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions


IR/VM
-----
- ExpressionCodeResult:  get rid of the separation between single result register and multiple result registers?
- constants are not retained in the IR file, they should. (need to be able to make asm labels from them eventually)
- implement missing operators in AssignmentGen  (array shifts etc)
- support %align on code chunks
- fix call() return value handling
- fix float register parameters (FAC1,FAC2) for extsubs, search for TODO("floating point register parameters not supported")
- proper code gen for the CALLI instruction and that it (optionally) returns a word value that needs to be assigned to a reg
- make it possible to jump and branch to a computed address (expression) in all cases, see TODO("JUMP to expression address"
- idea: (but LLVM IR simply keeps the variables, so not a good idea then?...): replace all scalar variables by an allocated register. Keep a table of the variable to register mapping (including the datatype)
  global initialization values are simply a list of LOAD instructions.
  Variables replaced include all subroutine parameters!  So the only variables that remain as variables are arrays and strings.
- add more optimizations in IRPeepholeOptimizer
- the @split arrays are currently also split in _lsb/_msb arrays in the IR, and operations take multiple (byte) instructions that may lead to verbose and slow operation and machine code generation down the line.
  maybe another representation is needed once actual codegeneration is done from the IR...?
- split word arrays, both _msb and _lsb arrays are tagged with an alignment. This is not what's intended; only the one put in memory first should be aligned (the other one should follow straight after it)
- getting it in shape for code generation...
- make optimizeBitTest work for IR too to use the BIT instruction?
- make sure that a 6502 codegen based off the IR, still generates BIT instructions when testing bit 7 or 6 of a byte var.


Libraries
---------
- monogfx: flood fill should be able to fill stippled
- Add in-place TSCrunch decoder routine as well to compression lib?  May come in handy where you load a block of compressed data, decompress it in place in the same buffer/memory bank
- pet32 target: make syslib more complete (missing kernal routines)?
- need help with: PET disk routines (OPEN, SETLFS etc are not exposed as kernal calls)
- fix the problems in atari target, and flesh out its libraries.
- c128 target: make syslib more complete (missing kernal routines)?
- VM: implement the last diskio support (file listings)


Optimizations
-------------
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
