TODO
====

STRUCTS and TYPED POINTERS
--------------------------

'DONE' means working in the 'virtual' compiler target... (no 6502 codegen has been touched yet)

- implicit cast of pointer to bool, also in loop conditions  (while ptr {...})
- implicit cast of pointer to uword in conditional expressoins
- DONE: add ast type check for assignments to struct fields;  node_ptr.nextnode = enemy_ptr should error
- add IR LOADPIX/STOREPIX instructions for efficient field access through a pointer var?
- change IR instruction LOADI should allow reg1 and reg2 to be the same, so we can remove the extra 'newPointerReg'.
- DONE: declare struct as a separate entity so you can then declare multiple variables (pointers) of the same struct type. Like usual.
- DONE: struct is a 'packed' struct, fields are placed in order of declaration. This guarantees exact size and place of the fields
- DONE: structs only supported as a reference type (uword pointer). This removes a lot of the problems related to introducing a variable length value type.
- DONE: need to introduce typed pointer datatype in prog8 to allow this to make any sense. Syntax to declare a pointer type: ^^datatype   (double hat to avoid parsing confusion with the eor operator)
- DONE: initially only a pointer-to-struct should actually work, pointer-to-other-type is possible but that can come later.
- DONE: a struct can contain only numeric type fields (byte,word,float) - no nested structs, no reference types (strings, arrays) inside structs.
- DONE: struct might also contain typed pointer fields (because a pointer is just an address word)
- DONE: max 1 page of memory total size to allow regular register indexing
- DONE: assigning ptrs of different types is only allowed via a cast as usual. For simple address (uword) assignments, no cast is needed (but allowed)
- DONE: how to dereference a pointer?  Pascal does it like this: ptr^  But this conflicts with the existing eor operator so we now use ptr^^^  (double hat)
- DONE: dereferencing a pointer to struct could look like Pascal's ptr^.field  as well, but the ^ is actually redundant here; compiler already knows it's a pointer type.
  Note that actually dereferencing a pointer to a struct as an explicit operation, conflicts with the third axiom on this list (structs only as reference types) so it can only be done for basic types?
  So... setting struct fields can simply be ``structvar.field = 42`` and reading them ``a = structvar.field``
