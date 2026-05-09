TODO
====

landscape.p8 no longer has previous optimizations active? (it has become larger again)


Dead Code Elimination BUG in 64tass with nested subroutines
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- When a subroutine contains a nested ``asmsub`` (or possibly a nested ``sub()``), 64tass cannot properly eliminate
  the outer subroutine if ANY symbol from within it is referenced elsewhere (even if the outer subroutine itself is never called).
- Workaround: move nested subroutines to be top-level (block-level) subroutines instead.
- Example: in gfx_lores.p8, the nested ``plot()`` inside ``line()`` caused unused ``line()`` to be included in programs
  that only used other gfx_lores functions (like ``circle()``). Fixed by moving it to a separate ``internal_line_plot()``.


Weird Heisenbug
^^^^^^^^^^^^^^^
- BUG: examples/cube3d-float crashes with div by zero error on C64 (works on cx16. ALready broken in v11, v10 still worked)
  caused by the RTS after JMP removal in optimizeJsrRtsAndOtherCombinations (replacing it with a NOP makes the problem disappear !??!?).
  Also observed in the boingball example for the C64 when some code was removed from the start and end.


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
- symboldump: some sort of javadocs generated from the p8 source files (instead of just the function signatures). Use markdown for formatting.
- why are (interned) strings stored as initialization value in the SymbolTable AND as string nodes in the interned string block? Something seems redundant here?
- when implementing unsigned longs: remove the (multiple?) "TODO "hack" to allow unsigned long constants to be used as values for signed longs, without needing a cast
- struct/ptr: implement the remaining TODOs in PointerAssignmentsGen.
- struct/ptr: support pointer to pointer?
- struct/ptr: support for typed function pointers, so that we can call them via functptr^^(args) maybe even without the ^^ operator?  (&routine could be typed by default as well then)
- struct/ptr: really fixing the pointer dereferencing issues (cursed hybrid beween IdentifierReference, PtrDereferece and PtrIndexedDereference) may require getting rid of scoped identifiers altogether and treat '.' as a "scope or pointer following operator"
- struct/ptr: (later, nasty parser problem:) support chaining pointer dereference on function calls that return a pointer.  (type checking now fails on stuff like func().field and func().next.field)
- allow the value of a memory mapped variable to be address-of another variable, not just a constant number, and maybe even even more complicated constant expressions such as:  &uword  MyHigh = &mylong+2
- Make all constants long by default? or not? (remove type name altogether), reduce to target type implictly if the actual value fits.  -> long-consts branch
  This will break some existing programs that depend on value wrap arounds, but gives more intuitive constant number handling.
  Can give descriptive error message for old syntax that still includes the type name?
- add documentation for more library modules instead of just linking to the source code
- sizeof(pointer) is now always 2 (an uword), make this a variable in the ICompilationTarget so that it could be 4 at the time we might ad a 32-bits 68000 target for example. Much code assumes word size addresses though.
- romable: should we have a way to explicitly set the memory address for the BSS area (add a -varsaddress and -slabsaddress options?)
- romable: fix remaining codegens (some for loops, see ForLoopsAsmGen)
- add float support to the configurable compiler targets. Restrictions: just have "cbm-style floats" as an option (to that it can slot into the current float codegen), where all you have to specify is the addresses of AYINT and GIVAYF and FADDT and all their friends.
- Change scoping rules for qualified symbols so that they don't always start from the root but behave like other programming languages (look in local scope first), maybe only when qualified symbol starts with '.' such as: .local.value = 33
- something to reduce the need to use fully qualified names all the time. 'with' ?  Or 'using <prefix>'?
- Improve register load order in subroutine call args assignments:
  in certain situations (need examples!), the "wrong" order of evaluation of function call arguments is done which results
  in overwriting registers that already got their value, which requires a lot of stack juggling (especially on plain 6502 cpu!)
  Maybe this routine can be made more intelligent.  See usesOtherRegistersWhileEvaluating() and argumentsViaRegisters().
- Does it make codegen easier if everything is an expression?  Start with the PtProgram ast classes, change statements to expressions that have (new) VOID data type. BUT probably not worth it if a new codegen is going to be based on the IR
- implement the signed remainder byte and word routines on 6502 (virtual target already has them working)
- implement the signed divmod byte and word routines on 6502 (virtual target already has them working)
- make a form of "manual generics" possible like: varsub routine(T arg)->T  where T is expanded to a specific type
  (this is already done hardcoded for several of the builtin functions)
