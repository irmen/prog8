# Register Packing Design Plan

## Current Approach (Simplified, Recommended)

Instead of a single global slot pool shared across all subroutines
(which is unsound — callees clobber callers' values), **each call depth
gets its own fixed, non-overlapping range of slots.**  Subroutines at
different depths never execute simultaneously, so their slots can occupy
the same physical memory without conflict — no save/restore needed, no
caller/callee clobbering.

```
Depth 0 (main, interrupt handlers):   slots   0..255
Depth 1 (direct callees):             slots 256..511
Depth 2:                              slots 512..767
...
```

This sidesteps the interprocedural analysis problem entirely, reuses
the existing graph-colouring allocator per subroutine with a single
parameter change (start slot per depth), and keeps per-subroutine
liveness analysis simple.

## Goal

Reduce the number of distinct virtual registers in the IR program by
coalescing registers whose live ranges do not overlap into the same slot.
This shrinks the register file (``p8_regfile`` BSS section) and makes the
generated assembly cleaner, which is especially important for memory-constrained
6502 targets.

The IR generator assigns fresh register numbers freely (one per value,
per operation).  A non-trivial program can easily use hundreds of distinct
registers after code generation.  Most of these have short, non-overlapping
live ranges and can share a single memory slot.

## Approach

The implementation lives in ``RegisterPacker.kt`` and is structured as a
standard graph-colouring register allocator:

```
      IR program
          |
          v
  1. Collect all register numbers and their types across the whole program.
  2. For each subroutine separately:
     a. Build a control-flow graph (CFG) of its code chunks.
     b. Compute liveness (gen/kill + iterative dataflow) to determine
        liveIn/liveOut per chunk.
     c. Walk backward through each chunk's instructions to build live
        intervals (register, start-index, end-index, type).
     d. Merge intervals of the same register into contiguous ranges.
     e. Build a conflict graph from instruction-level register aliasing
        and cross-type incompatibilities.
     f. Greedy-colour intervals using the conflict graph and a global
        slot-type registry (shared across subroutines).
     g. Rewrite instructions: replace each register with its assigned
        slot number.
          |
          v
   Packed IR program
```

## Current Status

The implementation exists and compiles but is **disabled** (the call in
``IRCodeGen.generate()`` is commented out).  Two bugs were found during
testing:

### 1. Disjoint-interval value clobbering (cx16 Fibonacci)

A register with multiple disjoint live ranges (e.g. a loop counter written
at the loop entry and read at the loop exit, with no uses in between) was
split into separate intervals.  The packer allowed another register to
share the same slot during the gap, overwriting the value before its next
read.

**Fix applied (but now disabled with the rest of the packer):** always
merge all intervals of the same register into a single contiguous range,
regardless of whether they overlap.

### 2. Complex control flow (m68k TextElite)

The liveness analysis produces incorrect live ranges for subroutines with
complex control flow (nested conditionals, loops, early returns).  Two
registers with genuinely overlapping live ranges are assigned the same
slot, causing one to clobber the other's value at runtime.  The symptom
was an infinite loop printing spaces (the galaxy map data was being read
from the wrong memory location).

**Root cause:** the iterative dataflow liveness analysis (gen/kill with
fixed-point iteration) does not correctly handle all CFG patterns present
in larger programs.  The analysis appears to converge to a fixed point
that is not the true meet-over-all-paths solution for some loop and
conditional structures.

## Alternative: Intra-expression Register Reuse in RegisterPool

Instead of a separate packing pass, attack the problem at the source:
make ``IRCodeGen.kt``'s ``RegisterPool`` reuse registers within
expressions where lifetimes are trivially bounded.

**How it works today:** Every call to ``registers.next(dt)`` returns a
fresh, monotonically increasing register number.  No register is ever
freed.  The IR ends up with hundreds of short-lived temporaries that the
peephole optimizer later removes via ``removeDeadStores`` and friends.
This is wasteful because by the time the peephole optimizer runs,
temporary registers have already consumed pool space, affecting the
regfile layout and BSS size.

**Proposed change:** Add ``pushFrame()`` / ``popFrame()`` to
``RegisterPool``.  Before translating an expression, push a mark.  When
the expression completes, free all registers allocated since the mark
— except those *pinned* (the result register that gets stored to a
variable or used by the next statement).

::

    pushFrame()
      r1 = next(BYTE)    ← temporary (freed on popFrame)
      r2 = next(WORD)    ← temporary
      r3 = next(WORD)    ← temporary
      result = r1 + r2   ← produces r4 = next(WORD)
    popFrame(pin = r4)   ← frees r1, r2, r3; keeps r4

**Where to apply it:** Every expression in ``ExpressionCodeGen`` ends
with a known result register.  After the expression's IR instructions
are emitted, all intermediate registers can be released.  The result
register is *pinned* if it will be read by a subsequent statement
(e.g., assigned to a variable, used as a function argument).

**Benefits over the packer:**

  - No liveness analysis at all — lifetimes are bounded by the
    expression tree, not by dataflow over arbitrary CFGs.
  - No cross-subroutine problems (each subroutine's pool is
    independent).
  - Produces cleaner IR from the start, reducing the peephole
    optimizer's work.
  - Simple to implement (stack of marks + a free list in
    ``RegisterPool``).
  - Same memory savings: most registers are short-lived temporaries.

**Limitations:**

  - Only captures within-expression reuse; registers live across
    statements (loop counters, function arguments, values stored to
    variables) still accumulate monotonically.
  - Requires careful handling of control flow (if/while bodies need
    separate frames).
  - Some expression codegen paths (fcallArgs, string/array init) may
    need extra pinning.

**Verdict:** Worth trying before re-attempting the packer.  It targets
the same wasteful-allocation root cause, is much simpler to implement
correctly, and eliminates the need for a separate packing pass for the
majority of temporary registers.  The depth-range packer would still be
useful for cross-statement reuse that intra-expression freeing misses,
but the urgency is lower once the RegisterPool stops leaking
temporaries.

## Consequences of the Current Implementation

### Register file slots are 4 bytes each

To work around the type-inconsistency problem (where a register can carry
different types -- BYTE, WORD, LONG, POINTER -- in different subroutines
after packing, and ``registersUsed()`` with ``putIfAbsent`` would only
record the first-seen type), the register file layout in both the new6502
and m68k codegens was changed to allocate **4 bytes per slot** (the
maximum data type size, LONG).  This eliminates under-allocation at the
cost of wasting memory.

The waste is small when the packer is active (single-digit slot counts),
but with the packer disabled the regfile reverts to variable-size slots
based on each register's first-seen type, which is correct for
non-packed programs because registers there have consistent types.

If the packer is re-enabled, the 4-bytes-per-slot approach must be
reconsidered:

  - **Problem:** allocating 4 bytes unconditionally inflates the BSS
    section, which on a C64 (with ~38K RAM) could be a real constraint
    for programs that already have hundreds of registers.

  - **Alternative:** restore per-register max-size tracking.  Instead of
    using ``registersUsed().regsTypes`` (which gives one type per
    register), scan all instructions with ``addUsedRegistersCounts`` to
    find the **maximum** data type size actually used for each register.
    This is more complex than the current simple lookup but avoids
    wasting memory.

  - **Simpler alternative after re-enabling:** accept 4 bytes per slot
    as long as the packer keeps register counts low (single digits).
    The BSS waste is negligible on targets with ample RAM (CX16, m68k),
    and on C64 the packer would likely reduce registers to <10, making
    40 bytes of BSS acceptable.

## What Needs to Be Fixed Before Re-enabling

### 1. Liveness analysis must be correct for all CFG shapes

The current iterative gen/kill dataflow is the weak point.  It needs to be
validated against a test suite of small programs with nested loops,
conditionals, early returns, and switch-like dispatch patterns.

Options:

  a) **Fix the existing dataflow.**  The equations are standard:
     ```
     liveOut[B]  = union of liveIn[succ(B)]
     liveIn[B]   = gen[B] ∪ (liveOut[B] - kill[B])
     ```
     The iteration uses a worklist or repeated passes until fixpoint.
     The bug may be in how ``gen`` and ``kill`` are computed for each
     chunk (they currently process instructions forward, adding to
     ``gen`` only if the register is not already in ``kill``).  Verify
     this matches the standard definition (a register is in ``gen`` if
     it is used before being defined in the block).

  b) **Replace with a more robust formulation.**  Use a proper
     iterative algorithm that tracks both ``liveUse`` and ``liveDef``
     (or ``liveGen`` and ``liveKill``) and iterates until no changes.
     The current implementation already does this but may have an error
     in the gen/kill calculation.

