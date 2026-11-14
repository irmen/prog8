# M68k Codegen Quality Improvements (open items)

Trimmed to the items still open. Completed tiers and their measurement notes
have been removed; see git history for the full record. Ordered roughly by
complexity/impact.

## Tier 4 - Register allocation & spilling (weeks)

### 4.1 Widen StackFrameLayout promotion
- **Where**: `StackFrameLayout` (M9) in `M68kCodeGenerator.kt`.
- Promote more vregs (not only long-lived) to A5 frame slots instead of the
  global `p8_regfile`.
- **Benefit**: removes `p8_regfile` traffic; frame-relative loads are cheaper
  and reentrant-safe.
- **Effort**: medium.
- Constraint already enforced: the 16384 frame size limit and `%option noframe`
  checks exist in `M68kCodeGenerator.kt`; this item is only the promotion policy.
- Re-measurement note: the allocator already sends virtually all spills to A5
  frame slots, so expected benefit is low. Consider combining with 4.3.

### 4.2 Prefer frames over regfile as the spill target
- Make the regfile a last resort (globals / non-reentrant state) rather than
  the default fallback for unallocated vregs.
- **Effort**: medium, depends on 4.1.
- Does NOT remove the regfile; actual removal is 4.5.

### 4.3 Improve coloring heuristic
- Current greedy by liveness. Add cost model: prefer keeping pointer-typed
  vregs in address regs (A2-A4), data in D2-D6; spill cheapest (fewest uses,
  longest live range) first.
- **Effort**: medium.
- Most promising Tier 4 item: frame-slot accesses are the dominant spill
  target (4-474/program); better coloring could reduce them.

### 4.4 Cross-chunk / limited interprocedural hints
- Inline small callees' register usage to avoid full caller-saved kill sets at
  call boundaries (when callee is known and small).
- **Effort**: high. Defer.

### 4.5 Eliminate `p8_regfile` / `p8_fregfile` entirely
- End state after 4.1/4.2: remove the section, not just the traffic. Stop
  emitting the `p8_regfile` / `p8_fregfile` BSS labels (`AsmGen.kt`
  `emitBssVars` tail) when nothing references them.
- **Where**: `AsmGen.kt` (emission + `resolveRegFileAddress` fallback),
  `M68kCodeGenerator.kt` (spill-slot path), `VregRenumber.kt` (bails when the
  regfile layout is observed).
- Remaining references are not from register spilling but from:
  - struct instance initialization staging (amiga1200)
  - system startup library code (amiga1200)
  - `sys.die.code` staging (qemu68k)
  - `%option noframe` subroutines, which bypass the allocator entirely and
    currently depend on `p8_regfile` for all temporaries. Strategy: noframe
    subs should revert to static in-memory variable allocation (like the 6502
    codegen), accessing dedicated BSS variables via absolute addressing.
- **Benefit**: smaller BSS, no global mutable spill state (reentrant-safe),
  cleaner listings; the assembler catches any missed reference as an
  undefined symbol.
- **Effort**: medium, depends on 4.1/4.2.

### 4.6 Interprocedural unpreserved-register propagation [low priority]
- **Problem**: callers of multi-return subroutines save the callee's return
  registers (d2-d4 for `bool,word,word,word,word`) unconditionally, even when
  nothing is live in them. The saves exist because a caller's caller may hold
  values in those registers across the call, and caller liveness is invisible
  from the callee's frame. A local fix (skipping the saves at multi-return
  call boundaries) is unsafe: it corrupts values one frame up.
- **Fix**: propagate an "unpreserved set" over the call graph as a fixpoint:
  `unpreserved(S) = returnRegisters(S) ∪ ⋃ unpreserved(ordinary callees)`.
  Call sites to S clobber `unpreserved(S)` (live values forbidden there,
  spilling instead of saving), and multi-return boundaries stop contributing
  to the caller's save set. Depends only on signatures and the call graph,
  not on allocation results, so one fixpoint pass before allocation suffices.
- **Where**: new pass next to the call-graph analysis in
  `StackFrameLayout.kt`, which already computes call sites per caller,
  indirect callers, dispatch targets and SCCs (recursion handling);
  `M68kRegisterAllocator` consumes the result.
