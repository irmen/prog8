TODO
====

Future Things and Ideas
^^^^^^^^^^^^^^^^^^^^^^^
- extend the ``-gendoc`` command to generate user reference documentation from Markdown docstrings; see ``ideas/markdown-docstrings-and-reference-docs.md`` for the plan.
- add float support to the configurable compiler targets. Restrictions: just have "cbm-style floats" as an option (to that it can slot into the current float codegen), where "all" you have to specify is the addresses of AYINT and GIVAYF and FADDT and all their friends.
- Change scoping rules for qualified symbols so that they don't always start from the root but behave like other programming languages (look in local scope first), maybe only when qualified symbol starts with '.' such as: .local.value = 33, or the other way around? i.e. require new syntax to explicitly look up from global scope. That would give a backwards compatible solution. See ideas/scoping-qualified-symbols.md for a brevity-focused exploration (opt-in `.a.b` local-first, `a.b` stays global; `::a.b` reserved for future flip).
- implement the signed remainder byte and word routines on 6502 old codegen (the experimental new 6502 backend also does not have them yet)
- implement the signed divmod byte and word routines on 6502 old codegen (the experimental new 6502 backend also does not have them yet)
- complete signed divmod support in the experimental new 6502 codegen:
  - ``sdivModRegisters`` is a bare ``TODO`` and throws if reached. Normal 6502 source is currently rejected earlier by ``BuiltinFuncGen.funcDivmod``.
  - ``sdivModImmediate`` delegates to the unsigned ``divModImmediate`` path. If reached, a runtime signed dividend with an immediate divisor gets incorrect results; fully constant calls such as ``divmod__byte(-7, 3)`` are folded separately.
- sign-correct the saved ``_remainder`` in ``prog8_math.divmod_b_asm``; it currently fixes only the quotient sign, and the saved remainder is not currently read by codegen.
- add automated validation for m68k ``asmsub`` clobber declarations, including user and third-party assembly bodies (declared asmsubs use precise per-call forbidding; bodies without usable metadata conservatively destroy the whole allocatable pool).
- the c64 sprite multiplexer still needs adjustments to make it smooth, it lacks a proper raster event scheduler.

Won't do's or deferred
^^^^^^^^^^^^^^^^^^^^^^
- Deferred: support arrays-of-struct-instances as struct fields (e.g. ``struct Outer { Inner[4] inners }``). Currently rejected with an error.
- Deferred: support ``&array[i].field`` syntax to take the address of a struct field in an array. Currently only ``&array[i] + offsetof(Struct.field)`` works; ``&array[i].field`` is parsed as a field access on the resulting pointer and rejected by the checker.
- Deferred: support typed pointer arrays as struct fields, curretly requires untyped pointers arrays.
- Deferred: support immediate struct instance variables (e.g. ``Enemy e``) instead of only ``^^Enemy`` pointers and ``Enemy[]`` arrays. Attempted Aug 2026 and rolled back — requires value-type IR (currently ``STRUCT_INSTANCE`` is pointer-only, see ``DataTypes.kt:22`` and ``IRCodeGen.kt:2332``; field access assumes ``^^Struct`` indirection). Far-off; use ``^^Enemy`` or ``Enemy[N]``.
- Deferred: make a form of "manual generics" possible, see ``ideas/polymorphism.md``
- Deferred: when implementing unsigned longs: remove the unsigned-long constant "hack" that lets unsigned long constants be used as values for signed longs without needing a cast (the ``Antlr2KotlinVisitor.kt:508``/``:548`` "TODO hack" comments, ``AstExpressions.kt:145`` "TODO should be ULONG, but we don't have that type yet", and ``AsmAssignment.kt:129`` ``TODO("unsigned long $pos")``)
- Deferred: make the fixed zero-page scratch locations (``P8ZP_SCRATCH_B1``/``_REG``/``_W1``/``_W2``/``_PTR``) dynamic rather than hardcoded per target. Right now they live at fixed per-target addresses and are always reserved; a ``%zpreserved``/``%zpallowed`` range that excludes them only produces a warning (the codegen would still overwrite that memory). Relocating scratch to an available low-address byte (or making it configurable) when not in conflict would let ``%zpreserved``/``%zpallowed`` fully protect the desired areas. See ``MemoryRegions.kt`` (``Zeropage.checkScratchConflicts`` and the scratch-byte list it maintains) and the per-target ``*Zeropage`` classes.
- Deferred: really fixing the pointer dereferencing issues (cursed hybrid between IdentifierReference, PtrDereferece and PtrIndexedDereference), which may require getting rid of scoped identifiers altogether and treating '.' as a "scope or pointer following operator".
- Won't do: ``var x = <value>`` type inference for ordinary variables will not be added: it hides the explicit size/cost that is central to Prog8's simple retro vibe, and has ambiguous literal/tag/scope interactions. Inference stays limited to ``const`` (compile-time, no allocation) and ``for`` loop counters (type fixed by the iterable).
- Won't do: make enums strongly typed instead of just syntactic sugar for ints (see ideas/enum-strong-type.md for the plan)
- Won't do: ``elif`` as syntactic sugar for nested ``else``/``if`` (Python-style). It saves one indent level but generates identical flat branch code, and the idiomatic replacements already exist: ``when`` for comparing one value against constant choices (the compiler even hints at this after 3 links in ``AstChecker.checkIfElseChainAsWhen``) and ``when true`` for heterogeneous conditions. Adding it would duplicate ``when``'s job for little gain.
- Won't do: VM stack frames. The virtual target keeps the static-memory model and the VM rejects frame-based IR with an explicit error instead of executing it (see ``technical.rst``). Rationale: the VM exists to test ordinary virtual-target programs, not as a second executor for m68k IR; giving it activation records plus per-activation vreg spilling would duplicate the m68k frame semantics for little validation gain, while emitting frames for ``-target virtual`` would churn hundreds of compile-test expectations. M68K programs are validated with the M68K backend and an emulator instead.
- Won't do: rejecting ``STR`` assignment to ``LONG`` on 16-bit targets, for consistency with 32-bit targets where str arrays are ``LONG[]``. On 16-bit targets this means a 2-byte string pointer can be assigned to a 4-byte long variable without a typecast; that side effect is harmless (and documented in ``variables.rst``). ``isAssignableTo`` in ``DataTypes.kt`` is target-agnostic and permits STR->LONG everywhere; correctness is enforced by the target-aware ``POINTER_MEM_SIZE`` branches in ``AstChecker``.
- Won't do: caching the per-line parse results in the m68k ``AsmOptimizer``, or restricting its passes to the ranges a modification touched. Measured: the whole phase is 0.24-0.28s of a ~3.5s compile and the cost is spread thin, so reworking ~100 call sites in 18 passes would buy ~3-4% at most.

