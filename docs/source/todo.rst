TODO
====

Known bugs:
- fix chained aliasing errors see test "chained aliasing"
- fix crash in ir loader/vm for deeply nested symbol reference, see test "deeply scoped variable references"


Weird Heisenbug
^^^^^^^^^^^^^^^
- BUG: examples/cube3d-float crashes with div by zero error on C64 (works on cx16. ALready broken in v11, v10 still worked)
  caused by the RTS after JMP removal in optimizeJsrRtsAndOtherCombinations (replacing it with a NOP makes the problem disappear !??!?).
  Also observed in the boingball example for the C64 when some code was removed from the start and end.


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
- make builtin functions capable of returning multiple values, then make divmod() return the 2 results rather than accepting 2 extra variables as arguments
- then also introduce lmh(longvalue) -or whatever sensible name- builtin function that returns the low, mid, hi (bank) bytes of a long.
- and rewrite the divmod (and others?) function to just return the 2 results instead of taking target variables as arguments.
- add a -profile option (for now X16 only) that instruments the start (and returns?) -of every prog8 subroutine with code that dumps to the X16 emulator debug console: name of sub, stack pointer (for call depth!), emudbg cycle count. Save/restore all used registers!  Start of program must set cycle count to zero.
- add @private to variables and subroutines declared in a scope to make them invisible from outside that scope?
- when implementing unsigned longs: remove the (multiple) "TODO "hack" to allow unsigned long constants to be used as values for signed longs, without needing a cast"
- structs: properly fix the symbol name prefix hack in StStruct.sameas(), see github issue 198
- struct/ptr: support const pointers (simple and struct types) (make sure to change codegen properly in all cases, change remark about this limitation in docs too)
- struct/ptr: implement the remaining TODOs in PointerAssignmentsGen.
- struct/ptr: optimize the float copying in assignIndexedPointer() (also word and long?)
- struct/ptr: optimize augmented assignments to indexed pointer targets like sprptr[2]^^.y++  (these are now not performend in-place but as a regular assignment)
- struct/ptr: implement even more struct instance assignments (via memcopy) in CodeDesugarer (see the TODO) (add to documentation as well, paragraph 'Structs')
- struct/ptr: support @nosplit pointer arrays?
- struct/ptr: support pointer to pointer?
- struct/ptr: support for typed function pointers?  (&routine could be typed by default as well then)
- struct/ptr: really fixing the pointer dereferencing issues (cursed hybrid beween IdentifierReference, PtrDereferece and PtrIndexedDereference) may require getting rid of scoped identifiers altogether and treat '.' as a "scope or pointer following operator"
- struct/ptr: (later, nasty parser problem:) support chaining pointer dereference on function calls that return a pointer.  (type checking now fails on stuff like func().field and func().next.field)
- should we have a SourceStorageKind.POINTER?   (there is one for TargetStorageKind...)
- make memory mapped variables support more constant expressions such as:  &uword  MyHigh = &mylong1+2 (see github issue #192)
- allow memory() to occur in array initializer (maybe needed for 2 dimensional arrays?) i.e. make it a constant (see github issue #192)
- allow the value of a memory mapped variable to be address-of another variable, not just a constant number
- implement for loops with long loopvar over long range expression
- Make all constants long by default? or not? (remove type name altogether), reduce to target type implictly if the actual value fits.  Experiment is in branch 'long-consts'
  This will break some existing programs that depend on value wrap arounds, but gives more intuitive constant number handling.
  Can give descriptive error message for old syntax that still includes the type name?
- improve ANTLR grammar with better error handling (as suggested by Qwen AI)
- add documentation for more library modules instead of just linking to the source code
- add an Index to the documentation
- can we just use index numbers instead of object hashes for struct instance labels (and other places?)  see labelnameForStructInstance
- sizeof(pointer) is now always 2 (an uword), make this a variable in the ICompilationTarget so that it could be 4 at the time we might ad a 32-bits 68000 target for example. Much code assumes word size addresses though.
- Two- or even multidimensional arrays and chained indexing, purely as syntactic sugar over regular arrays?
- when a complete block is removed because unused, suppress all info messages about everything in the block being removed
- is "checkAssignmentCompatible" redundant (gets called just 1 time!) when we also have "checkValueTypeAndRange" ?
- romable: should we have a way to explicitly set the memory address for the BSS area (add a -varsaddress and -slabsaddress options?)
- romable: fix remaining codegens (some for loops, see ForLoopsAsmGen)
- Kotlin: can we use inline value classes in certain spots? (domain types instead of primitives)
- Kotlin: can private setters / backing fields be used? (internal mutableList, external List)
- add float support to the configurable compiler targets. Restrictions: just have "cbm-style floats" as an option (to that it can slot into the current float codegen), where all you have to specify is the addresses of AYINT and GIVAYF and FADDT and all their friends.
- Improve the SublimeText syntax file for prog8, you can also install this for 'bat': https://github.com/sharkdp/bat?tab=readme-ov-file#adding-new-syntaxes--language-definitions
- Change scoping rules for qualified symbols so that they don't always start from the root but behave like other programming languages (look in local scope first), maybe only when qualified symbol starts with '.' such as: .local.value = 33
- something to reduce the need to use fully qualified names all the time. 'with' ?  Or 'using <prefix>'?
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
- getting it in shape for code generation: the IR file should be able to encode every detail about a prog8 program (the VM doesn't have to actually be able to run all of it though!)
- encode asmsub/extsub clobber info in the call, or maybe include these definitions in the p8ir file itself too.  (return registers are already encoded in the CALL instruction)
- SWAP opcode needs more addressing modes to avoid code bloat? for instance to swap 2 array elements by index
- extend the index register datatype in the LOADX, STOREX, STOREZX instructions from byte to word (0-255 to 0-65535) (this not compatible with 8 bit 6502, but the 68000 can use that , well, up to 32767)
- or just get rid of LOADX/STOREX/STOREZX, just use add + loadi / storei (because we have to translate that sequence anyway)?
- if instruction has both integer and float registers, the sequence of the registers is sometimes weird in the .p8ir file (float regs always at the end even when otherwise the target -integer- register is the first one in the list, for example.)
- rollback this exception?:  "LOADI has an exception to allow reg1 and reg2 to be the same"  + actual exception check in the check "reg1 must not be same as reg2"
- maybe change all branch instructions to have 2 exits (label if branch condition ture, and label if false) instead of 1, and get rid of the implicit "next code chunk" link between chunks.
- if float<0 / if word<0  uses sgn or load, but still use a bgt etc instruction after that with a #0 operand even though the sgn and load instructions sets the status bits already, so just use bstneg etc
- make multiple classes of registers and maybe also categorize by life time? , to prepare for better register allocation in the future
    SYSCALL_ARGS,        // Reserved for syscall arguments (r99000-99099, r99100-99199)
    FUNCTION_PARAMS,     // For passing function parameters
    FUNCTION_RETURNS,    // For function return values
    TEMPORARY,           // Short-lived temporary values
    LOCAL_VARIABLES,     // Local variables within functions
    GLOBAL_VARIABLES,    // Global/static variables
    HARDWARE_MAPPED,     // Mapped to CPU hardware registers
    LOOP_INDICES,        // Used as loop counters
    ADDRESS_CALCULATION  // Used for pointer arithmetic
  Registers could be categorized by how frequently they're accessed:
   - Hot Registers: Frequently accessed (should be allocated to faster physical registers)
   - Warm Registers: Moderately accessed
   - Cold Registers: Rarely accessed (move to memory variables)
  We already have type-based pools
    - byte, word, long, float registers

- pointer dt's are all reduced to just an uword (in the irTypeString method) - is this okay or could it be beneficial to reintroduce the actual pointer type information? See commit 88b074c208450c58aa32469745afa03e4c5f564a
- change the instruction format so an indirect register (a pointer) can be used more often, at least for the inplace assignment operators that operate on pointer. Breaks SSA form though?
- register reuse to reduce the number of required variables in memory eventually. But can only re-use a register if a) it's the same type and b) if the second occurrence is not called from the first occurrence (otherwise the value gets overwritten!) Breaks SSA form though?
- reduce register usage via linear-scan algorithm (based on live intervals) https://anoopsarkar.github.io/compilers-class/assets/lectures/opt3-regalloc-linearscan.pdf
  don't forget to take into account the data type of the register when it's going to be reused! Reuse breaks SSA form though
- implement fast code paths for TODO("inplace split....
- implement more TODOs in AssignmentGen
- add more optimizations in IRPeepholeOptimizer, implement the TODOs in there at least
- the split word arrays are currently also split in _lsb/_msb arrays in the IR, and operations take multiple (byte) instructions that may lead to verbose and slow operation and machine code generation down the line.
  maybe another representation is needed once actual codegeneration is done from the IR...? Should array operations be encoded in a more high level form in the IR?
- ExpressionCodeResult:  get rid of the separation between single result register and multiple result registers? maybe not, this requires hundreds of lines to change.. :(


Libraries
---------
- diskio: make f_open (and f_open_w?) and exists to read the command channel for success status rather than depending on flaky ST
- Add split-word array sorting routines to sorting module?
- make a list of all floats.* routines that the compiler expects for full float support?

Optimizations
-------------

- bind types in the Ast much sooner than the simplifiedAst creation, so that we maybe could get rid of InferredType ?
- Port more benchmarks from https://thred.github.io/c-bench-64/  to prog8 and see how it stacks up. (see benchmark-c/ directory)
- Since fixing the missing zp-var initialization, programs grew in size again because STZ's reappeared. Can we add more intelligent (and correct!) optimizations to remove those STZs that might be redundant again?
- Compilation speed regression: test/comparisons/test_word_lte.p8 compilation takes almost twice as long as with prog8 11.4 and 10.5 is even faster. Largest slowdown in "ast optimizing" pass.
- Compilation speed: try to join multiple modifications in 1 result in the AST processors instead of returning it straight away every time
- Optimize the IfExpression code generation to be more like regular if-else code.  (both 6502 and IR) search for "TODO don't store condition as expression" ... but maybe postpone until codegen from IR, where it seems solved?
- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers (or these first??), then the rest
  This will probably need the register categorization from the IR explained there, for the old 6502 codegen there is not enough information to act on
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code, those checks should probably all be removed, or be made permanent
