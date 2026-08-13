TODO
====

- need a bunch of type casting/conversion checks that test the handling of the 4-byte/long pointer datatype on the qemu68k target.
- amiga library structs: use more typed pointers if it knows the struct type from the same (or another amiga library module) , rather than using `pointer`. Consider both the extsubs but also the struct fields in the amigaDOS structs in the generated library modules.


Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
- m68k codegen: make use of scaling factors in the indexed instructions on 68020+ ? see ideas/scaled-indexing-IR.md
- support arrays-of-structs, see ideas/arrays-of-structs.md
- split up AssignmentAsmGen.kt in codeGenCpu6502 it is by far the largest file 6000+ lines
- make enums strongly typed instead of just syntactic sugar for ints (see ideas/enum-strong-type.md for the plan)
- symboldump: some sort of javadocs generated from the p8 source files (instead of just the function signatures). Use markdown for formatting, not html.
- if implementing unsigned longs: remove the (multiple?) "TODO "hack" to allow unsigned long constants to be used as values for signed longs, without needing a cast
- struct/ptr: implement the remaining TODOs in PointerAssignmentsGen (see ``ideas/ptr-assignment-todo.md`` for the severity breakdown).
- struct/ptr: really fixing the pointer dereferencing issues (cursed hybrid between IdentifierReference, PtrDereferece and PtrIndexedDereference) may require getting rid of scoped identifiers altogether and treat '.' as a "scope or pointer following operator"
- struct/ptr: (later, nasty parser problem:) support chaining pointer dereference on function calls that return a pointer.  (type checking now fails on stuff like func().field and func().next.field)
- add documentation for more library modules instead of just linking to the source code
- add float support to the configurable compiler targets. Restrictions: just have "cbm-style floats" as an option (to that it can slot into the current float codegen), where "all" you have to specify is the addresses of AYINT and GIVAYF and FADDT and all their friends.
- Change scoping rules for qualified symbols so that they don't always start from the root but behave like other programming languages (look in local scope first), maybe only when qualified symbol starts with '.' such as: .local.value = 33, or the other way around? i.e. require new syntax to explicitly look up from global scope. That would give a backwards compatible solution.
- implement the signed remainder byte and word routines on 6502 (virtual target already has them working)
- implement the signed divmod byte and word routines on 6502 (virtual target already has them working)
- make a form of "manual generics" possible, see ``ideas/polymorphism.md``
  (this is already done hardcoded for several of the builtin functions)
- add new directives ``%bssaddress`` and ``%slabsaddress`` to set the memory address for the BSS area and memory slabs (analogous to ``%address`` for program load address).
  Note: these should be mutually exclusive with the existing CLI options (``-varsgolden``, ``-varshigh``, ``-slabsgolden``, ``-slabshigh``)
  because the CLI options are target-aware shorthands (set bank symbols, do bounds checking against predefined ranges)
  while the directives are raw addresses — they'd conflict if both specified for the same area.
- the c64 sprite multiplexer example may need timing adjustments after compiler changes (not a compiler bug — cycle-exact C64 code is inherently fragile)