Romable (%option romable)
^^^^^^^^^^^^^^^^^^^^^^^^^
- ForLoopsAsmGen: remaining constant-step methods use self-modifying code (patching ``cmp #0`` immediates). This is an accepted RAM-mode size optimization; in ROMable mode they should continue to report the established ``romableError`` unless a smaller ROM-safe implementation is later desired:
  - ``forOverBytesRangeStepGreaterOne`` (byte, abs(step)>=2)
  - ``forOverWordsRangeStepGreaterOne`` (word, step>=2)
  - ``forOverWordsRangeStepGreaterOneDescending`` (word, step<=-2)
- BuiltinFunctionsAsmGen: ``callfar`` / ``callfar2`` with non-const bank/addr. Uses self-modifying ``sta +0`` / ``sty +1`` to patch JSRFAR operands. Needs a RAM trampoline approach (copy stub with variable args into RAM, JSR to that).
- FunctionCallAsmGen: subroutine with a variable bank (``@bank``). Same JSRFAR operand patching issue; making this work in romable mode requires the RAM trampoline approach.


IR/VM
^^^^^
Missing VM Implementations (VirtualMachine.kt)
- An ``asmsub`` on the virtual target is only rejected when the VM loads the program (``VmProgramLoader.kt``), not at compile time. The rejection cannot simply move into ``AstChecker`` because an ``asmsub`` with an IR body has to stay compilable - the IR-codegen tests use the virtual target to inspect the generated IR of asmsub calls (register and status-flag returns). ``IRInlineBinaryChunk`` (``%binary`` include) stays deferred: the program is not loaded as data, so an embedded blob has no place.
Source line tracking in new codegen backends
- Improve source line tracking across the IR into the generated assembly code in the new codegen backends (new6502, m68k). Currently, the IR preserves some source position information, but this is not consistently propagated through to the final assembly output. Better tracking would improve debugging experience (e.g., in monitor/debugger tools) and make it easier to correlate generated assembly back to the original Prog8 source code. Consider adding source location metadata to IR instructions and ensuring code generators emit appropriate ``.line`` directives or comments in the assembly output.

Libraries
^^^^^^^^^
- Add split-word array sorting routines to sorting module by adding copies of the existing non-split routines that are split-aware (split words are stored as separate lsb/msb arrays, so the split-aware routines must index into both halves).
- add an IFF ANIM5 playback helper module for the Amiga targets (a separate concern from the bare-metal hardware helpers): in-memory IFF chunk walker (BMHD/CMAP/CAMG/BODY plus per-frame ANHD/DLTA), ByteRun1 decoder, op5 byte-vertical delta decoder (unvscomp-style), and a small double-buffer/interleave state machine with ANHD ``reltime`` pacing via vblank waits. CMAP loading is already covered by ``custom.load_iff_cmap()`` (bare metal) and ``graphics.load_iff_cmap()`` (Intuition screens). Usable both OS-friendly (drawing into a ``playfield``) and bare-metal (chip-RAM planes plus ``copper.set_planes`` for the flip). A working C reference implementation is in ``ideas/anim5player.c``: SDL2 + libiff, but structured the way an on-system player must work (two screens, page flip per frame, op5 deltas decoded on demand, no frame cache), so the decode loop and the double-buffer/interleave recipe can be ported from it directly.
- implement the remaining Amiga bare-metal hardware helpers; see ``ideas/amiga-baremetal-helpers.md`` for the designs and status. Remaining: the sprite helper module (packing control words, setting sprite pointers, AGA ``FMODE`` fetch width), and IRQ/VERTB/CIA/TOD handling.

Optimizations
^^^^^^^^^^^^^
- new6502 codegen: use virtual-register liveness or write tracking to remove the retained register-file store when an immediate value is forwarded directly into all arguments of a call.
- new6502 codegen: implement equivalent optimization ideas as the m68k codegen gained since the register allocator got merged
- Port more benchmarks from https://thred.github.io/c-bench-64/  to prog8 and see how it stacks up. (see benchmark-c/ directory)
- Compilation speed: try to join multiple modifications in 1 result in the AST processors instead of returning it straight away every time
- the remaining 6502-specific AST optimizations can't be removed: the old 6502 codegen is still the default backend (``-newcodegen`` is opt-in), so it still depends on them. Revisit once the IR-based 6502 codegen becomes the default.
- m68k/Amiga dead-code elimination: vasm does not strip unreachable routines from the final HUNK output, and ``IRUnusedCodeRemover`` currently keeps assembly subroutines from library modules conservatively (see its TODO). Improve IR reachability analysis so imported library routines are retained only when reachable from program entry points, inline assembly references, address-taken symbols, or other live routines. Keep the existing conservative fallback for opaque assembly references, and add size-regression tests for examples such as ``lowlevel-timer``.
- m68k/Amiga codegen: eliminate redundant pointer reloads in the IR. For example, ``wavc``'s ``read_le_word`` loads its ``addr`` argument twice because the optimizer rewrites the second ``loadm.p`` as ``loadm.l`` and existing CSE does not merge different types. See ``ideas/m68k-eliminate-double-pointer-reload.md`` for a plan.
- IR optimization: investigate restricted common-subexpression elimination within a single basic block for identical pure arithmetic expressions whose inputs are unchanged. Initially exclude memory reads, calls, and control-flow boundaries. The qemu68k maze BFS repeats ``idx +/- H`` to update ``cells``, ``pathdirs``, and ``solveQueue``; sharing that computed neighbor index could be a focused benchmark.
- optimize the small integer division routines in ``compiler/res/prog8lib/math.asm`` (``divmod_b_asm``, ``divmod_ub_asm``, ``divmod_w_asm``, ``divmod_uw_asm``) using the constant-divisor reciprocal-multiply / fast-division tricks from https://forums.nesdev.org/viewtopic.php?t=11336 . Division by a known small constant (or a small variable divisor) can often be replaced by a multiply and a shift, which is much faster than the current subtractive loops. For the 68000 targets this is likely only relevant for the software 32-bit helpers in ``amiga500/m68k_prog8_longmath.asm``, since native ``DIVU``/``DIVS`` already cover small divisions; evaluate and adapt as warranted.


Dead Code Elimination bug in 64tass, for nested subroutines
^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^^
- When a subroutine contains a nested ``asmsub`` (or possibly a nested ``sub()``), 64tass cannot properly eliminate
  the outer subroutine if ANY symbol from within it is referenced elsewhere (even if the outer subroutine itself is never called).
- Workaround: move nested subroutines to be top-level (block-level) subroutines instead (e.g. in gfx_lores.p8, the nested ``plot()`` inside ``line()`` caused unused ``line()`` to be included in programs that only used other gfx_lores functions like ``circle()``).
