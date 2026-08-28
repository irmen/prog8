TODO
====

Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
- Consider extending ``var xx = <value>`` type inference (recently added for for-loop counter variables) to all variable declarations. Investigate pros/cons: desired syntax (``var x = expr`` vs ``var x := expr``), interaction with existing ``ubyte``/``uword``/``word`` inference defaults, const vs var, scope and shadowing, error messages for ambiguous types, documentation, impact on block/sub scope, and whether ``var`` without initializer should be allowed.
- DataType.ARRAY_POINTER depends on the compilation target to be either a split word array or not. This is horrible because now we have to check with the compilation target everywhere to see if a DataType enumeration value is split word array, and PtVariable and PtArrayIndexer need an explicit boolean to tell us if this is the case. See ideas/remove_array_pointer_plan.md for the plan.
- m68k codegen: make use of scaling factors in the indexed instructions on 68020+ ? see ideas/scaled-indexing-IR.md
- support arrays-of-structs, see ideas/arrays-of-structs.md
- split up AssignmentAsmGen.kt in codeGenCpu6502 it is by far the largest file 6000+ lines
- make enums strongly typed instead of just syntactic sugar for ints (see ideas/enum-strong-type.md for the plan)
- symboldump: some sort of javadocs generated from the p8 source files (instead of just the function signatures). Use markdown for formatting, not html.
- if implementing unsigned longs: remove the (multiple?) "TODO "hack" to allow unsigned long constants to be used as values for signed longs, without needing a cast
- struct/ptr: really fixing the pointer dereferencing issues (cursed hybrid between IdentifierReference, PtrDereferece and PtrIndexedDereference) may require getting rid of scoped identifiers altogether and treat '.' as a "scope or pointer following operator"
- struct/ptr: support chaining pointer dereference without explicit ^^ on assignment targets, such as ``l1.s[0] = 4242`` and ``listarray[2].value = 123`` (implicit ``^^`` forms; see TestPointers xtests and ideas/NEW-POINTERDEREF-PLANS.md). Note: the LHS functioncall and parenthesized-expression cases (``func().field = a``, ``(expr as ^^T).field = a``) are now supported: the assign_target grammar rule accepts them and the CodeDesugarer rewrites them into poke-style writes.
- add documentation for more library modules instead of just linking to the source code
- add float support to the configurable compiler targets. Restrictions: just have "cbm-style floats" as an option (to that it can slot into the current float codegen), where "all" you have to specify is the addresses of AYINT and GIVAYF and FADDT and all their friends.
- add equivalent properties to the customizable target configuration file for the ``%bssaddress`` and ``%slabsaddress`` directives (for example ``bss_address`` and ``slabs_address``), so that a custom target can define default raw addresses for the BSS and slabs segments. This would work like ``load_address``, which provides the target-level default that the ``%address`` directive can override per program. Currently the configuration file only supports the golden/high RAM *range* pairs used by the ``-varsgolden``/``-varshigh``/``-slabsgolden``/``-slabshigh`` options.
- combine ``%bssaddress`` and ``%slabsaddress`` into a single ``%varsaddress`` directive. With two separate directives the given addresses are not strictly followed anyway: when both specify the same address, the variables are placed at that address and the slabs simply follow right after them, so the ``%slabsaddress`` value is effectively ignored in that case. One directive that places both segments sequentially starting from a single address is simpler, avoids this confusion, and is consistent with what ``-varsgolden``/``-varshigh`` already do. The custom target configuration file properties (previous item) should then follow suit with a single matching property instead of two.
- Change scoping rules for qualified symbols so that they don't always start from the root but behave like other programming languages (look in local scope first), maybe only when qualified symbol starts with '.' such as: .local.value = 33, or the other way around? i.e. require new syntax to explicitly look up from global scope. That would give a backwards compatible solution.
- implement the signed remainder byte and word routines on 6502 (virtual target already has them working)
- implement the signed divmod byte and word routines on 6502 (virtual target already has them working)
- make a form of "manual generics" possible, see ``ideas/polymorphism.md`` (this is already done hardcoded for several of the builtin functions)
- the c64 sprite multiplexer still needs adjustments to make it smooth, it lacks a proper raster event scheduler.
- TODO: ``equalsSize`` in ``DataTypes.kt`` treats POINTER as WORD-sized (lines46-47). On 32-bit targets (m68k, virtual), POINTER is actually LONG (4 bytes).
  Status: not fixed. The helpers in ``codeCore`` are target-agnostic (no access to ``POINTER_MEM_SIZE``), so they cannot know the pointer width. All three call sites
  (``AstChecker.kt`` bitwise-op check, ``StatementReorderer.kt`` array-element check, 6502-only redundant-cast path) never hit the raw-POINTER case, so it is dead code and has not caused problems.
