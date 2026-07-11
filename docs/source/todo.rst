TODO
====

- need a bunch of type casting/conversion checks that test the handling of the 4-byte/long pointer datatype on the qemu68k target.
- need a 'pointer' type that is an alias for long on m68k and uword otherwise?
- some generated label names in the m68k codegen can maybe replaced by local/anonymous labels?


m68k Codegen: FPU register allocation (68881 only has fp0-fp7)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
The m68k codegen maps IR virtual FPU register numbers (RegisterNum) directly to physical ``fpN`` names — so fr8 becomes ``fp8``, which doesn't exist on a real 68881 (only fp0-fp7).
A proper virtual-to-physical register allocator is needed to remap the virtual registers to the 8 available FPU registers.
This was not noticed earlier because simple float programs only use 1-3 registers.

Note: this problem does NOT affect regular data/address registers because those use a memory-based register file (``p8_regfile+N`` in RAM). Any number of virtual data/address registers fits. The 68881's FPU registers can't be memory-mapped into that register file, so the direct virtual-to-physical mapping is used instead, which overflows past fp7.

Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
- make enums strongly typed instead of just syntactic sugar for ints (see ideas/enum-strong-type.md for the plan)
- add %option private_symbols to make access mode private by default; need (new) 'public' keyword to explicitly mark symbols public.
- symboldump: some sort of javadocs generated from the p8 source files (instead of just the function signatures). Use markdown for formatting, not html.
- when implementing unsigned longs: remove the (multiple?) "TODO "hack" to allow unsigned long constants to be used as values for signed longs, without needing a cast
- struct/ptr: implement the remaining TODOs in PointerAssignmentsGen.
- struct/ptr: support pointer to pointer?
- struct/ptr: typed function pointers (simplified): ``&&subroutine`` returns opaque typed pointer (no ``funcptr`` keyword), assignment allowed where target type is inferred, calling via ``ptr(args)``. No explicit signature syntax. Useful mostly for 68000 targets.
- struct/ptr: really fixing the pointer dereferencing issues (cursed hybrid between IdentifierReference, PtrDereferece and PtrIndexedDereference) may require getting rid of scoped identifiers altogether and treat '.' as a "scope or pointer following operator"
- struct/ptr: (later, nasty parser problem:) support chaining pointer dereference on function calls that return a pointer.  (type checking now fails on stuff like func().field and func().next.field)
- Make all constants long by default? or not? (remove type name altogether), reduce to target type implicitly if the actual value fits.  -> long-consts branch
  This will break some existing programs that depend on value wraparound, but gives more intuitive constant number handling.
  Can give descriptive error message for old syntax that still includes the type name?
- add documentation for more library modules instead of just linking to the source code
- sizeof(pointer) is now always 2 (an uword), make this a variable in the ICompilationTarget so that it could be 4 at the time we might ad a 32-bits 68000 target for example. Much code assumes word size addresses though.
- add float support to the configurable compiler targets. Restrictions: just have "cbm-style floats" as an option (to that it can slot into the current float codegen), where all you have to specify is the addresses of AYINT and GIVAYF and FADDT and all their friends.
- add support for pointer size >2  to configurable compiler targets.
- Change scoping rules for qualified symbols so that they don't always start from the root but behave like other programming languages (look in local scope first), maybe only when qualified symbol starts with '.' such as: .local.value = 33
- implement the signed remainder byte and word routines on 6502 (virtual target already has them working)
- implement the signed divmod byte and word routines on 6502 (virtual target already has them working)
- make a form of "manual generics" possible like: varsub routine(T arg)->T  where T is expanded to a specific type
  (this is already done hardcoded for several of the builtin functions)
- migrate CLI argument parsing from the obsolete kotlinx-cli library to Clikt (com.github.ajalt:clikt)
- add new directives ``%bssaddress`` and ``%slabsaddress`` to set the memory address for the BSS area and memory slabs (analogous to ``%address`` for program load address).
  Note: these should be mutually exclusive with the existing CLI options (``-varsgolden``, ``-varshigh``, ``-slabsgolden``, ``-slabshigh``)
  because the CLI options are target-aware shorthands (set bank symbols, do bounds checking against predefined ranges)
  while the directives are raw addresses — they'd conflict if both specified for the same area.
- the c64 sprite multiplexer example may need timing adjustments after compiler changes (not a compiler bug — cycle-exact C64 code is inherently fragile)


Romable (%option romable)
^^^^^^^^^^^^^^^^^^^^^^^^^
- ForLoopsAsmGen: fix remaining codegens. Three methods use self-modifying code (patching ``cmp #0`` immediates) with no romable-safe alternative:
  - ``forOverBytesRangeStepGreaterOne`` (byte, abs(step)>=2)
  - ``forOverWordsRangeStepGreaterOne`` (word, step>=2)
  - ``forOverWordsRangeStepGreaterOneDescending`` (word, step<=-2)
  Fix pattern (already used by step-1 methods): add ``if(romable)`` branch that allocates a temp var via ``createTempVarReused``, stores the loop end value into it, and compares against it. Existing self-modifying code stays in ``else`` branch for RAM programs.
- BuiltinFunctionsAsmGen: ``callfar`` / ``callfar2`` with non-const bank/addr. Uses self-modifying ``sta +0`` / ``sty +1`` to patch JSRFAR operands. Needs a RAM trampoline approach (copy stub with variable args into RAM, JSR to that).
- FunctionCallAsmGen: ``extsub`` with variable bank. Same JSRFAR operand patching issue. Needs RAM trampoline.
- Add more test coverage for the romable option.


IR/VM
^^^^^
- maybe change all branch instructions to have 2 exits (label if branch condition true, and label if false) instead of 1, and get rid of the implicit "next code chunk" link between chunks.
- implement more TODOs in AssignmentGen?
- add more optimizations in IRPeepholeOptimizer?
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


BSS section bug in 64tass (v1.60)
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
See ``test_64tass_bss_bug.asm`` for a minimal reproduction.
The new 6502 code generator ran into this when trying to nest ``.section BSS`` inside nested ``.proc`` blocks.
64tass gives "not defined 'BSS'" (interprets bare section name as expression). Quoting also fails.
Workaround in new6502gen: strip ``.section``/``.send`` from library asmsub assembly.
Affected variables (``_exitcarry``, ``_exitcode``, ``_exitcodeX``, ``_exitcodeY``) end up as a binary gap
instead of cleared BSS — 4 bytes unzeroed.  Fine for ``sys.exit(n)`` (values written before read).


Dead Code Elimination bug in 64tass, for nested subroutines
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- When a subroutine contains a nested ``asmsub`` (or possibly a nested ``sub()``), 64tass cannot properly eliminate
  the outer subroutine if ANY symbol from within it is referenced elsewhere (even if the outer subroutine itself is never called).
- Workaround: move nested subroutines to be top-level (block-level) subroutines instead.
- Example: in gfx_lores.p8, the nested ``plot()`` inside ``line()`` caused unused ``line()`` to be included in programs
  that only used other gfx_lores functions (like ``circle()``). Fixed by moving it to a separate ``internal_line_plot()``.