### 2. Interval merging must not lose type information

The "always merge" fix for disjoint intervals merges all intervals of
the same register, but the merged interval keeps only the **first**
interval's type.  If later intervals have incompatible types, the
register is skipped entirely (via ``skipRegs``).  But if they have
compatible types (POINTER ↔ WORD/LONG via ``typesCompatible``), the
merged type is the first one, which may be the *narrower* type.
A WORD slot allocated from a POINTER-typed interval is fine (both are
2 bytes on 6502), but a BYTE slot from a POINTER interval would be
wrong.  Currently this is handled by the regfile layout allocating
4 bytes unconditionally (when packing is active), which makes the
merged type irrelevant for sizing.

When reverting to variable-size slots, the interval must carry the
**widest** type of all merged sub-intervals, not just the first one.

### 3. SkipRegs must not silently discard registers

When a register is skipped because its intervals have incompatible types,
the register keeps its original number.  The rest of the packer must not
assign this number as a packed slot to another register.  Currently this
is guaranteed by ``startSlot = maxReg + 1`` (packed slots always exceed
the highest original register number).  This invariant must be preserved.

### 4. Cross-subroutine slot collision (fundamental design issue)

The packer processes each subroutine independently, but assigns slots
from a single global pool (``p8_regfile`` is one flat memory block shared
by all subroutines).  If subroutine A gets slot 41, calls B, and B's
packing also assigned it slot 41, B's writes clobber A's live values.
There are no caller/callee save semantics.

