# M68k Assembly Peephole Opportunities

Plan for a set of assembly-level cleanups for the m68k backend, based on the
generated output in `amiga-regalloc/` (23 programs, about 43,000 instructions,
Oct 2026 snapshot). Related design: `m68k-register-allocation.md`. The register
allocator is the owner of most of the waste found here, so this plan separates
what a peephole pass should do from what belongs in codegen or the allocator.

**Status:** Draft. Earlier-stage findings (IR and source traces) are recorded below.
Several tier A and B items are better fixed earlier in the chain. Open questions need
answers before implementation.

## Background

The existing `optimizeAssembly()` in
`codeGenM68k/src/prog8/codegen/m68k/AsmOptimizer.kt` runs four passes:
jump-to-next-label, tail call (`bsr` + `rts` to `bra`), redundant `tst` after a
flag-setting move, and redundant `lea base,a0`. None of them looks at data
flow between registers or frame slots.

Reading the generated code shows the following patterns:

- Frame locals are stored and reloaded with no change in between
  (`move.b d3,-13(a5)` followed by `move.b -13(a5),d3`).
- Values are copied through a temporary register before use
  (`move.b -6(a5),d5` / `moveq #0,d6` / `move.b d5,d6`).
- Small constants are loaded with `move.w`/`move.l` where `moveq` is shorter and
  faster.
- Address constants go through a data register before being moved to an address
  register.
- Self-moves and swap-back copies remain in the output.

## Design rule

`m68k-register-allocation.md` §0 says that improvements must come from the codegen
structure. Peepholes are allowed for local rewrites that are cheap to verify. A
peephole must not compensate for a missing allocator feature.

Each item below is tagged with an owner:

- **peephole**: local rewrite over one or two instructions, or a block-local
  liveness check. Implemented in `AsmOptimizer.kt`.
- **codegen**: the rewrite should be done when the code is emitted (for example
  duplicate zeroing in a prologue). Implemented in the translators or `AsmGen`.
- **allocator**: caused by copies and round-trips the register allocator should
  remove (coalescing, frame local promotion). Tracked in the register allocation
  stages, not here.

Peephole passes must not read or write `p8_regfile`. Stage 3 of the allocator
deletes the regfile peepholes; the new passes must survive that deletion.

## Inventory

Counts are from text matching over the `amiga-regalloc/*.asm` snapshot. They are
approximate and count matches, not sites that would change after rewriting.

### Tier A - window-2 peepholes (no liveness needed)

| ID | Pattern | Rewrite | Count | Owner |
| --- | --- | --- | --- | --- |
| A1 | `move.S dN,M` / `move.S M,dN` | drop the reload | 58 | peephole |
| A2 | `move.S dN,M` / `move.S M,dK` (K != N) | keep the store, replace reload with `move.S dN,dK` | 75 | peephole |
| A3 | `move.S dA,dB` / `move.S dB,dA` | drop the second move | 42 | peephole |
| A4 | `move.S dN,dN` | drop the self-move (see safety) | 10 | peephole |
| A5 | `move.w/l #N,dK`, N in -128..127 (or $FF80..$FFFF for .w) | `moveq #N,dK` | about 225 | peephole |
| A6 | `move.b M,dN` / `and.l #$ff,dN` | `moveq #0,dN` / `move.b M,dN` | 9 | peephole |
| A7 | `bCC L1` / `bra L2` / `L1:` | `bCC' L2` | 1 | peephole |
| A8 | `bsr` to a label whose body is only `rts` | drop the call | 1 per program | peephole |

Safety notes:

- **A1, A2, A3**: `move.b` to a data register writes only the low byte, and a
  byte store to memory writes only the low byte. The reload and the register
  copy give the same value and the same N/Z flags. Keep labels on the removed
  line (the existing `applyModifications` already does this).
- **A4**: a move sets N and Z from the value. Removing it changes the flags, so
  the rewrite is only safe if the next instruction does not read the flags
  directly. Check the next instruction, which needs a window of 3.
- **A5**: `moveq` sets all 32 bits. `move.w` and `move.b` already leave the upper
  bits undefined, so no consumer may rely on them. Flags match: N and Z depend only
  on the value, and both instructions clear V and C. Restrict to the range where
  the sign-extended value equals the immediate.
