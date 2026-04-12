TODO
====

Chess, Imageviewer, Halloween, Paint have all increased size (maybe more)  Plasma example increased in size too since commit 14abc1f0


Dead Code Elimination BUG with nested subroutines
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
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
- add @private to variables and subroutines declared in a scope to make them invisible from outside that scope?
- when implementing unsigned longs: remove the (multiple?) "TODO "hack" to allow unsigned long constants to be used as values for signed longs, without needing a cast
- struct/ptr: support const pointers (simple and struct types) (make sure to change codegen properly in all cases, change remark about this limitation in docs too)
- struct/ptr: implement the remaining TODOs in PointerAssignmentsGen.
- struct/ptr: optimize augmented assignments to indexed pointer targets like sprptr[2]^^.y++  (these are now not performend in-place but as a regular assignment)
- struct/ptr: implement even more struct instance assignments (via memcopy) in CodeDesugarer (see the TODO) (add to documentation as well, paragraph 'Structs')
- struct/ptr: support @nosplit pointer arrays?
- struct/ptr: support pointer to pointer?
- struct/ptr: support for typed function pointers?  (&routine could be typed by default as well then)
- struct/ptr: optimize the long pointer deref in assignPointerDerefExpression() so that it doesnt require a temp assignment all the time
- struct/ptr: really fixing the pointer dereferencing issues (cursed hybrid beween IdentifierReference, PtrDereferece and PtrIndexedDereference) may require getting rid of scoped identifiers altogether and treat '.' as a "scope or pointer following operator"
- struct/ptr: (later, nasty parser problem:) support chaining pointer dereference on function calls that return a pointer.  (type checking now fails on stuff like func().field and func().next.field)
- allow the value of a memory mapped variable to be address-of another variable, not just a constant number, and maybe even even more complicated constant expressions such as:  &uword  MyHigh = &mylong+2
- Make all constants long by default? or not? (remove type name altogether), reduce to target type implictly if the actual value fits.  Experiment is in branch 'long-consts'
  This will break some existing programs that depend on value wrap arounds, but gives more intuitive constant number handling.
  Can give descriptive error message for old syntax that still includes the type name?
- add documentation for more library modules instead of just linking to the source code
- sizeof(pointer) is now always 2 (an uword), make this a variable in the ICompilationTarget so that it could be 4 at the time we might ad a 32-bits 68000 target for example. Much code assumes word size addresses though.
- when a complete block is removed because unused, suppress all info messages about everything in the block being removed
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
- ``divModLongSigned`` - multiplication and division of signed long (4-byte) numbers is not implemented. Use floats or words as workaround.
- ``divModConstLongSigned`` - constant variant of the above.
- ``divModLongSignedInplace`` - in-place variant of the above.
- ``IRInlineBinaryChunk`` and ``IRInlineAsmChunk`` - inline chunks cannot be loaded by the VM (VmProgramLoader.kt). Limitation of the current VM design: program is not loaded into memory as data
- VM label address loading - ``VmProgramLoader.kt`` throws when it cannot resolve a label address as a value (``"vm cannot yet load a label address as a value"``).
- ``statusOverflow`` determination in comparison operations (see line ~1555 in VirtualMachine.kt).


Libraries
---------
- Add split-word array sorting routines to sorting module?
- make a list of all floats.* routines that the compiler expects for full float support?

Optimizations
-------------

- **BUG: AST-level dead store elimination removes reads from memory-mapped IO variables.**
  When a memory-mapped variable (e.g., ``&ubyte vera_data = $9f24``) is assigned to a regular variable multiple times
  in a row (``result = vera_data; result = vera_data; result = vera_data``), the AST optimizer sees this as redundant
  dead stores and removes all but the last assignment. However, reading from an IO address has side effects
  (e.g., VERA_DATA advances the VRAM address pointer), so these reads must NOT be eliminated.
  The assembly optimizer (``AsmOptimizer.kt``) has been fixed to protect IO accesses, but the AST-level
  ``UnusedCodeRemover`` / dead store elimination runs *before* assembly generation and doesn't know which
  variables map to IO space. A proper fix requires marking memory-mapped IO variables in the AST so the
  optimizer can treat reads from them as having side effects.

- inliner: extend multi-value return inlining to support parameterized subroutines. Currently only works for parameterless subroutines. (Void calls with parameters already work if the parameters are unused in the body.)
- bind types in the Ast much sooner than the simplifiedAst creation, so that we maybe could get rid of InferredType ?
- Port more benchmarks from https://thred.github.io/c-bench-64/  to prog8 and see how it stacks up. (see benchmark-c/ directory)
- Compilation speed regression: test/comparisons/test_word_lte.p8 compilation takes almost twice as long as with prog8 11.4 and 10.5 is even faster. Largest slowdown in "ast optimizing" pass.
- Compilation speed: try to join multiple modifications in 1 result in the AST processors instead of returning it straight away every time
- VariableAllocator: can we think of a smarter strategy for allocating variables into zeropage, rather than first-come-first-served?
  for instance, vars used inside loops first, then loopvars, then uwords used as pointers (or these first??), then the rest
  This will probably need the register categorization from the IR explained there, for the old 6502 codegen there is not enough information to act on
  Note that simple prioritization based on size (bytes first) yields WORSE results for many programs.
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once 6502-codegen is done from IR code, those 6502 only optimizations should probably be removed
