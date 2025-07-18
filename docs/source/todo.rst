TODO
====

STRUCTS and TYPED POINTERS
--------------------------

'DONE' means working in the 'virtual' compiler target... (no 6502 codegen has been touched yet)

- DONE: add ast type check for assignments to struct fields;  node_ptr.nextnode = enemy_ptr should error
- DONE: declare struct as a separate entity so you can then declare multiple variables (pointers) of the same struct type. Like usual.
- DONE: struct is a 'packed' struct, fields are placed in order of declaration. This guarantees exact size and place of the fields
- DONE: structs only supported as a reference type (uword pointer). This removes a lot of the problems related to introducing a variable length value type.
- DONE: need to introduce typed pointer datatype in prog8 to allow this to make any sense. Syntax to declare a pointer type: ^^datatype   (double hat to avoid parsing confusion with the eor operator)
- DONE: initially only a pointer-to-struct should actually work, pointer-to-other-type is possible but that can come later.
- DONE: a struct can contain only numeric type fields (byte,word,float) or str fields (translated into ^^ubyte) or other pointer fields. No nested structs, no arrays.
- DONE: max 1 page of memory total size to allow regular register indexing
- DONE: assigning ptrs of different types is only allowed via a cast as usual. For simple address (uword) assignments, no cast is needed (but allowed)
- DONE: how to dereference a pointer?  Pascal does it like this: ptr^  But this conflicts with the existing eor operator so we now use ptr^^^  (double hat)
- DONE: dereferencing a pointer to struct could look like Pascal's ptr^.field  as well, but the ^ is actually redundant here; compiler already knows it's a pointer type.
  Note that actually dereferencing a pointer to a struct as an explicit operation, conflicts with the third axiom on this list (structs only as reference types) so it can only be done for basic types?
  So... setting struct fields can simply be ``structvar.field = 42`` and reading them ``a = structvar.field``
- DONE: you should be able to get the address of an individual field: ``&structpointer.field``
- DONE: teach sizeof() how to calculate struct sizes (need unit test + doc)
- DONE: sizeof(ptr^^) works
- DONE: implicit cast of pointer to uword in conditional expressions
- DONE: subroutine parameters and return values should be able to accept pointers as well now
- DONE (for basic types only): allow array syntax on pointers too: ptr[2]  means ptr+sizeof()*2,   ptr[0]  just means  ptr^^  .
- DONE (?) allow array syntax on pointers to structs too, but what type will ptr[2] have? And it will require  ptr[2].field  to work as well now. Actually that will be the only thing to work for now.
- DONE: allow multi-field declarations in structs
- DONE: static initialization of structs. It behaves like arrays; it won't reset to the original value when program is restarted, so beware.
  Syntax:  ^^Node ptr = Node(1,2,3,4) statically allocates a Node with fields set to 1,2,3,4 and puts the address in ptr.
  Node() without arguments allocates a node in BSS variable space instead that gets zeroed out at startup.
  ()Internally this gets translated into a structalloc(1,2,3,4)  builtin function call that has a pointer to the struct as its return type)