- **A6**: `and.l` and `moveq` both clear V and C and set N and Z from the 32-bit
  result, which equals the byte result because the upper bits are zero.
- **A7**: exact inversion table needed for each condition code. Only one occurrence,
  so it may not be worth implementing.

### Tier B - block-local liveness needed

| ID | Pattern | Rewrite | Count | Owner |
| --- | --- | --- | --- | --- |
| B1 | `move.b M,dA` / `moveq #0,dB` / `move.b dA,dB` | `moveq #0,dB` / `move.b M,dB` when dA is dead | about 230 | peephole |
| B1b | `move.S M,dA` / `move.S dA,dB` | `move.S M,dB` when dA is dead | several hundred | allocator first |
| B2 | `move.l #sym,dN` / `movea.l dN,aM` | `movea.l #sym,aM` when dN is dead | 269 | peephole |
| B2b | `move.l M,dN` / `movea.l dN,aM` | `movea.l M,aM` when dN is dead | 126 | peephole |
| B3 | `lea X,aN` / `movea.l aN,aM` | `lea X,aM` when aN is dead | 11 | peephole |

Liveness definition for this plan: a register is dead after an instruction if the
next access in the same basic block is a full write. Labels, branches, calls and
`rts` end the block, and a register is treated as live at those points. This is
conservative and needs no whole-program analysis.

B2 changes the flags: the original `move.l` sets N and Z on the address value,
while `movea` does not. Confirm that no following branch depends on those flags.

B1b is mostly argument setup in the allocator output (for example `move.w d2,d0`
followed by `move.w d3,d1`). Coalescing in the allocator should remove it. The
peephole version is only a fallback if the allocator does not get there.

### Tier C - codegen or allocator (not peephole)

| ID | Observation | Owner | Notes |
| --- | --- | --- | --- |
| C1 | Prologues clear bytes already zeroed by an earlier `clr.l`/`clr.w` in the same frame (about 42) | codegen | Dedupe in the locals zeroing logic. The `clr.b` pairs in `circles` `drawDisc` are an example. |
| C2 | Frame locals are stored and reloaded across calls and other code (not adjacent) | allocator | Needs local promotion into registers. Not a peephole. |
| C3 | `move.l sys.IntuitionBase,a6` (about 331 loads) before each library call | codegen | Absolute load of 20 cycles on the 68000. Caching it needs the library ABI to preserve A6. Verify in the NDK docs before any change. |
| C4 | Constant shift count via register (`move.w #15,d1` then `asr.l d1,d2`) | codegen | Shift immediates only go to 8. A `moveq` (A5) covers the load; hoisting it is a codegen matter. |

### Not yet analysed

- `divs.l #256,dN` and `divs.w #2,dN` for signed power-of-two division. `asr`
  rounds differently for negative values, so a naive rewrite is wrong.
- Epilogue tail duplication (`movem.l (sp)+`, `unlk a5`, `rts` shared by several
  branches).
- Frame size and `link` for argument-only subroutines (110 `link a5,#0`). These
  need `a5` for arguments, so no rewrite is possible.

## Earlier-stage findings

Tracing the patterns back through the IR and the source shows that several of the
items above start earlier in the compilation chain. Fixing them there is cheaper
than a peephole at the end, and removes more than one asm pattern at a time.

Pipeline for m68k: AST (`compiler/src/prog8/compiler/astprocessing`) -> `IRCodeGen`
with `IRPeepholeOptimizer` -> `M68kCodeGenerator`: `StackFrameLayout`, then
`M68kRegisterAllocator`, then `AsmGen`, then `AsmOptimizer`. The `.p8ir` snapshots
are written after `StackFrameLayout` and before allocation, so they show frame
locals and the zeroing prologue as the allocator sees them.

### Traces