**The 902-to-15 reduction observed with TextElite is therefore not
plausible for a correct allocator.**  The observed aggressive coalescing
is likely a side effect of incorrect liveness (intervals not covering
call sites properly) rather than genuine reuse.  A correct per-subroutine
packing would need its slots to be disjoint from those of all callers
and callees, which drastically limits reuse.

Options to fix this:

  a) **Call-graph-aware allocation.**  Process subroutines in reverse
     call order (leaves first).  Each callee reports its used slot set
     back to its callers, so the caller's packer can avoid those slot
     numbers (or reserve them as incompatible types).  This requires
     building the call graph and propagating slot usage up the call
     tree, which is non-trivial for indirect calls (``CALLI``) and
     function pointers.

  b) **Save/restore around calls.**  After packing, insert save/restore
     instructions at call boundaries for any register that is live
     across the call and whose slot might be used by the callee.  This
     adds code size but keeps each subroutine's packing independent.
     The callee's used slot set must be known, requiring some form of
     interprocedural analysis (even if just per-callee analysis).  On
     6502 targets this is expensive (many instructions to push/pop to
     the stack or a secondary save area).

  c) **Reserve a fixed set of "caller-save" slots.**  Instead of a
     global flat regfile, split into a "local" section (per subroutine,
     overlapping freely) and a "global" section (fixed, used for values
     that must survive calls).  This is a compromise: the local section
     can be packed aggressively within each subroutine, while the global
     section stays unpacked.  It avoids interprocedural analysis at the
     cost of less packing for call-spanning values.

None of these are quick fixes.  The current per-subroutine independent
packing is fundamentally unsound for a flat register file, and this
must be addressed before re-enabling.