- more support for (64tass) SEGMENTS in the prog8 syntax itself? maybe %segment blah  in blocks?
- ability to use a sub instead of only a var for @bank ? what for though? dynamic bank/overlay loading?
- BUG: fix the c64 multiplexer example


IR/VM
-----
- getting it in shape for code generation: the IR file should be able to encode every detail about a prog8 program (the VM doesn't have to actually be able to run all of it though!)
- maybe change all branch instructions to have 2 exits (label if branch condition ture, and label if false) instead of 1, and get rid of the implicit "next code chunk" link between chunks.
- implement more TODOs in AssignmentGen?
- add more optimizations in IRPeepholeOptimizer?
- **Multi-Level IR Design**: Consider introducing a High-Level IR (HLIR) layer before the current low-level IR to preserve semantics like loop bounds, array indexing, and structure field access.
  The current IR is effectively "assembly with infinite registers," losing high-level info needed for optimal 6502 instruction selection (e.g., choosing ``LDA addr,X`` vs ``LDA (zp),Y``).
  Recommendation: Implement a custom HLIR using Kotlin sealed classes (inspired by MLIR dialects but lighter weight).
  Flow: HLIR (Loops/Arrays) -> Lowering -> Current IR (Ops/Regs) -> Codegen.
  Don't adopt LLVM (too low-level) or QBE (too simple). Custom HLIR fits Kotlin best and preserves semantic intent for better 6502 codegen.
  **Split word arrays** are a prime example: currently represented as two separate ``_lsb``/``_msb`` ubyte arrays in the IR, so a single ``words[i] += 50`` expands to 8 byte-level IR instructions (two LOADM, CONCAT, ADD, LSIGB, MSIGB, two STOREM). At the HLIR level this should remain a single word-array augmented assignment; the lowering pass can split it into ``_lsb``/``_msb`` ops (or emit direct word ops for a backend that supports them).

**Missing VM Implementations (VirtualMachine.kt)**
- ``IRInlineBinaryChunk`` and ``IRInlineAsmChunk`` - inline chunks cannot be loaded by the VM (VmProgramLoader.kt). Limitation of the current VM design: program is not loaded into memory as data
- VM label address loading - ``VmProgramLoader.kt`` throws when it cannot resolve a label address as a value (``"vm cannot yet load a label address as a value"``).

Libraries
---------
- add a serialio library to interface with the serial/wifi card, see https://github.com/safiire/cx16_wget/blob/master/uart.s 
- Add split-word array sorting routines to sorting module?
- make a list of all floats.* routines that the compiler expects for full float support?
- pet32 list_filenames() doesn't check filenames buffer size


Optimizations
-------------

- Always do const-folding regardless of using -noopt or not. This will break A LOT of unit tests though
- inliner: extend multi-value return inlining to support parameterized subroutines. Currently only works for parameterless subroutines. (Void calls with parameters already work if the parameters are unused in the body.)
- Port more benchmarks from https://thred.github.io/c-bench-64/  to prog8 and see how it stacks up. (see benchmark-c/ directory)
- Compilation speed: try to join multiple modifications in 1 result in the AST processors instead of returning it straight away every time
- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers (or these first??), then the rest
  This will probably need the register categorization from the IR explained there, for the old 6502 codegen there is not enough information to act on
  Note that simple prioritization based on size (bytes first) yields WORSE results for many programs.
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code, those 6502 only optimizations should probably be removed
- Const-optimization of ``memory()`` variables: variables initialized with a ``memory()`` call are automatically 
  promoted to constants when they have at most 2 reads (``VarConstantValueTypeAdjuster`` + ``SimplifiedAstMaker``).
  For variables with many reads, keeping them as regular variables is beneficial because the 6502 codegen can 
  use a zeropage pointer for repeated slab access, saving code size.
  When such a variable is explicitly declared as ``const`` and used as the base of an array indexing expression,
  the ``ConstantIdentifierReplacer`` does skip replacement, but the ``CodeDesugarer`` and subsequent optimizer passes
  still handle it correctly (no actual problem).