| Asm (`circles`, `blitcop`, `wavc`) | IR (`.p8ir`) | Source | Origin |
| --- | --- | --- | --- |
| `clr.l -6(a5)`, then `clr.b -6`, `clr.b -4/-5/-3` (`circles.asm:3702-3718`) | `storezm.l [-6]` then `storezm.b` on bytes already zeroed (`circles.p8ir:2991-3001`) | `ubyte yy = 0`, `ubyte pendingRadius` (`circles.p8:75-79`) | `StatementReorderer.kt:43-66` adds `x = 0` to every local. `StackFrameLayout.kt:634-664` zeroes the same bytes in the prologue. |
| `moveq #0,d5` / `move.b d4,d5` after `move.b 10(a5),d4` (`circles.asm:3734-3738`) | `loadm.b` then `ext.b` (`circles.p8ir:2995-2996`) | `(1 as word)-radius` (`circles.p8:76`) | Implicit widening. The `loadm`/`ext` pair is not fused. |
| `and.l #$ff,d2` before `divu.w` (`circles.asm:3626-3628`) | `loadm.b`, `mod.b` (`circles.p8ir:2935-2936`) | `index % 10` (`circles.p8:68`) | The zero-extension is emitted inside the backend (`InstrArithmetic.kt:245-380`), so IR cannot see it. |
| `move.w d2,d2` / `move.w d3,d3` before `bsr blitter.line_draw` (`blitcop.asm:1879-1880`) | call argument `r296@s12`, loaded from `[frame:-26]` (`blitcop.p8ir:1590`) | `blitter.line_draw(..., x2 as uword, ...)` (`blitcop.p8:58`) | The allocator does not bind the vreg to the argument register. |
| `bhi L36` / `bra L35` / `L36:` (`wavc.asm:2524-2527`) | `bgtr.w`, `jump`, label (`wavc.p8ir:2476-2481`) | loop in `wavc.p8` | `removeWeirdBranches` does not invert a conditional that jumps over a single `jump` chunk. |
| `addq.b #1,-5(a5)` / `move.b -5(a5),d2` / `cmpi.b #21` (`circles.asm:3680-3685`) | `incm`, `loadm`, `cmpi` on `[frame:-5]` (`circles.p8ir:2956-2958`) | `for index in 0 to 20` | Loop variable kept in frame memory. |

The zeroing duplication reproduces with a small program: the prologue zeroes three
bytes, then each declaration zeroes them again.

### Ranked earlier-stage opportunities

Counts are from the 23 `amiga-regalloc/*.p8ir` snapshots and are approximate.

1. **Promote non-escaping scalar locals and parameters to vregs** (IR, before
   `StackFrameLayout`). Largest opportunity. The snapshots contain 1,843 loads from
   frame locals and parameters (1,111 locals, 732 parameters), 193 loads from locals
   of static subroutines, and 334 frame reloads of a value stored earlier in the same
   chunk. Eligibility can reuse the existing `StackFrameLayout` checks: not
   address-taken, no asm reference, not static-initialised, not explicitly aligned.
   The design docs do not cover this. `IRPeepholeOptimizer.kt:8-16` disabled
   `foldStoremLoadmToLoadr` because regfile spills cost more; promotion avoids that
   problem because vregs get allocator treatment. Start with m68k only, since a vreg
   is not obviously cheaper than a static cell on the 6502.
   Removes: A1, A2, C2, and most of the reload and copy chains.
2. **Remove provably redundant zero stores** after `StackFrameLayout`. About 20
   matches, counted conservatively within a single chunk. A forward "known zero" scan
   over the first chunk is safe for framed and static subroutines alike. Do not remove
   the `StatementReorderer` zero assignment: static subroutines need it on every call.
   Removes: C1.
3. **Fuse `loadm.b`/`loadm.w` with a following `ext`** into a zero- or
   sign-extending load. 240 adjacent pairs. Saves one register copy per instance.
   Either a new load form or a fold in `collapseConversions`.
   Removes: B1, and the copy part of B1b.
4. **Make byte div/mod zero-extension explicit in IR.** Currently the backend emits
   `and.l #$ff` itself. An explicit `ext.b` feeding a word operation would let items
   1 and 3 see it. Medium confidence: this also changes the 6502 IR, so it needs a
   check there.
   Removes: A6.
5. **Invert a conditional branch over a single `jump` chunk.** One asm case (`wavc`).
   Cheap, low payoff.
   Removes: A7.
6. **Argument register hints in the allocator.** Call setup copies and self-moves
   come from the allocator not binding call arguments to their argument registers.
   Removes: A3, A4, B1b. This belongs to the allocator stages, not to IR or the peephole
   pass.

Not earlier than the backend:

- Small constants as `moveq` (A5, about 225 instances). The choice belongs in the
  `LOAD` emitter (`InstrLoadStore.kt`), which knows the value and the size. Not an
  `AsmOptimizer` pass.
- Shift counts above 8 (C4). The backend loads the count into a register.
- Library base reloads before each `jsr` (C3). Removing them needs proof that the
  library base globals are not written after startup.
- Signed division by a power of two (`divs.l #256`). `asr` rounds differently, so
  this needs a fix-up sequence in the backend.

Effect on the tiers:

| Item | Moves to |
| --- | --- |
| A1, A2, C2 | 1 (promotion) |
| C1 | 2 (known-zero pass) |
| B1 | 3 (load/ext fusion) |
| A6 | 4 (explicit zero-extension) |
| A7 | 5 (branch inversion) |
| A3, A4, B1b | 6 (allocator hints) |
| A5, C4 | backend emitter |
| B2, B2b, B3, C3 | remain backend or codegen |
| A8 | remains a peephole (one per program, low value) |

### Correctness issue found on the way

`circles.p8:68`, `uword x = 80 + (index % 10) * 159 / 2`, computes the product in
`ubyte`. Prog8 does not widen expression results, so the product truncates. Test
program (`@shared ubyte index = 9`): the intermediate `ubyte` gives **155**, while
`((index as uword) % 10) * 159` gives **795**. `-warnimplicitcasts` reports nothing.
This may be intended, but it is a silent trap. An AST check could warn when an
expression of byte type is implicitly widened on assignment and contains an operation
that can overflow the byte range.

## Work

1. **Answer the open questions** (below). Settles the scope of tier B and C.
2. **Baseline.** Build with the current tree and record instruction counts and
   hunk sizes over the m68k example set for `amiga500` and `qemu68k`. Store the
   numbers in this document so later changes can be compared.
3. **Tier A passes.** Add one pass per item to `AsmOptimizer.kt`, each as a
   window-2 (or window-3 for A4) function registered in `optimizeAssembly()`.
   Add unit tests next to the existing peephole tests in
   `codeGenM68k/test/TestInstructionSelectionOptimizations.kt`, covering: a
   matching case, a label on the second line, a flag-reading next instruction
   (A4), and a non-matching register or size.
4. **Liveness helper.** Add a block-local liveness function over the
   `TrimmedLine` window, then the tier B passes on top of it. Keep the helper
   separate so it can be reused for allocator checks.
5. **Hand-off of tier C.** Record each tier C item as a task in the register
   allocation stage documents or in `docs/source/todo.rst`. Do not implement them
   as peepholes.
6. **Verification.** After each pass:
   - `gradle :codeGenM68k:test --console=plain` for the pass tests.
   - `gradle installdist installshadowdist --console=plain` to install the
     compiler.
   - Recompile the example set, check that the output size dropped, and run the
     programs on `amiga500` (`-emu2`, Vamos) and `qemu68k`.
   - Full `gradle build` before the final verification.

## Open questions

1. **Is the amiga-regalloc snapshot built with `-m68kregalloc`, or with the
   default allocator?** Stage 4 makes the allocator the default. If the snapshot
   predates that, the counts above must be re-measured.
2. **Scope of tier B.** Implement the liveness-based rewrites in the peephole
   pass now, or wait for the allocator to remove the copies they target?
3. **Target priority.** The 68000 (`amiga500`) and the 68020 (`qemu68k`) differ
   in cycle costs. Should the peepholes be optimized for the 68000 timing, or for
   size only?
4. **Acceptance criteria.** Is a minimum size reduction required on the example
   set (for example at least 1%), or is correctness on the example set enough?
5. **Overlap with Stage 3.** Stage 3 deletes peepholes that the allocator
   subsumes. Should tier A passes be checked against Stage 3 deletions to avoid
   reimplementing something that is removed later?
6. **Promotion scope.** Should local promotion (ranked item 1) be limited to m68k
   for now? The 6502 effect is unknown, and vregs are not obviously cheaper than
   static cells there.
7. **Byte-truncation check.** Should the `circles.p8:68` pattern be a compiler
   warning, an error, or left alone as documented behaviour? The check would go in
   the AST checker.
8. **Which earlier-stage items go into this plan?** The ranked items above could stay
   here, or each could get its own plan document in `ideas/`. Item 1 is large enough
   to need its own document.
