# M68k Codegen Quality Improvements (open items)

Trimmed to the items still open. Completed tiers and their measurement notes
have been removed; see git history for the full record. Ordered roughly by
complexity/impact.

## Tier 4 - Register allocation & spilling (weeks)

### 4.1 Widen StackFrameLayout promotion
- **Where**: the promotion policy lives in `StackFrameLayout` (shared `codeGenIntermediate` module); `M68kCodeGenerator.kt` computes `StackFrameLayout.PromotionPressure` from the m68k register-pressure stats and applies the layout.
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

### 5.5 Pre-decrement load/store: `LOADP_DEC` / `STOREP_DEC`
- **Problem**: backward traversal and stack-like access currently need a
  separate decrement plus a load or store. M68K can do this in one
  instruction with pre-decrement addressing, but the IR only has the
  post-increment pair `LOADP_INC`/`STOREP_INC`. 
- **Fix**: add `LOADP_DEC` and `STOREP_DEC`, mirroring `LOADP_INC` and
  `STOREP_INC`. Semantics: decrement the pointer by `sizeof(type)` first,
  then load or store at the new address. M68K lowers this to `move.s (-(An)),Dn`
  or `move.s Dn,-(An)` (with staging if the pointer is not already in an
  address register). new6502 lowers it to decrement plus indirect access.
- Scope/gate: shared `intermediate` change - new opcodes, `IRTextCodec`,
  reader/writer, validation, VM interpreter, and both codegens.
- 68000 and 68020+ both support pre-decrement addressing.
- **Benefit**: one IR instruction and one M68K instruction instead of a
  separate decrement plus access; simpler codegen for backward walks.
- **Effort**: medium.

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

## IR peephole: eliminate double pointer reload in `read_le_word`-like patterns

Status: not yet implemented. The double pointer reload itself still remains;
the two related improvements in Scope below are done and merged.

### Problem

The wavc `read_le_word` subroutine (and similar byte-pair readers in `shared_m68k_wavfile.p8`) loads its `pointer addr` argument from the stack twice:

```asm
p8b_wavfile.p8s_read_le_word:
    link  a5,#0
    move.l  8(a5),a2
    move.b  (1,a2),d0
    and.w  #$ff,d0
    move.w  d0,d2
    lsl.w   #8,d2
    move.l  8(a5),a2        ; <-- same stack slot loaded again
    move.b  (a2),d0
    and.w  #$ff,d0
    move.w  d0,d3
    or.w    d3,d2
    move.w  d2,d0
    unlk  a5
    rts
```

### Root cause

The optimized IR contains two loads of the same parameter:

```ir
loadm.p  r14.p,[main.read_le_word.addr]
loadiz.b r15.w,[r14.p+1]
lsli.w   r15.w,#8.w
loadm.l  r17.l,[main.read_le_word.addr]    ; same 4 bytes, typed as long
loadiz.b r18.w,[r17.p]
orr.w    r15.w,r18.w
returnr.w r15.w
```

The unoptimized IR already had both loads typed as `loadm.p`; the optimizer rewrote the second one to `loadm.l`. Because the IR types differ, existing CSE / redundant-load elimination does not merge them.

`IRInstruction` validation also requires operand types to match the instruction type, so a naïve `loadr.l r17, r14` is rejected when `r14` is typed pointer.

### Scope

This plan is **only** about the double reload. Two related improvements are tracked separately and implemented first:

1. ~~Remove the dead `and.w #$ff` before a left shift by 8.~~ **Done.** The IR pattern is `loadiz.b X; lsli.w #N` (and the sign/zero-extending variants). For a left shift by `N >= resultSize - sourceSize`, the extension is dead because the shift discards all extended/sign-replicated bits. Implemented in the M68K backend (`InstrLoadStore.kt`): an extending load immediately followed by such a shift now emits just `move.<srcSize> M, dN` and lets the following shift operate on the narrow value directly. The temporary assembly peephole was removed.
2. ~~Use the shorter `moveq #0, dN; move.b M, dN` idiom instead of `move.b M, dN; and.w #$ff, dN` for zero extension.~~ **Partial.** The masking was removed and loads now use `moveq #0, d0; move.<size> M, d0`. When the destination register is not `d0`, the backend still emits an extra `move.<size> d0, dN`. A further improvement is to target an allocated data register directly when the address resolution does not clobber it.

### Proposed solution

Add an IR-level redundant-load elimination pass to `IRPeepholeOptimizer.kt`.

#### Rules

1. Scan each code chunk for pairs of `LOADM`/`LOADI` instructions that read the same `MemoryReference`.
2. Allow the pair when their types are memory-compatible:
   - same type, or
   - `POINTER` ↔ `LONG`/`UWORD`/`WORD` when the target pointer size is 4 bytes, or
   - `WORD` ↔ `UWORD`.
