TODO
====

- cbm.SETTIML(0) generates invalid asm on cx16/65c02 (stz r0)
- cx16: add support for VIA timer IRQ in the irq routines, example code for timer irqs see https://cx16forum.com/forum/viewtopic.php?p=37808

Weird Heisenbug
^^^^^^^^^^^^^^^
- BUG: examples/cube3d-float crashes with div by zero error on C64 (works on cx16. ALready broken in v11, v10 still worked)
  caused by the RTS after JMP removal in optimizeJsrRtsAndOtherCombinations (replacing it with a NOP makes the problem disappear !??!?)


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
- BUG: structs: properly fix the symbol name prefix hack in StStruct.sameas(), see github issue 198
- when implementing unsigned longs: remove the (multiple) "TODO "hack" to allow unsigned long constants to be used as values for signed longs, without needing a cast"
- struct/ptr: support const pointers (simple and struct types) (make sure to change codegen properly in all cases, change remark about this limitation in docs too)
- struct/ptr: implement the remaining TODOs in PointerAssignmentsGen.
- struct/ptr: optimize deref in PointerAssignmentsGen: optimize 'forceTemporary' to only use a temporary when the offset is >0
- struct/ptr: optimize the float copying in assignIndexedPointer() (also word and long?)
- struct/ptr: optimize augmented assignments to indexed pointer targets like sprptr[2]^^.y++  (these are now not performend in-place but as a regular assignment)
- struct/ptr: implement even more struct instance assignments (via memcopy) in CodeDesugarer (see the TODO) (add to documentation as well, paragraph 'Structs')
- struct/ptr: support @nosplit pointer arrays?
- struct/ptr: support pointer to pointer?
- struct/ptr: support for typed function pointers?  (&routine could be typed by default as well then)
- struct/ptr: really fixing the pointer dereferencing issues (cursed hybrid beween IdentifierReference, PtrDereferece and PtrIndexedDereference) may require getting rid of scoped identifiers altogether and treat '.' as a "scope or pointer following operator"
- struct/ptr: (later, nasty parser problem:) support chaining pointer dereference on function calls that return a pointer.  (type checking now fails on stuff like func().field and func().next.field)
- should we have a SourceStorageKind.POINTER?   (there is one for TargetStorageKind...)
- make memory mapped variables support more constant expressions such as:  &uword  MyHigh = &mylong1+2
- allow memory() to occur in array initializer (maybe needed for 2 dimensional arrays?) i.e. make it a constant (see github issue #192)
- handle Alias in a general way in LiteralsToAutoVarsAndRecombineIdentifiers instead of replacing it scattered over multiple functions
- Make all constants long by default? or not? (remove type name altogether), reduce to target type implictly if the actual value fits.  Experiment is in branch 'long-consts'
  This will break some existing programs that depend on value wrap arounds, but gives more intuitive constant number handling.
  Can give descriptive error message for old syntax that still includes the type name?
- improve ANTLR grammar with better error handling (as suggested by Qwen AI)
- add documentation for more library modules instead of just linking to the source code
- add an Index to the documentation
- sizeof(pointer) is now always 2 (an uword), make this a variable in the ICompilationTarget so that it could be 4 at the time we might ad a 32-bits 68000 target for example. Much code assumes word size addresses though.
- Two- or even multidimensional arrays and chained indexing, purely as syntactic sugar over regular arrays?
- when a complete block is removed because unused, suppress all info messages about everything in the block being removed
- is "checkAssignmentCompatible" redundant (gets called just 1 time!) when we also have "checkValueTypeAndRange" ?
- romable: should we have a way to explicitly set the memory address for the BSS area (add a -varsaddress and -slabsaddress options?)
- romable: fix remaining codegens (some for loops, see ForLoopsAsmGen)
- Kotlin: can we use inline value classes in certain spots? (domain types instead of primitives)
- add float support to the configurable compiler targets. Restrictions: just have "cbm-style floats" as an option (to that it can slot into the current float codegen), where all you have to specify is the addresses of AYINT and GIVAYF and FADDT and all their friends.
- Improve the SublimeText syntax file for prog8, you can also install this for 'bat': https://github.com/sharkdp/bat?tab=readme-ov-file#adding-new-syntaxes--language-definitions
- Change scoping rules for qualified symbols so that they don't always start from the root but behave like other programming languages (look in local scope first), maybe only when qualified symbol starts with '.' such as: .local.value = 33
- something to reduce the need to use fully qualified names all the time. 'with' ?  Or 'using <prefix>'?
- detect circular aliases and print proper error message for them
- Improve register load order in subroutine call args assignments:
  in certain situations (need examples!), the "wrong" order of evaluation of function call arguments is done which results
  in overwriting registers that already got their value, which requires a lot of stack juggling (especially on plain 6502 cpu!)
  Maybe this routine can be made more intelligent.  See usesOtherRegistersWhileEvaluating() and argumentsViaRegisters().
- Does it make codegen easier if everything is an expression?  Start with the PtProgram ast classes, change statements to expressions that have (new) VOID data type. BUT probably not worth it if a new codegen is going to be based on the IR
- Can we support signed % (remainder) somehow?
- make a form of "manual generics" possible like: varsub routine(T arg)->T  where T is expanded to a specific type
  (this is already done hardcoded for several of the builtin functions)
- more support for (64tass) SEGMENTS in the prog8 syntax itself? maybe %segment blah  in blocks?
- ability to use a sub instead of only a var for @bank ? what for though? dynamic bank/overlay loading?
- enums?
- BUG: fix the c64 multiplexer example
- Zig-like try-based error handling where the V flag could indicate error condition? and/or BRK to jump into monitor on failure? (has to set BRK vector for that) But the V flag is also set on certain normal instructions


IR/VM
-----
- extend the index register datatype in the LOADX, STOREX, STOREZX instructions from byte to word (0-255 to 0-65535) (this not compatible with 8 bit 6502, but the 68000 can use that)
- get rid of LOADX/STOREX/STOREZX, LOADFIELD/STOREFIELD just use add + loadi / storei?
- if float<0 / if word<0  uses sgn or load, but still use a bgt etc instruction after that with a #0 operand even though the sgn and load instructions sets the status bits already, so just use bstneg etc
- add and sub instructions should modify the status flags so an explicit compare to zero can be avoided for example: if cx16.r0sL + cx16.r1sL <= 0  now compiles into:  addr.b r10,r11 /  bgts.b r10,#0,label
- getting it in shape for code generation: the IR file should be able to encode every detail about a prog8 program (the VM doesn't have to actually be able to run all of it though!)
- BUG: fix call() return value handling (... what's wrong with it again?)
- proper code gen for the CALLI instruction and that it (optionally) returns a word value that needs to be assigned to a reg
- make multiple classes of registers and maybe also categorize by life time , to prepare for better register allocation in the future
    SYSCALL_ARGS,        // Reserved for syscall arguments (r99000-99099, r99100-99199)
    FUNCTION_PARAMS,     // For passing function parameters
    FUNCTION_RETURNS,    // For function return values
    TEMPORARY,           // Short-lived temporary values
    LOCAL_VARIABLES,     // Local variables within functions
    GLOBAL_VARIABLES,    // Global/static variables
    HARDWARE_MAPPED,     // Mapped to CPU hardware registers
    LOOP_INDICES,        // Used as loop counters
    ADDRESS_CALCULATION  // Used for pointer arithmetic
  Categorizing registers by lifetime can significantly improve allocation:
   - Short-lived: Temporary registers used in expressions
   - Medium-lived: Local variables within a function
  Registers could be categorized by how frequently they're accessed:
   - Hot Registers: Frequently accessed (should be allocated to faster physical registers)
   - Warm Registers: Moderately accessed
   - Cold Registers: Rarely accessed (can be spilled to memory if needed)
  We already have type-based pools
    - byte, word, long, float registers

- pointer dt's are all reduced to just an uword (in the irTypeString method) - is this okay or could it be beneficial to reintroduce the actual pointer type information? See commit 88b074c208450c58aa32469745afa03e4c5f564a
- change the instruction format so an indirect register (a pointer) can be used more often, at least for the inplace assignment operators that operate on pointer. Breaks SSA form though?
- register reuse to reduce the number of required variables in memory eventually. But can only re-use a register if a) it's the same type and b) if the second occurrence is not called from the first occurrence (otherwise the value gets overwritten!) Breaks SSA form though?
- reduce register usage via linear-scan algorithm (based on live intervals) https://anoopsarkar.github.io/compilers-class/assets/lectures/opt3-regalloc-linearscan.pdf
  don't forget to take into account the data type of the register when it's going to be reused!
- encode asmsub/extsub clobber info in the call , or maybe include these definitions in the p8ir file itself too.  (return registers are already encoded in the CALL instruction)
- implement fast code paths for TODO("inplace split....
- implement more TODOs in AssignmentGen
- do something with the 'split' tag on split word arrays
- add more optimizations in IRPeepholeOptimizer, implement the TODOs in there at least
- idea: replace all scalar variables that are not @shared by an allocated register. Keep a table of the variable to register mapping (including the datatype)
  global initialization values are simply a list of LOAD instructions.
  Variables replaced include all subroutine parameters? Or not?  So the only variables that remain as variables are arrays and strings.
- the split word arrays are currently also split in _lsb/_msb arrays in the IR, and operations take multiple (byte) instructions that may lead to verbose and slow operation and machine code generation down the line.
  maybe another representation is needed once actual codegeneration is done from the IR...? Should array operations be encoded in a more high level form in the IR?
- ExpressionCodeResult:  get rid of the separation between single result register and multiple result registers? maybe not, this requires hundreds of lines to change.. :(


Libraries
---------
- Add split-word array sorting routines to sorting module?
- pet32 target: make syslib more complete (missing kernal routines)?
- need help with: PET disk routines (OPEN, SETLFS etc are not exposed as kernal calls)
- c128 target: make syslib more complete (missing kernal routines)?


Optimizations
-------------

- peek(address + offset) and poke(address + offset, value) generate quite large asm segments. Optimize into subroutines for "indexed memory peek/pokes"?
- (6502) optimize if sgn(value)<0: still does a compare with 0 even though SGN sets all status bits.
- longvar = lptr^^ ,  lptr2^^=lptr^^  now go via temporary registers, optimize this to avoid using temps.  (seems like it is dereferencing the pointer first and then assigning the intermediate value)
- optimize inplaceLongShiftRight() for byte aligned cases
- more optimized operator handling of different types, for example uword a ^ byte b now does a type cast of b to word first
- optimize longEqualsValue() for long const and variable operands to not assign needlessly to R0-R3.
- optimize optimizedBitwiseExpr()  for long const and variable operands to not assign needlessly to R0-R3.
- optimize inplacemodificationLongWithLiteralval() for more shift values such as 8, 16, 24 etc but take sign bit into account!
- optimize simple cases in funcPeekL and funcPokeL
- bind types in the Ast much sooner than the simplifiedAst creation, so that we maybe could get rid of InferredType ?
- Port more benchmarks from https://thred.github.io/c-bench-64/  to prog8 and see how it stacks up. (see benchmark-c/ directory)
- Since fixing the missing zp-var initialization, programs grew in size again because STZ's reappeared. Can we add more intelligent (and correct!) optimizations to remove those STZs that might be redundant again?
- in Identifier: use typedarray of strings instead of listOf? Other places?
- Compilation speed regression: test/comparisons/test_word_lte.p8 compilation takes twice as long as with prog8 10.5
- Compilation speed: try to join multiple modifications in 1 result in the AST processors instead of returning it straight away every time
- Optimize the IfExpression code generation to be more like regular if-else code.  (both 6502 and IR) search for "TODO don't store condition as expression" ... but maybe postpone until codegen from IR, where it seems solved?
- optimize floats.cast_from_long and floats.cast_as_long by directly accessing FAC bits?
- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers (or these first??), then the rest
  This will probably need the register categorization from the IR explained there, for the old 6502 codegen there is not enough information to act on
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code, those checks should probably all be removed, or be made permanent
