TODO
====

Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
- DataType.ARRAY_POINTER depends on the compilation target to be either a split word array or not. This is horrible because now we have to check with the compilation target everywhere to see if a DataType enumeration value is split word array, and PtVariable and PtArrayIndexer need an explicit boolean to tell us if this is the case. See ideas/remove_array_pointer_plan.md for the plan.
- split up AssignmentAsmGen.kt in codeGenCpu6502 it is by far the largest file 6000+ lines
- extend the ``-gendoc`` command to generate user reference documentation from Markdown docstrings; see ``ideas/markdown-docstrings-and-reference-docs.md`` for the plan.
- add documentation for more library modules instead of just linking to the source code
- struct/ptr: support explicit ^^ before an index on assignment targets, e.g. ``l1^^.s[0] = 4242`` (still a parse error; see the remaining TestPointers xtest). Implicit ^^ forms (``listarray[2].value = 123``, ``a.b.c[i].value = X``, ``l1.s[0] = 4242``) and LHS ``func().field = a`` / ``(expr as ^^T).field = a`` already compile via the dotExpression poke-style desugar, covered by enabled tests. Remaining work: the explicit-^^ grammar fix, plus a VM execution test confirming the implicit-^^ write stores/loads correctly. See ideas/new-pointer-deref-plans.md (Option B notes now partially outdated).
- add float support to the configurable compiler targets. Restrictions: just have "cbm-style floats" as an option (to that it can slot into the current float codegen), where "all" you have to specify is the addresses of AYINT and GIVAYF and FADDT and all their friends.
- Change scoping rules for qualified symbols so that they don't always start from the root but behave like other programming languages (look in local scope first), maybe only when qualified symbol starts with '.' such as: .local.value = 33, or the other way around? i.e. require new syntax to explicitly look up from global scope. That would give a backwards compatible solution. See ideas/scoping-qualified-symbols.md for a brevity-focused exploration (opt-in `.a.b` local-first, `a.b` stays global; `::a.b` reserved for future flip).
- implement the signed remainder byte and word routines on 6502 old codegen (virtual, m68k and IR-based codegens already have them working)
- implement the signed divmod byte and word routines on 6502 old codegen (virtual, m68k and IR-based codegens already have them working)
- the c64 sprite multiplexer still needs adjustments to make it smooth, it lacks a proper raster event scheduler.
- support typed pointer arrays as struct fields, curretly requires untyped pointers arrays.

Won't do's or deferred
^^^^^^^^^^^^^^^^^^^^^^
- support arrays-of-struct-instances as struct fields (e.g. ``struct Outer { Inner[4] inners }``). Currently rejected with an error.
- support immediate struct instance variables (e.g. ``Enemy e``) instead of only ``^^Enemy`` pointers and ``Enemy[]`` arrays. Attempted Aug 2026 and rolled back — requires value-type IR (currently ``STRUCT_INSTANCE`` is pointer-only, see ``DataTypes.kt:22`` and ``IRCodeGen.kt:2332``; field access assumes ``^^Struct`` indirection). Far-off; use ``^^Enemy`` or ``Enemy[N]``.
- Won't do soon: make a form of "manual generics" possible, see ``ideas/polymorphism.md`` (this is already done hardcoded for several of the builtin functions)
- Won't do soon: when implementing unsigned longs: remove the (multiple?) "TODO "hack" to allow unsigned long constants to be used as values for signed longs, without needing a cast
- Won't do: ``var x = <value>`` type inference for ordinary variables will not be added: it hides the explicit size/cost that is central to Prog8's simple retro vibe, and has ambiguous literal/tag/scope interactions. Inference stays limited to ``const`` (compile-time, no allocation) and ``for`` loop counters (type fixed by the iterable).
- Won't do: make enums strongly typed instead of just syntactic sugar for ints (see ideas/enum-strong-type.md for the plan)
- Deferred: implement a true m68k register allocator that keeps virtual registers in hardware registers (D0-D7/A0-A6/FP0-FP7) instead of the flat ``p8_regfile`` memory block. See ideas/m68k-register-allocation.md for the design.
- Preparatory work for the deferred m68k register allocator: centralized register effects, call-effect metadata, allocation hints, and an IR dataflow verifier. See ideas/m68k-register-allocation-preparation.md.
- Deferred: implement a stack-based memory model for m68k locals and parameters to make subroutines reentrant and recursive. See ideas/m68k-stack-memory-model.md for the design.
- Deferred: make the fixed zero-page scratch locations (``P8ZP_SCRATCH_B1``/``_REG``/``_W1``/``_W2``/``_PTR``) dynamic rather than hardcoded per target. Right now they live at fixed per-target addresses and are always reserved; a ``%zpreserved``/``%zpallowed`` range that excludes them only produces a warning (the codegen would still overwrite that memory). Relocating scratch to an available low-address byte (or making it configurable) when not in conflict would let ``%zpreserved``/``%zpallowed`` fully protect the desired areas. See ``MemoryRegions.kt`` (``Zeropage.scratchBytes``/``checkScratchConflicts``) and the per-target ``*Zeropage`` classes.
- Deferred: really fixing the pointer dereferencing issues (cursed hybrid between IdentifierReference, PtrDereferece and PtrIndexedDereference), which may require getting rid of scoped identifiers altogether and treating '.' as a "scope or pointer following operator".
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
- Add split-word array sorting routines to sorting module by adding copies of the existing non-split routines that are split-aware (split words are stored as separate lsb/msb arrays, so the split-aware routines must index into both halves).
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