Romable (%option romable)
^^^^^^^^^^^^^^^^^^^^^^^^^
- ForLoopsAsmGen: remaining constant-step methods use self-modifying code (patching ``cmp #0`` immediates). This is an accepted RAM-mode size optimization; in ROMable mode they should continue to report the established ``romableError`` unless a smaller ROM-safe implementation is later desired:
  - ``forOverBytesRangeStepGreaterOne`` (byte, abs(step)>=2)
  - ``forOverWordsRangeStepGreaterOne`` (word, step>=2)
  - ``forOverWordsRangeStepGreaterOneDescending`` (word, step<=-2)
  The new variable-step byte/word paths are already ROM-safe because they use temporary variables rather than self-modifying code. Add more ROMable tests for both behaviors as needed.
- BuiltinFunctionsAsmGen: ``callfar`` / ``callfar2`` with non-const bank/addr. Uses self-modifying ``sta +0`` / ``sty +1`` to patch JSRFAR operands. Needs a RAM trampoline approach (copy stub with variable args into RAM, JSR to that).
- FunctionCallAsmGen: ``extsub`` with variable bank. Same JSRFAR operand patching issue. Needs RAM trampoline.
- Add more test coverage for the romable option.


IR/VM
^^^^^
- encode indexed scaling into IR (so that m68k codegen can use scale factor addressing) see ideas/scaled-indexing-IR.md
- maybe change all branch instructions to have 2 exits (label if branch condition true, and label if false) instead of 1, and get rid of the implicit "next code chunk" link between chunks.
- implement more TODOs in AssignmentGen?
- add even more optimizations in IRPeepholeOptimizer?
- **Multi-Level IR Design**: Consider introducing a High-Level IR (HLIR) layer before the current low-level IR to preserve semantics like loop bounds, array indexing, and structure field access.
  The current IR is effectively "assembly with infinite registers."
  Recommendation when adding non-6502 targets: Implement a custom HLIR using Kotlin sealed classes (inspired by MLIR dialects but lighter weight).
  Flow: SimpleAst -> HLIR (Loops/Arrays) -> Lowering -> Current IR (Ops/Regs) -> Codegen.
  Don't adopt LLVM (too low-level) or QBE (too simple). Custom HLIR fits Kotlin best and preserves semantic intent.
  **Important**: HLIR's value for 6502 is minimal if the backend consumes only the lowered IR. For 6502 to benefit from HLIR, the backend would need to target HLIR directly (bypassing the lowering pass for applicable constructs), adding complexity. HLIR is primarily useful for non-6502 backends (68000) and the VM interpreter.
  **Split word arrays** are a prime example: currently represented as two separate ``_lsb``/``_msb`` ubyte arrays in the IR, so a single ``words[i] += 50`` expands to 8 byte-level IR instructions (two LOADM, CONCAT, ADD, LSIGB, MSIGB, two STOREM). At the HLIR level this should remain a single word-array augmented assignment; the lowering pass can split it into ``_lsb``/``_msb`` ops (for 6502) or keep it as a word op (for 68000).

**Missing VM Implementations (VirtualMachine.kt)**
- ``IRInlineBinaryChunk`` and ``IRInlineAsmChunk`` - inline chunks cannot be loaded by the VM (VmProgramLoader.kt). Limitation of the current VM design: program is not loaded into memory as data
- VM label address loading - ``VmProgramLoader.kt`` throws when it cannot resolve a label address as a value (``"vm cannot yet load a label address as a value"``).
- ``prefixScopedName`` (``codeGenIntermediate/src/prog8/codegen/intermediate/SymbolPrefixer.kt:206``) hardcodes ``p8s_`` for all middle path parts of a dotted scoped name. This is wrong for structs in the path: ``main.MyStruct.field`` produces ``p8s_MyStruct`` (subroutine prefix) instead of ``p8t_MyStruct`` (struct prefix). Fix: look up each middle part in the symbol table and apply ``typePrefixChar()`` per part. Pre-existing bug carried over from the 6502 new6502codegen (``AsmGen.kt``).


Libraries
^^^^^^^^^
- Add split-word array sorting routines to sorting module?
- make a list of all floats.* routines that the compiler expects for full float support?


Optimizations
^^^^^^^^^^^^^
- Port more benchmarks from https://thred.github.io/c-bench-64/  to prog8 and see how it stacks up. (see benchmark-c/ directory)
- Compilation speed: try to join multiple modifications in 1 result in the AST processors instead of returning it straight away every time
- various optimizers skip stuff if compTarget.name==VMTarget.NAME.  Once new 6502 codegen is done from IR code, those 6502 only optimizations should probably be removed


Dead Code Elimination bug in 64tass, for nested subroutines
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- When a subroutine contains a nested ``asmsub`` (or possibly a nested ``sub()``), 64tass cannot properly eliminate
  the outer subroutine if ANY symbol from within it is referenced elsewhere (even if the outer subroutine itself is never called).
- Workaround: move nested subroutines to be top-level (block-level) subroutines instead.
- Example: in gfx_lores.p8, the nested ``plot()`` inside ``line()`` caused unused ``line()`` to be included in programs
  that only used other gfx_lores functions (like ``circle()``). Fixed by moving it to a separate ``internal_line_plot()``.