- STR is now assignable to LONG on all targets including 16-bit ones, for consistency with 32-bit targets where str arrays are LONG[]. On 16-bit targets this means a 2-byte string pointer can be assigned to a 4-byte long variable without a typecast.
  Status: not fixed (known behavior). ``isAssignableTo`` in ``DataTypes.kt`` is target-agnostic and permits STR->LONG everywhere; the 16-bit side effect is harmless and correctness is enforced by the target-aware ``POINTER_MEM_SIZE`` branches in ``AstChecker``.


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
- add even more optimizations in IRPeepholeOptimizer?
- **Multi-Level IR Design**: Consider introducing a High-Level IR (HLIR) layer before the current low-level IR to preserve semantics like loop bounds, array indexing, and structure field access.
  The current IR is effectively "assembly with infinite registers."
  Recommendation when adding non-6502 targets: Implement a custom HLIR using Kotlin sealed classes (inspired by MLIR dialects but lighter weight).
  Flow: SimpleAst -> HLIR (Loops/Arrays) -> Lowering -> Current IR (Ops/Regs) -> Codegen.
  Don't adopt LLVM (too low-level) or QBE (too simple). Custom HLIR fits Kotlin best and preserves semantic intent.
  **Important**: HLIR's value for 6502 is minimal if the backend consumes only the lowered IR. For 6502 to benefit from HLIR, the backend would need to target HLIR directly (bypassing the lowering pass for applicable constructs), adding complexity. HLIR is primarily useful for non-6502 backends (68000) and the VM interpreter.
  Counted loops (``repeat`` / unused-``for`` / ``for x in A to 0 step -1``) are already handled for m68k via the ``dbra d7`` peephole in ``codeGenM68k/AsmOptimizer.kt:optimizeDbraRepeatLoops`` (hidden ``p8_regfile`` counter -> ``move.w #N-1,d7`` / ``dbra d7,label``, bounced if body uses ``d7`` or contains ``bsr``/``jsr``). No HLIR needed for those cases.

**Missing VM Implementations (VirtualMachine.kt)**
- ``IRInlineBinaryChunk`` and ``IRInlineAsmChunk`` - inline chunks cannot be loaded by the VM (VmProgramLoader.kt). Limitation of the current VM design: program is not loaded into memory as data
- VM label address loading - ``VmProgramLoader.kt`` throws when it cannot resolve a label address as a value (``"vm cannot yet load a label address as a value"``).
- ``prefixScopedName`` (``codeGenIntermediate/src/prog8/codegen/intermediate/SymbolPrefixer.kt:206``) hardcodes ``p8s_`` for all middle path parts of a dotted scoped name. This is wrong for structs in the path: ``main.MyStruct.field`` produces ``p8s_MyStruct`` (subroutine prefix) instead of ``p8t_MyStruct`` (struct prefix). Fix: look up each middle part in the symbol table and apply ``typePrefixChar()`` per part. Pre-existing bug carried over from the 6502 new6502codegen (``AsmGen.kt``).

**Source line tracking in new codegen backends**
- Improve source line tracking across the IR into the generated assembly code in the new codegen backends (new6502, m68k). Currently, the IR preserves some source position information, but this is not consistently propagated through to the final assembly output. Better tracking would improve debugging experience (e.g., in monitor/debugger tools) and make it easier to correlate generated assembly back to the original Prog8 source code. Consider adding source location metadata to IR instructions and ensuring code generators emit appropriate ``.line`` directives or comments in the assembly output.

**Multiple status flag returns in new codegens**
- The new6502 and m68k codegens do not support multiple status flag returns in a single multi-assign (e.g. ``-> bool @Pz, bool @Pc``). The first flag's extraction clobbers the CPU status register before the second flag can be read. This is a codegen limitation, not a fundamental IR issue. The old 6502 codegen handles this correctly by using ``php``/``plp`` to save/restore the processor status around each flag extraction (see ``AssignmentAsmGen.kt:60-84``). The new codegens could be improved similarly by detecting multiple status flag returns and emitting appropriate save/restore instructions around the IR's branch patterns.


Libraries
^^^^^^^^^
- Add split-word array sorting routines to sorting module?
- make a list of all floats.* routines that the compiler expects for full float support?


Optimizations
^^^^^^^^^^^^^
- new6502 codegen: use virtual-register liveness or write tracking to remove the retained register-file store when an immediate value is forwarded directly into all arguments of a call. (m68k codegen already implements this optimization.)
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