3. Require no intervening instruction with a memory write or other side effect.
4. Replace the second load with a `LOADR` move. Reconcile the source register's type by emitting the move under the second load's type (the bits are identical).

#### Alternative: backend-only fix

A narrower fix is to let the M68K assembly generator keep a "last loaded frame slot → address register" cache. This avoids IR type issues but only helps M68K and does not reduce vreg pressure for the register allocator.

The IR-level fix is preferred because it benefits every backend and removes the redundant virtual register.

### Implementation notes

- `removeDoubleLoadsAndStores()` in `IRPeepholeOptimizer.kt` is the natural place to extend; it already removes redundant `STOREM` after `LOADM`.
- The pass must be careful with I/O addresses and aliasing: only eliminate loads from frame slots or named variables, not from raw absolute addresses that may be hardware registers.
- `IRDataType` compatibility helper probably belongs in `RegisterPacker.kt` where a similar check already exists.

### Testing

1. Recompile `examples/amiga/wavc.p8` and verify `p8b_wavfile.p8s_read_le_word` loads `8(a5)` only once.
2. Recompile `readle_test.p8` and check the generated IR no longer contains two `loadm` instructions for `addr`.
3. Run the existing compiler tests and the m68k instruction-selection tests.

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

### A.7 Constant indexed store as an immediate
- Problem: `STOREX` stages the value in `d1` and then stores it:
  `move.s d1,(a0,dN.w*scale)`. When the stored value is a compile-time
  constant, the value register is unnecessary.
- Example from `examples/test.p8`:
  ```asm
  move.b  #99,d2
  move.b  d2,(a0,d3.w)
  ```
  could become:
  ```asm
  move.b  #99,(a0,d3.w)
  ```
- Fix: in `InstrLoadStore.kt`, recognize `STOREX` whose value register is
  defined by an immediately preceding `LOAD` immediate and emit the immediate
  directly to the resolved target. Keep the `d1` staging for non-constant
  values. The indexed target must still be resolved first, including any
  address/index setup.
- Scope/gate: backend-only optimization; no IR change. Must preserve I/O
  address handling and must not reorder across side effects.
- Benefit: one fewer instruction and one fewer register per constant indexed
  store.
- Effort: small-medium.

### A.8 Zero-extension directly into the destination register
- Problem: `LOADMZ` and `LOADMS` currently load into `d0` and then move to
  the destination:
  ```asm
  moveq   #0,d0
  move.b  M,d0
  move.w  d0,dN
  ```
  The `and.w #$ff` masking was already removed, but the `d0` staging remains.
- Fix: when the destination is an allocated data register and address
  resolution does not clobber that register, emit:
  ```asm
  moveq   #0,dN
  move.b  M,dN
  ```
  Keep the `d0` path for address-register or memory destinations, or when
  the address resolution needs `d0`.
- Scope/gate: backend-only. Must invalidate the `d0` cache correctly and
  preserve the existing dead-extension optimization.
- Benefit: one fewer move per zero-extending load.
- Effort: small.

### A.9 Caller-saved temporaries for values dead across calls
- Problem: the allocator only colors into `d2-d6` and `a2-a4`; `d0` and
  `d1` are scratch and are not allocatable. A value that is dead across a
  call can still end up in a callee-saved register, forcing `movem` saves.
  In `examples/test.p8`, `d2` and `d3` are both saved even though neither is
  live across `print_ub`.
- Fix options:
  - allow `d0`/`d1` for vregs whose live range does not cross a call or
    instruction that clobbers them;
  - or shrink `movem` lists based on which callee-saved registers are
    actually live across call boundaries.
- Scope/gate: register allocator change; must respect fixed-register call
  boundaries and reentrancy.
- Benefit: smaller prologues/epilogues and less register pressure.
- Effort: medium-high.

### A.10 Direct push from memory for call arguments
- Problem: an argument register is often loaded from memory and then pushed:
  ```asm
  move.b  (a0,d2.w),d3
  move.b  d3,-(sp)
  ```
  M68K can push directly from memory:
  ```asm
  move.b  (a0,d2.w),-(sp)
  ```
- Fix: a narrow backend peephole or argument-emission special case when the
  argument register is defined by an immediately preceding memory load and
  the address/index registers are unchanged.
- Scope/gate: backend-only; must not cross calls, stores, or address
  register updates.
- Benefit: one fewer instruction and register per such argument.
- Effort: medium.

## Decisions (won't do)

- 4.7 Avoid fixed A5 frame slots for simple locals - rejected. The peephole
  version miscompiled and saved only ~3 move pairs; the full vreg-based
  solution is the only viable path and is not pursued.
- Frame-pointer omission Phase 2 (movem save-size tracking) - rejected; Phase 1
  covers the bulk of leaf helpers.
