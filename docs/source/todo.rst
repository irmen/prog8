TODO
====

- IR: Multi-value returns of normal subroutines: use cpu register A or AY for the first one and only start using virtual registers for the rest.  see TODO("fix A/AY for first value")

...


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^

- Look at github PR for improved romability (see github issue 149)
- Kotlin: can we use inline value classes in certain spots?
- add float support to the configurable compiler targets
- Improve the SublimeText syntax file for prog8, you can also install this for 'bat': https://github.com/sharkdp/bat?tab=readme-ov-file#adding-new-syntaxes--language-definitions
- [problematic due to using 64tass:] better support for building library programs, where unused .proc are NOT deleted from the assembly.
  Perhaps replace all uses of .proc/.pend/.endproc by .block/.bend will fix that with a compiler flag?
  But all library code written in asm uses .proc already..... (textual search/replace when writing the actual asm?)
  Maybe propose a patch to 64tass itself that will treat .proc as .block ?
  Once new codegen is written that is based on the IR, this point is mostly moot anyway as that will have its own dead code removal on the IR level.

- Change scoping rules for qualified symbols so that they don't always start from the root but behave like other programming languages (look in local scope first)
- something to reduce the need to use fully qualified names all the time. 'with' ?  Or 'using <prefix>'?
- Improve register load order in subroutine call args assignments:
  in certain situations (need examples!), the "wrong" order of evaluation of function call arguments is done which results
  in overwriting registers that already got their value, which requires a lot of stack juggling (especially on plain 6502 cpu!)
  Maybe this routine can be made more intelligent.  See usesOtherRegistersWhileEvaluating() and argumentsViaRegisters().
- Does it make codegen easier if everything is an expression?  Start with the PtProgram ast , get rid of the statements there -> expressions that have Void data type
- Can we support signed % (remainder) somehow?
- instead of copy-pasting inline asmsubs, make them into a 64tass macro and use that instead.
  that will allow them to be reused from custom user written assembly code as well.
- Multidimensional arrays and chained indexing, purely as syntactic sugar over regular arrays. Probaby only useful if we have typed pointers.
- make a form of "manual generics" possible like: varsub routine(T arg)->T  where T is expanded to a specific type
  (this is already done hardcoded for several of the builtin functions)
- [much work:] more support for (64tass) SEGMENTS ?
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions


IR/VM
-----
- getting it in shape for code generation...: the IR file should be able to encode every detail about a prog8 program (the VM doesn't have to actually be able to run all of it though!)
- fix call() return value handling
- proper code gen for the CALLI instruction and that it (optionally) returns a word value that needs to be assigned to a reg
- implement fast code paths for TODO("inplace split....
- implement more TODOs in AssignmentGen
- sometimes source lines end up missing in the output p8ir, for example the first assignment is gone in:
     sub start() {
     cx16.r0L = cx16.r1 as ubyte
     cx16.r0sL = cx16.r1s as byte }
- do something with the 'split' tag on split word arrays
- add more optimizations in IRPeepholeOptimizer
- idea: (but LLVM IR simply keeps the variables, so not a good idea then?...): replace all scalar variables by an allocated register. Keep a table of the variable to register mapping (including the datatype)
  global initialization values are simply a list of LOAD instructions.
  Variables replaced include all subroutine parameters!  So the only variables that remain as variables are arrays and strings.
- the @split arrays are currently also split in _lsb/_msb arrays in the IR, and operations take multiple (byte) instructions that may lead to verbose and slow operation and machine code generation down the line.
  maybe another representation is needed once actual codegeneration is done from the IR...?
- ExpressionCodeResult:  get rid of the separation between single result register and multiple result registers? maybe not, this requires hundreds of lines to change


Libraries
---------
- Sorting module gnomesort_uw could be optimized more by fully rewriting it in asm? Shellshort seems consistently faster even if most of the words are already sorted.
- Add split-word array sorting routines to sorting module?
- See if the raster interrupt handler on the C64 can be tweaked to be a more stable raster irq
- add even more general raster irq routines to build some sort of "copper list" , like Oscar64 has?
- pet32 target: make syslib more complete (missing kernal routines)?
- need help with: PET disk routines (OPEN, SETLFS etc are not exposed as kernal calls)
- c128 target: make syslib more complete (missing kernal routines)?
- VM: implement the last diskio support (file listings)


Optimizations
-------------

- Compare output of some Oscar64 samples to what prog8 does for the equivalent code (see https://github.com/drmortalwombat/OscarTutorials/tree/main and https://github.com/drmortalwombat/oscar64/tree/main/samples)
- Multi-value returns of normal subroutines: Can FAC then be used for floats as well again? Those are now not supported for multi-value returns.
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