- Gates: `noframe` callees, address-taken/indirect-called subroutines
  (conservative: keep the saves), same gates as the re-entrancy check.
- **Benefit**: removes up to three `movem` registers per invocation in every
  intermediate caller of a multi-return subroutine; measured cost of the
  current conservative behavior is small (~8 bytes, ~48 cycles per
  invocation, zero for callers already using d2-d6), hence low priority.
- **Effort**: medium.

## Tier 5 - Bigger structural work (months)

### 5.1 Better D0 reload cache
- Extend cache validity analysis across more opcodes and branch targets;
  currently invalidated conservatively.
- **Effort**: medium-high.

### 5.2 Dead store / dead variable elimination at IR level for m68k
- Tighten `UnusedCodeRemover` / `VregRenumber` for m68k-specific spill patterns.
- **Effort**: medium.

### 5.3 Pointer-indexed array access: keep index + scale in the IR memory reference
- **Problem**: pointer-indexed element access (`values[pos]` on `^^uword`)
  pre-folds the element scale into a long index register; the backend
  materializes ~9 instructions per access and cannot use hardware scaling.
- **Fix**: teach the IR about a register-held pointer base with a scaled index
  (extend `MemoryReference.Indexed` with a `pointer`-vreg base, or add an
  indexed variant of `MemoryReference.Indirect`); emit `LOADI/STOREI
  [rPtr + rIdx.w * eltSize]` and let the scale disappear.
- Scope/gate: shared `intermediate` change - `IRTextCodec`, register-use
  accounting, `mapRegisters`/renumber, and the VM interpreter must all handle
  the new shape. 6502 codegen is unaffected.
- **Benefit**: fewer instructions per pointer-indexed access (9 -> 3 on 68020
  with a `.l` index, ~1.7x on 68000); helps all m68k programs using
  pointer-indexed loops.
- **Effort**: large.

### 5.4 Memory-based multi-value return convention [low priority]
- **Problem**: multi-return subroutines return their values in d0, d1, d2, ...
  (fp0.. for floats). Values beyond d1 land in callee-saved pool registers, so
  the callee clobbers them, forcing callers to save/restore them and the
  allocator to force-spill every transfer temporary (see 4.6 for the
  propagation alternative).
- **Fix**: return multi-values through a caller-provided result area instead
  (hidden pointer argument, e.g. in a0, pointing at caller frame slots). The
  callee writes results to memory directly: no d2+ clobber, no caller saves,
  and today's d-register -> memory force-spill round trip disappears.
- Scope/gate: changes the ABI of every multi-return signature. Touches
  `returnsWhatWhere` (`AstStatements.kt`), `AssignmentGen` (LOADHR captures
  become memory reads), `IRCodeGen` (STOREHR emission for `return a, b, ...`
  and multi-value forwarding), `ExpressionGen` (call-site result slots), the
  6502 convention is unaffected but the shared code paths are. Re-entrancy is
  preserved because the result area lives in the caller's frame.
- **Effort**: large; supersedes 4.6 if ever done (4.6 becomes unnecessary).

## Assembly codegen improvements

### A.6 Frame-slot traffic and the base-pointer clobber [STILL TODO - high complexity]
- The six sorting routines contain 112 frame-slot loads and 15 stores; the hot
  ones are reloaded per statement and per loop test.
- Concrete cause: the element address is computed *into* a2, clobbering the
  base pointer a2 already holds, so the base is reloaded (`move.l 10(a5),d6`)
  before every access. Keeping the base in one An and the target in another
  removes the reload.
- Also: the double `loadr.w` (`move.w d3,d2` / `move.w d3,d4` for the same
  vreg value) needs copy coalescing in the allocator.
- **Status: STILL TODO.** Not a peephole pass. Requires changes to the m68k
  code generator's register allocation and address computation. Days-to-weeks,
  risks regressions across all m68k programs. Cannot be closed by AsmOptimizer
  passes alone.

## Decisions (won't do)

- 4.7 Avoid fixed A5 frame slots for simple locals - rejected. The peephole
  version miscompiled and saved only ~3 move pairs; the full vreg-based
  solution is the only viable path and is not pursued.
- Frame-pointer omission Phase 2 (movem save-size tracking) - rejected; Phase 1
  covers the bulk of leaf helpers.