- DONE: pointer arrays are split-words only, enforce this (variable dt + initializer array dt)
- DONE: make an error message for all pointer expressions (prefixed, binary) so we can start implementing the ones we need one by one.
- DONE: start by making ptr.value++ work  , and  ptr.value = ptr.value+20,   and ptr.value = cx16.r0L+20+ptr.value   Likewise for subtraction.  DON'T FORGET C POINTER SEMANTICS.   Other operators are nonsensical for ptr arith
- DONE: support @dirty on pointer vars -> uninitialized pointer placed in BSS_noclear segment
- DONE: support comparison operators on pointers
- DONE: implement augmented assignment on pointer dereference
- DONE: pointer types in subroutine signatures (both normal and asm-subs, parameters and return values)
- DONE: arrays of structs? No -> Just an array of uword pointers to said structs.
- DONE: what about pointers to subroutines? should these be typed as well now? Probably not, just stick with UWORD untyped pointer to avoid needless complexity.
- DONE: implement inplace logical and & or, with short-cirtuit, on dereferenced pointer
- DONE: existing ARRAY type remains unchanged (it doesn't become a typed pointer) so we can keep doing register-indexed LDA array,Y addressing directly on them.
- DONE: passing STR to a subroutine: parameter type becomes ^^UBYTE  (rather than UWORD)  (we still lose the bounds check)
- DONE: passing ARRAY to a subroutine: parameter type becomes ^^ElementDt  (rather than UWORD)  (we still lose the bounds check)
- DONE: @(ptr) complains that ptr is not uword when ptr is ^^ubyte (should be allowed)
- DONE: pointer[0] should be replaced with @(pointer)  if pointer is ^^ubyte,   so these are now all identical:  ptr[0], ptr^^, @(ptr)   if ptr is ^^ubyte
- DONE: STR should be asssignment compatible with UBYTE^^ but local scoped STR should still be accessed directly using LDA str,Y instead of through the pointer, like arrays.
- DONE: replace ^^str by ^^ubyte
- DONE: allow return ubyte/uword when pointer type is expected as return value type
- DONE: fix _msb/_lsb storage of the split-words pointer-arrays
- DONE: what about static initialization of an array of struct pointers? -> impossible right now because the pointer values are not constants.
- DONE: make typeForAddressOf() be even more specific about the typed pointers it returns for the address-of operator.
- DONE: existing '&' address-of still returns untyped uword (for backward compatibility). New '&&' operator returns typed pointer.
- DONE: allow  list1^^ = list2^^  (value wise assignment of List structures) by replacing it with a sys.memcopy(list2, list1, sizeof(List)) call.
- DONE: allow  a.b.ptr[i].value  (equiv to a.b.ptr[i]^^.value)  expressions  (assignment target doesn't parse yet, see below)
- DONE: check passing arrays to typed ptr sub-parameters.  NOTE: word array can only be a @nosplit array if the parameter type is ^^word, because the words need to be sequential in memory there
- DONE: allow str assign to ^^ubyte without cast (take address)
- DONE: added peekbool() and pokebool() and pokebowl()  boolean peek and poke, the latter is equivalent to pokebool()
- DONE: fixed support for (expression) array index dereferencing "array[2]^^"   where array contains pointers to primitives: replace with peek()
- DONE: fixed support for (assigntarget) array index dereferencing "array[2]^^"   where array contains pointers to primitives: replace with poke()
- write docs in structpointers.rst
- scan through virtual library modules to change untyped uword pointers to typed pointers: compression, conv, diskio, math, sorting, strings, syslib, textio.
- add support for array index dereferencing as assign target "array[2]^^.value = 99"   where array is struct pointers (currently a 'no support' error)
- add support for array index dereferencing as assign target "array[2].value = 99"   where array is struct pointers (currently a parser error)
- try to fix parse error  l1^^.s[0] = 4242   (equivalent to l1.s[0]=4242 , which does parse correctly)
- try to make sizeof(^^type) parse correctly (or maybe replace it immediately with sys.SIZEOF_POINTER)
- add ?. null-propagation operator (for expression and assignment)?
- 6502 codegen: remove checks in checkForPointerTypesOn6502()
- 6502 codegen should warn about writing to initialized struct instances when using romable code, like with arrays "can only be used as read-only in ROMable code"
- 6502 asm symbol name prefixing should work for dereferences too.
- update structpointers.rst docs with 6502 things?
- scan through 6502 library modules to change untyped uword pointers to typed pointers
- scan through 6502 examples to change untyped uword pointers to typed pointers
- really fixing the pointer dereferencing issues (cursed hybrid beween IdentifierReference, PtrDereferece and PtrIndexedDereference) may require getting rid of scoped identifiers altogether and treat '.' as a "scope or pointer following operator"
- (later, nasty parser problem:) support chaining pointer dereference on function calls that return a pointer.  (type checking now fails on stuff like func().field and func().next.field)


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^

- when a complete block is removed because unused, suppress all info messages about everything in the block being removed
- fix the line, cols in Position, sometimes they count from 0 sometimes from 1
- is "checkAssignmentCompatible" redundant (gets called just 1 time!) when we also have "checkValueTypeAndRange" ?
- enums?
- romable: should we have a way to explicitly set the memory address for the BSS area (add a -varsaddress and -slabsaddress options?)
- romable: fix remaining codegens (some for loops, see ForLoopsAsmGen)
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
- Multidimensional arrays and chained indexing, purely as syntactic sugar over regular arrays. Probaby only useful once we have typed pointers. (addressed in 'struct' branch)
- make a form of "manual generics" possible like: varsub routine(T arg)->T  where T is expanded to a specific type
  (this is already done hardcoded for several of the builtin functions)
- [much work:] more support for (64tass) SEGMENTS in the prog8 syntax itself?
- ability to use a sub instead of only a var for @bank ? what for though? dynamic bank/overlay loading?
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions


IR/VM
-----
- possible to use LOADFIELD/STOREFIELD instructions more?
- change the instruction format so an indirect register (a pointer) can be used more often, at least for the inplace assignment operators that operate on pointer
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
- apparently for SSA form, the IRCodeChunk is not a proper "basic block" yet because the last operation should be a branch or return, and no other branches
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
- See if the raster interrupt handler on the C64 can be tweaked to be a more stable raster irq
- pet32 target: make syslib more complete (missing kernal routines)?
- need help with: PET disk routines (OPEN, SETLFS etc are not exposed as kernal calls)
- c128 target: make syslib more complete (missing kernal routines)?


Optimizations
-------------

- if expression generates more instructions than old style if else (IR):
    pp.next = if particles!=0 particles else 0
      versus:
    if particles!=0
        pp.next = particles
    else
        pp.next = 0

- in Identifier: use typedarray of strings instead of listOf? Other places?
- Compilation speed: try to join multiple modifications in 1 result in the AST processors instead of returning it straight away every time
- Compare output of some Oscar64 samples to what prog8 does for the equivalent code (see https://github.com/drmortalwombat/OscarTutorials/tree/main and https://github.com/drmortalwombat/oscar64/tree/main/samples)
- Optimize the IfExpression code generation to be more like regular if-else code.  (both 6502 and IR) search for "TODO don't store condition as expression"
- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers (or these first??), then the rest
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code, those checks should probably be removed, or be made permanent