### 5. Recommended approach: subroutine-local disjoint slot ranges

Instead of fixing the cross-subroutine sharing (which requires
interprocedural analysis or save/restore code), sidestep it entirely:
**each call depth gets its own fixed, non-overlapping range of slots.**
Subroutines at different depths never execute simultaneously, so their
slots can occupy the same physical memory without conflict — no
save/restore needed, no caller/callee clobbering.

```
Depth 0 (main, interrupt handlers):   slots   0..63
Depth 1 (direct callees):             slots  64..127
Depth 2:                              slots 128..191
...
```

**How it works:**

  1. Build the call graph from the IR (scan ``CALL`` / ``SYSCALL``
     instructions; indirect ``CALLI`` falls back to a conservative
     depth estimate).
  2. Compute max call depth via DFS from each entry point.  Detect
     recursion — mark recursive subroutines as "unpacked" (keep
     original register numbers).
  3. Assign slot ranges per depth.  The range width per depth is the
     max register count needed by any subroutine at that depth, or a
     fixed conservative size (e.g. 64).
  4. Pack each subroutine independently using the current algorithm,
     but constrained to its depth's range: start at ``depth_base``
     instead of ``maxReg + 1``.
  5. No cross-subroutine type conflicts because slots at different
     depths are disjoint addresses.

**Benefits:**
  - Sound by construction — no caller/callee clobbering.
  - Reuses the existing packer logic with a single parameter change
    (start slot per subroutine).
  - No save/restore code generation anywhere.
  - Liveness analysis only needs to be correct within a single
    subroutine (much simpler than interprocedural).

**Tradeoffs:**
  - If a subroutine needs more slots than its depth range allows,
    packing is less aggressive (excess registers stay unpacked).
  - Indirect calls (``CALLI``) need a conservative depth estimate or
    those subroutines skip packing.
  - Recursion detection is required; truly recursive calls cannot
    use static depth ranges.

Per-subroutine packing won't yield headline reductions like 902→15,
but a realistic 60-70% reduction within each subroutine is still
significant — hundreds of bytes of BSS saved on a C64.

### 6. Is this still worthwhile if we later add a CPU register allocator?

It depends on the timeline:

**Short term (months):** Yes.  A CPU register allocator is a major
undertaking (new analysis, code generator changes, calling convention
work).  If it's not imminent, the packer delivers real memory savings
with modest engineering effort using the depth-range approach above.

**The packer and a future CPU allocator share core infrastructure:**
both need correct intraprocedural liveness analysis.  Building the
packer's liveness properly now is not wasted — it becomes the
foundation for the CPU allocator.  The interval analysis, conflict
graph, and greedy coloring can all be reused or adapted.

**Medium term (a year+):** If a CPU register allocator is on the
roadmap, the packer is a bridge solution.  The CPU allocator will
replace the memory-based register file for hot values; the packer's
slot compaction becomes a fallback for spill values only.  At that
point the packer's complexity (depth ranges, skipRegs, type merging)
may not be worth maintaining alongside the allocator.

**Verdict:** Implement the depth-range packer if BSS pressure is
hurting now.  Design the liveness infrastructure to be shared with a
future CPU allocator.  Revisit whether to keep the packer once the
allocator is operational.

### 7. Test coverage

The following scenarios must have unit tests (in ``TestRegisterPacker.kt``):

  - Simple non-overlapping registers coalesced into one slot.
  - Overlapping registers forced into different slots.
  - Cross-chunk liveness (register live across multiple chunks).
  - Disjoint intervals of the same register (the Fibonacci fix).
  - Registers with incompatible types skipped (sqrt_long case).
  - Registers with compatible types (POINTER ↔ WORD/LONG) sharing a slot.
  - FCallArgs registers tracked correctly in the conflict graph.
  - Nested loops and conditionals (the textelite case) -- these tests
    need to be designed and added once the liveness bug is understood.