- DONE: you should be able to get the address of an individual field: ``&structpointer.field``
- DONE: need to teach sizeof() how to calculate struct sizes (need unit test + doc)
- subroutine parameters should be able to accept pointers as well now
- arrays of structs?  Just an array of uword pointers to said structs. Can even be @split as the only representation form because that's the default for word arrays.
- static initialization of structs may be allowed only at block scope and then behaves like arrays; it won't reset to the original value when program is restarted, so beware.  Syntax = TBD
- allow memory-mapped structs?  Something like &Sprite sprite0 = $9000   basically behaves identically to a typed pointer, but the address is immutable as usual
- existing STR and ARRAY remain unchanged (don't become typed pointers) so we can keep doing register-indexed addressing directly on them
- rather than str or uword parameter types for routines with a string argument, use ^^str  (or ^^ubyte maybe? these are more or less identical..?)
- same for arrays? pointer-to-array syntax = TBD
- what about pointers to subroutines? should these be typed as well now?
- asm symbol name prefixing should work for dereferences too.
- pointer arithmetic is a pain, but need to follow C?  ptr=ptr+10 adds 10*sizeof() instead of just 10.


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^

- is "checkAssignmentCompatible" redundant (gets called just 1 time!) when we also have "checkValueTypeAndRange" ?
- romable: should we have a way to explicitly set the memory address for the BSS area (instead of only the highram bank number on X16, allow a memory address too for the -varshigh option?)
- Kotlin: can we use inline value classes in certain spots?
- add float support to the configurable compiler targets
- Improve the SublimeText syntax file for prog8, you can also install this for 'bat': https://github.com/sharkdp/bat?tab=readme-ov-file#adding-new-syntaxes--language-definitions
- Change scoping rules for qualified symbols so that they don't always start from the root but behave like other programming languages (look in local scope first), maybe only when qualified symbol starts with '.' such as: .local.value = 33
- something to reduce the need to use fully qualified names all the time. 'with' ?  Or 'using <prefix>'?
- Improve register load order in subroutine call args assignments:
  in certain situations (need examples!), the "wrong" order of evaluation of function call arguments is done which results
  in overwriting registers that already got their value, which requires a lot of stack juggling (especially on plain 6502 cpu!)
  Maybe this routine can be made more intelligent.  See usesOtherRegistersWhileEvaluating() and argumentsViaRegisters().
- Does it make codegen easier if everything is an expression?  Start with the PtProgram ast classes, change statements to expressions that have (new) VOID data type
- Can we support signed % (remainder) somehow?
- Multidimensional arrays and chained indexing, purely as syntactic sugar over regular arrays. Probaby only useful if we have typed pointers. (addressed in 'struct' branch)
- make a form of "manual generics" possible like: varsub routine(T arg)->T  where T is expanded to a specific type
  (this is already done hardcoded for several of the builtin functions)
- [much work:] more support for (64tass) SEGMENTS in the prog8 syntax itself?
- ability to use a sub instead of only a var for @bank ? what for though? dynamic bank/overlay loading?
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions


IR/VM
-----
- instruction LOADI should allow reg1 and reg2 to be the same, so we can remove the extra 'newPointerReg' in the pointer chain traversals.
- getting it in shape for code generation...: the IR file should be able to encode every detail about a prog8 program (the VM doesn't have to actually be able to run all of it though!)
- fix call() return value handling (... what's wrong with it again?)
- encode asmsub/extsub clobber info in the call , or maybe include these definitions in the p8ir file itself too.  (return registers are already encoded in the CALL instruction)
- proper code gen for the CALLI instruction and that it (optionally) returns a word value that needs to be assigned to a reg
- implement fast code paths for TODO("inplace split....
- implement more TODOs in AssignmentGen
- sometimes source lines end up missing in the output p8ir, for example the first assignment is gone in:
     sub start() {
     cx16.r0L = cx16.r1 as ubyte
     cx16.r0sL = cx16.r1s as byte }
- do something with the 'split' tag on split word arrays
- add more optimizations in IRPeepholeOptimizer
- reduce register usage via linear-scan algorithm (based on live intervals) https://anoopsarkar.github.io/compilers-class/assets/lectures/opt3-regalloc-linearscan.pdf
  don't forget to take into account the data type of the register when it's going to be reused!
- idea: (but LLVM IR simply keeps the variables, so not a good idea then?...): replace all scalar variables by an allocated register. Keep a table of the variable to register mapping (including the datatype)
  global initialization values are simply a list of LOAD instructions.
  Variables replaced include all subroutine parameters!  So the only variables that remain as variables are arrays and strings.
- the @split arrays are currently also split in _lsb/_msb arrays in the IR, and operations take multiple (byte) instructions that may lead to verbose and slow operation and machine code generation down the line.
  maybe another representation is needed once actual codegeneration is done from the IR...?
- ExpressionCodeResult:  get rid of the separation between single result register and multiple result registers? maybe not, this requires hundreds of lines to change


Libraries
---------
- Add split-word array sorting routines to sorting module?
- cx16: _irq_dispatcher  now only dispatches a single irq source, better to ROL/BCC to handle *all* possible (multiple) sources.
- See if the raster interrupt handler on the C64 can be tweaked to be a more stable raster irq
- pet32 target: make syslib more complete (missing kernal routines)?
- need help with: PET disk routines (OPEN, SETLFS etc are not exposed as kernal calls)
- c128 target: make syslib more complete (missing kernal routines)?


Optimizations
-------------

- Sorting module gnomesort_uw could be optimized more by fully rewriting it in asm? Shellshort seems consistently faster even if most of the words are already sorted.
- Compare output of some Oscar64 samples to what prog8 does for the equivalent code (see https://github.com/drmortalwombat/OscarTutorials/tree/main and https://github.com/drmortalwombat/oscar64/tree/main/samples)
- Optimize the IfExpression code generation to be more like regular if-else code.  (both 6502 and IR) search for "TODO don't store condition as expression"
- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers (or these first??), then the rest
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code,
  those checks should probably be removed, or be made permanent
