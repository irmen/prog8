# M68k Register Allocation - Stage 2: The Allocator

**Parent design:** `ideas/m68k-register-allocation.md`
**Prerequisite:** Stage 0 complete, including Item 2 (byte-identical golden
harness in place and green). Do not start Stage 2 without the corpus harness;
it is the only automated defense against empty-map regressions once the
allocator begins populating `regAllocation`.
**Additional prerequisite:** Stage 1A and Stage 1B gates green (byte-identical
output with empty allocation map).
**Additional prerequisite:** Decisions §7.1 items 1-4 of the design resolved and
recorded (see "Decisions required before implementation" below).
**Status:** Complete. All 10 steps implemented; allocator is now the default
path (Stage 4).
d0-cache retirement via gated direct emission, spill-under-pressure proof
(int and float, end to end in QEMU), and branch-target CFG edges. Corpus
compiles/assembles/runs correctly with 100% register residency and no stale
memory traffic; code size falls on every metric (ELF -4..-14%, code lines
-5..+1% - see Validation). See "Implementation progress" below.
**Gate to next stage:** green.

**Policy update (supersedes the declared-clobber metadata for asmsubs described
below):** an M68K `asmsub` must not declare `clobbers(...)`; its body preserves
the callee-saved and reserved registers it uses, and a call to it destroys only
the caller-saved set, as for an ordinary subroutine. Only extsubs forbid the whole
allocatable pool for values live across the call. See
`ideas/m68k-register-allocation.md` §2.6. Declared clobbers remain in use for
`extsub` targets. Amiga library calls use a fixed destroy set in the allocator
(A6 and D6).

## Implementation progress (as of Oct 2026)

**Golden harness (prerequisite):** `compiler/test/codegeneration/TestM68kGoldenAsm.kt`
compares byte-identical `.asm` for 72 corpus entries (all 3 qemu68k examples, 13
curated amiga examples x amiga500+amiga1200, 4 top-level examples x 3 targets;
optimized + unoptimized). Goldens under `compiler/test/golden/m68k/`. Regenerate with
`UPDATE_M68K_GOLDEN=1`. Verified byte-identical against the pre-Stage-0/1 commit.

**New file:** `codeGenM68k/src/prog8/codegen/m68k/RegisterAllocator.kt`
(`M68kRegisterAllocator`), returning `AllocationResult(map, calleeSavedBySub,
spillDemandBySub)`.

Done (analysis-level, gated off by default so shipping output is unchanged):
- Step 1: convention fully centralized in `M68kRegisterConvention` (caller-saved
  split, reserved set, return-value locations, callee-saved pools). The asmsub
  arg/return slot table (`CallingConventionSlot` -> register name) is shared
  between codegen and allocator via `fixedRegister`; per-call-site actual slots
  come from the Step 8 IR metadata.
- Step 2: intraprocedural liveness (gen/kill fixed-point recursing into IRLoopChunk)
  + liveness-derived interference graph (replaces the packer's layout-order model).
  NOT yet validated against nested loops / early returns / jump tables.
- Step 3: class-aware greedy coloring; live-across-CALL is safe by construction
  (callee-saved-only pool).
- Item 4: class constraints from RegisterOperand.resolvedAllocationHint
  (PREFER_ADDRESS -> A2-A4, PREFER_DATA -> D2-D6, float -> FP2-FP7).
- Item 3: CALL boundary modeled (return-value vregs pinned to d0/fp0; pool and
  kill set disjointness asserted).
- Step 5 (partial): prologue/epilogue movem emitted and paired via emitFrameUnlk()
  (the single exit path for returns + tail-jumps), gated behind registerAllocationEnabled.
- Step 6: spill slots are appended to the A5 frame for framed and frameless
  subs alike (`appendSpillSlotsToFrame`, mirroring StackFrameLayout geometry);
  spilled vregs resolve to their slots through `operand()`/`storeOperand()`/
  `floatOperand()` at use points. Covered by frame-layout and codegen unit tests.
 `%option noframe` subroutines are exempt: they manage the machine stack
 themselves, so framing them is rejected with a compile error instead of
 silently emitting `link`/`unlk` (falling back to the static register file
  would be unsound under vreg reuse). The allocator also leaves these
  subroutines completely unallocated: generated `movem` saves would change
  the stack layout expected by their hand-written assembly (notably the
  startup routine's saved original stack pointer). Covered by unit tests.
- Step 7: return-value pinning wired end to end (§2.7): the allocator pins each
  call result to its boundary register (D0 for default int/pointer, FP0 for
  default float, slot register for slot-annotated results; memory and
  status-flag results unpinned) and colors it into that register's class
  regardless of later-use hints. Caller and callee marshal through D0/FP0;
  self-moves where the vreg already lives in its slot register are elided
  (`translateReturnValue`, `LOADHR`/`STOREHR`, int and float). Multi-value
  calls stay memory-based and emit no return moves. Covered by allocator
  pinning tests and call-boundary codegen tests.
- Step 8: per-call-site fixed-register boundary metadata keyed by subroutine and
  call ordinal (`AllocationResult.callSiteEffects`): slot reads/defs from IR
  locations plus declared asmsub clobbers (and a6 for Amiga library calls).
  The allocator forbids destroyed pool registers for values live across the call
  (`forbiddenByVreg`); ordinary Prog8 calls use the uniform caller-saved kill
  set and get no entry. Covered by allocator unit tests.
- Step 9: `ImmediateCallOptimization` applies to spilled operands only; register-
  resident arguments skip forwarding and use the normal call-boundary path.
  Covered by codegen unit tests.
- Step 10: per-subroutine vreg reuse (`renumberVregsPerSubroutine`, flag-gated).
  Each subroutine's vregs are compacted above the highest number used outside
  subroutines (reserved >= 99000 untouched; raw regfile-referencing asm bails
  out). Because numbers are shared across subroutines, all number-keyed state
  is per-sub: the allocation map lives in `AllocationResult.mapBySub` and is
  swapped into `AsmGen.regAllocation` at sub boundaries (with per-sub type map
  and d0-cache clears). Covered by renumber + cross-sub independence tests.
- Validation gate (§7.1 #6): `AllocationResult.statsBySub` reports per-sub
  residency, spill count and max live per class; liveness validated by unit
  tests over nested loops, early returns and jump tables (current coarse CFG
  handles these shapes; full branch-edge modeling remains future work). Corpus
  numbers measured - see Validation below.

Residual work (resolved Oct 2026 - see below):
- Translators routed most computation through the d0 scratch
  (`emitLoadD0`/`emitStoreD0`). RESOLVED: gated direct emission
  (`directDataPair`/`directDataDst`/`directDataMove`/`directDataSrc`/
  `directMoveSrc`/`directExtend`/`directFloatDst` in `AsmGen`, consumed by all
  six translators). Gated, not deleted: the d0 cache machinery stays for the
  legacy path because deleting it would change default-path output and break
  the golden gate; under allocation every load-compute-store round trip that
  can be expressed directly now is. Deviation from the "Additional design
  notes" plan (which called for full deletion) is intentional and recorded.
- Spill path (Step 6) was exercised only by unit tests. RESOLVED: two
  spill-forcing programs validate it end to end (both assemble with vasm and
  run correctly in QEMU, agreeing with the default path): seven
  simultaneously-live WORDs spill r6/r7/r8 to A5 frame slots, and seven live
  floats spill through the FP pool to frame slots.
- Liveness used the coarse chunk-sequential CFG (+ loop back-edges).
  RESOLVED: `buildCfg` now models branch-target edges (conditional taken
  edges, unconditional/exit/return termination, JUMPI to all labeled chunks,
  CALLI fall-through). Two discriminator tests prove the old model miscompiled
  (shared one register for two branch-co-live values); they fail on the
  sequential-only CFG and pass with edges.

Flag plumbing: `-m68kregalloc` CLI switch -> CompilationOptions.m68kRegisterAllocation
-> M68kCodeGenerator(allocateRegisters). Default false. The residual items above
have landed and the spill-forcing programs were validated in QEMU. The flag stays
experimental until Stage 4 makes the allocator the only path. `:codeGenM68k:test`
and the golden harness pass (default path output is unchanged by Stage 2).

Stage 3 finding (Oct 2026): under `-m68kregalloc`, the peepholes removed in Stage
3a fire only in the `%option noframe` startup routine `init_system` (amiga500 and
amiga1200). The allocator never allocates noframe subroutines, so that code keeps
its regfile round-trips: 5 bounce pairs per amiga program, none on qemu68k. See
`m68k-regalloc-stage3-teardown.md`.

## Validation (Step 10 gate, measured Oct 2026)

Corpus: fibonacci, numbergame, primes (top-level) + maze, textelite,
qemubootinfo (qemu68k), each compiled with and without `-m68kregalloc`.

Output-level metrics (default -> flag):

| program      | .asm bytes | code lines | .elf bytes | regfile traffic | BSS vregfile |
|--------------|-----------|------------|------------|-----------------|--------------|
| fibonacci    | -4%       | -1%        | -6%        | 126 -> 0        | 126 -> 64    |
| numbergame   | -3%       | +0%        | -5%        | 204 -> 0        | 280 -> 77    |
| primes       | -3%       | +1%        | -4%        | 99 -> 0         | 130 -> 39    |
| maze         | -10%      | -5%        | -14%       | 749 -> 0        | 736 -> 163   |
| textelite    | -9%       | -1%        | -11%       | 1646 -> 0       | 2119 -> 165  |
| qemubootinfo | -8%       | -2%        | -12%       | 543 -> 0        | 798 -> 550   |

Allocator stats over the same programs (refs = operand references):

| program      | subs | refs | residency | spills | max live D/A/F |
|--------------|------|------|-----------|--------|----------------|
| fibonacci    | 11   | 151  | 100%      | 0      | 2/1/0          |
| numbergame   | 19   | 242  | 100%      | 0      | 3/1/0          |
| primes       | 11   | 122  | 100%      | 0      | 3/1/0          |
| maze         | 27   | 977  | 100%      | 0      | 4/1/0          |
| textelite    | 75   | 2108 | 100%      | 0      | 5/1/0          |
| qemubootinfo | 18   | 631  | 100%      | 0      | 3/1/0          |

Correctness: all 6 flag outputs assemble cleanly (vasm with backend flags);
fibonacci, primes and qemubootinfo run correctly in QEMU (correct fib
sequence, 54 primes, bootinfo tables + clean exit). maze/textelite/numbergame
are interactive and were compile+assemble validated only. Default-path output
is byte-identical (golden harness green).

Bugs found and fixed by this validation:
- Naive per-sub renumbering collided in the program-wide allocation map (same
  number, two subs -> wrong register, e.g. byte vreg emitted as `move.b a2`).
  Fixed with per-sub maps swapped at sub boundaries (+ per-sub type map and
  d0-cache clears).
- `bitOpMem` bypassed the allocation map (`bclr` on a stale BSS slot for an
  address-resident vreg) and never invalidated the d0 cache. Fixed with
  d-register fast path, An staging through d0, and cache invalidation on all
  modifying paths (also fixes pre-existing staleness on the default path).
- 68881 rejects `fmove.s`/`fadd.s`/etc. with an FP-register source ("illegal
  opcode extension"); FP-to-FP moves and FP-sourced arithmetic must use the
  bare form, memory sources use `.s`. The latent bug (invisible while no float
  was ever resident) surfaced in float load/store, binary ops, SQRT, FCOMP,
  FABS/FSIN/FCOS/FTAN/FATAN/FPOW/FLN/FLOG/FROUND/FFLOOR, call-argument and
  call-result marshalling. Fixed with `emitFmoveFloat`/`emitFloatOpSrc`
  plus a codegen test pinning the suffix rules.
- Direct-emission work briefly cleared the d0 cache on the legacy LOADX path
  (`invalidateD0CacheForSlot` outside the flag gate), adding redundant loads
  to default output. Caught by the golden harness; fixed by moving the
  invalidation inside the gated branch. Goldens are byte-identical again.
- Review-driven fixes (Oct 2026): 68000 LONG multiply/divide/modulo helpers
  (`p8_umult32` etc.) clobber D2-D7 with no IR call edge, silently corrupting
  allocated registers. Fixed by saving/restoring D2-D7 around the helper
  `bsr`, gated on the flag (A6 needs no save: only consumed by the tail jump
  and preserved on both continuations; D7 was already saved by the
  loop-counter logic). Covered by an amiga500 codegen test.
- Review-driven fix: pure definitions built no interference edges, so a dead
  definition could share with a value live across it - which the direct
  paths then overwrite. Interference entries now include defs
  (`live + uses + defs`), proven by a discriminator test that shares on the
  old model and separates on the new one. Corpus stats unchanged (still 100%
  residency, 0 spills).
- Runtime-validated fix (amiga500, real OS): the AmigaOS library ABI
  (`exec`, `dos`, `graphics`, `lowlevel`, `iffparse`, ...) preserves only
  d2-d5, a2-a5 and a6 - d0/d1/d6/d7/a0/a1 are volatile. But the allocator's
  pool treats **d6** as callee-saved and `fixedBoundaryEffects` only added
  `a6` to the destroy set for `AmigaLibrary` targets, so a live value parked
  in d6 across a `callfar` to an OS library was silently corrupted (guru
  meditations and freezes in lowlevel-timer, stream-file, iff, stream-disk).
  Fixed by adding `d6` to the destroyed set for AmigaLibrary boundaries; d7
  is already saved separately by the loop-counter logic. Under qemu68k there
  is no OS-library call edge, so this never showed in the qemu corpus.
 - Runtime-validated fix, superseding the d6-only note above (amiga500, real
   OS): the systematic cause was undeclared scratch in hand-written bodies.
   The m68k stdlib declarations are now audited, including shared m68k,
   Amiga 500/1200, and QEMU 68k sources. Declared asmsubs use their precise
   clobber sets; Amiga library calls use the backend ABI effects; only asmsubs
   without usable metadata and genuinely unknown fixed boundaries forbid the
   whole allocatable pool. Covered by tests for both declared and undeclared
   asmsub scratch. extsub declarations carry no clobber lists (bank/LVO/slots
   only), so that knowledge remains in the backend.

FP precision semantics (accepted): the bare 68881 forms compute in extended
precision, and that matches what the legacy path always did - its accumulators
(fp0/fp1) are extended registers and `.s` only converted at memory
boundaries. Direct forms therefore preserve legacy value flow exactly; the
only change is fewer single-precision round-trips through memory, i.e.
strictly more accurate intermediates. Results can differ from default output
in last bits where the default rounded an intermediate, and a spilled value is
rounded on spill/reload exactly as before. This matches standard register
allocation semantics. Assembler behavior verified with vasm 2.0f:
`fmove.s fp0,fp2`, `fadd.s fp0,fp2` and `fcmp.s fp1,fp0` are rejected with
error 2035, while the bare forms assemble.

Read against §7.1 #6 / success metrics: residency target (>=80%) exceeded
(100%, zero stale refs); DATA pool is the bottleneck as hypothesized (peaks at
5/5 on the corpus, exceeded only by the spill-pressure programs); ADDRESS/FP
pools have ample headroom. Final sizes (default -> flag): ELF -4..-14%,
asm bytes -2..-10%, code lines -5..+1% (fibonacci -1%, numbergame +0%,
primes +1%, maze -5%, textelite -1%, qemubootinfo -2%). The residual +1% on
primes is movem prologue/epilogue overhead (7 added lines) outweighing bounce
savings in a program of many tiny subroutines - inherent to the callee-saved
design, not to codegen shape. Spill demand on the corpus is still 0, but
frame-spill codegen is now proven end to end by the spill-pressure programs
below, which run correctly in QEMU and agree with the default path.

Spill-pressure proof (Oct 2026): `pressure.p8` (seven simultaneously-live
WORD call results) spills r6/r7/r8 to A5 frame slots with movem saves, and
`fpressure.p8` (seven live floats) spills through the FP pool to frame slots
with fmovem saves. Both assemble with vasm, print SPILL-OK 28 / SPILL-OK in
QEMU under `-m68kregalloc`, and agree with the default-path runs.

This stage makes the allocator decide the vreg -> physical-register mapping per
subroutine.

## Background

See `ideas/m68k-register-allocation.md` §0 (goals) and §1 (current state).
Summary of the convention this allocator is built against (final all-stack
convention, IR format 8, stack model already implemented):

- Normal subs: caller pushes args left-to-right into word-packed slots
  (byte/bool/word = 2 bytes, long/float/pointer = 4), `jsr`s, pops the whole
  area after return (caller cleanup). Callee reads args from positive A5
  offsets, accumulating from the rightmost arg at `8(a5)` by per-arg slot width.
- asmsub/extsub: args go into fixed hardware slots via `CallingConventionSlot`
  (slots 10..17 -> D0..D7, 18..24 -> A0..A6, 25..32 -> FP0..FP7).
- CALL does no register save/restore; it is a bare `jsr`/`bsr`.
- Caller-saved (killed by CALL): D0, D1, A0, A1, FP0, FP1.
- Callee-saved (preserved by CALL): D2-D6, A2-A4, FP2-FP7.
- Reserved (never allocated): D7 (repeat loop counter), A5 (frame pointer),
  A6 (AmigaOS library base).
- Allocatable pool: 5 data (D2-D6), 3 address (A2-A4), 6 FPU (FP2-FP7).
- Scratch registers D0/D1/A0/A1/FP0/FP1 never hold allocated vregs (§2.3).
- Return values pinned at CALL boundary: single scalar/pointer -> D0; single
  float -> FP0; multi-value -> unchanged (memory slots/LOADHR).
- asmsub/extsub boundaries are not uniform CALLs; they carry per-call-site
  metadata (regs read, defined, destroyed) (§2.6).

## Known blockers carried from Stage 1B (resolved)

Stage 1B deliberately left two opcode groups location-agnostic in name only.
They generate correct assembly with the empty `regAllocation` map but break
once the allocator populates it. Both are now guarded, so the allocator never
produces an invalid operand for them. The guards use the fallback path below,
not the `PREFER_ADDRESS` constraint.

### `JUMPI`/`CALLI` memory-indirect addressing on 68020+

`InstrControl.kt` emits `jmp ([operand(reg)])` / `jsr ([operand(reg)])` on
68020+. Memory-indirect addressing requires an address-register base. With an
empty map `operand(reg)` is a regfile address and the mode is valid; with an
allocated data register it is not.

**Fix:** honor the `INDIRECT_TARGET` / `PREFER_ADDRESS` class constraint in the
allocator, with an `a0` staging fallback for cases where the pointer vreg cannot
be kept in an address register.

### Memory-form word shifts/rotates in `InstrBitwise.kt`

`LSRM`, `ROLM`, `ROXLM`, etc. emit `op.w  operand(reg)`. This is only valid
when the operand resolves to a memory address. With an allocated data register
the instruction is misinterpreted as a register-count shift/rotate.

**Fix:** either (a) keep these operands spilled to memory via allocator spill
policy, or (b) re-select to the register form (`move addr,d0` / `shift #1,d0` /
`move d0,addr`) when the operand is register-resident.

## Register classes (§4)

- DATA (Dn): integer arithmetic on byte/word/long.
- ADDRESS (An): pointers and address arithmetic; prefer A-regs so `(An)`,
  `(An)+`, `-(An)`, `(An,d0)` addressing modes are usable.
- FPU (FPn): `float` values (32-bit 68881 singles).

Each class has its own interference graph (or class-tagged unified graph), sized
by available registers after removing reserved and scratch registers.

Class of a register is derived, not stored on `CpuRegister` (which is shared
with 6502/VM and has no m68k class notion). Define in `codeGenM68k`:

    enum class RegClass { DATA, ADDRESS, FPU }
    fun regClassOf(r: CpuRegister): RegClass = when (r) {
        CpuRegister.D0..CpuRegister.D7 -> RegClass.DATA
        CpuRegister.A0..CpuRegister.A6 -> RegClass.ADDRESS
        CpuRegister.FP0..CpuRegister.FP7 -> RegClass.FPU
        else -> error("6502 register $r has no m68k class")
    }

Expected demand (hypotheses to measure, not assumptions): DATA is the
bottleneck; ADDRESS is probably least pressured but pointer temporaries must be
counted from liveness; FPU likely over-provisioned. The empirical gate (§7.1 #6)
focuses measurement on the data pool first.

Class requirements flow from translator to allocator (§4.1): index -> DATA,
pointer -> ADDRESS. The mechanism is an open decision (§7.1 #2).

## Liveness analysis (§3.1, intraprocedural, required)

Standard per-subroutine allocator needs correct liveness within each subroutine.
Structured-IR input (available): derive gen/kill from `IRInstruction.registerAccesses`
with `OperandDirection` (USE/DEF/USE_DEF), via `uses`/`definitions` keyed on
`VirtualRegister` (`IntReg` and `FloatReg` strictly separate). `registerAccesses`
already aggregates registers inside `memory` (`Indexed`/`Indirect`), `target`
(`Indirect` pointer), and `callSite` (arguments/results/bank/pointer). Use
`RegisterOperand.allocationHint` (`PREFER_DATA`/`PREFER_ADDRESS`) as the
class-constraint input.

Steps:
1. Build CFG of the subroutine's code chunks.
2. Compute liveness via gen/kill + iterative dataflow -> `liveIn`/`liveOut`
   per chunk.
3. Build interference graph from simultaneous liveness: edge between any two
   vregs live at the same point.
4. Greedy-colour vregs onto physical registers, respecting classes, spilling
   what does not fit.

Liveness drives: call-spill (vregs live across CALL), callee-saved usage
(prologue/epilogue mask), and spilling.

Reuse of `RegisterPacker` (`codeGenIntermediate/RegisterPacker.kt`):
- Likely reusable: gen/kill dataflow, interval construction, greedy coloring,
  IR rewrite (mechanical regnum replacement).
- Needs validation before reuse: CFG edge construction (`buildCFG`) against
  calls, early returns, fall-through, jump-table dispatch.
- Do not reuse: interference model (derives conflicts from instruction-level
  aliasing and judges overlap against flat concatenation of chunks in layout
  order; layout order is not execution order once loops exist). Replace with
  liveness-derived interference graph.

Validate the new interference model against nested loops, conditionals, early
returns, jump-table dispatch before trusting the allocator.

No interprocedural analysis needed: the uniform calling convention makes every
CALL identical. Interprocedural analysis remains a later optimization only.

## Spilling (§5)

When register pressure exceeds physical registers in a class, spill vregs to
memory, inserting stores/loads at definition/use points.

Spill targets:
- Frame slots (primary, intended): per-invocation, recursion-safe, cheaper
  addressing (`move.l -8(a5),d0`). The frame layout pass currently budgets
  locals, incoming args, and `frameVregSlots` (re-entrant subs only); generic
  spill-slot budgeting is Stage-2 work (§7.1 #3).
- SP-reserved spill area (fallback for frameless subroutines, `hasFrame == false`,
  i.e. `frameSize == 0 && incomingSize == 0`): reserve a fixed-size area once
  with a single `subq`, keep SP stable throughout the body; address spills via
  fixed SP displacement. Per-invocation, recursion-safe for register state.
- BSS regfile (`p8_regfile`): demoted to spill area. Static storage, so not
  per-activation - unsound for a subroutine on a call-graph cycle once the
  stack model enables recursion, and shared by every subroutine. With
  per-subroutine vreg reuse, a caller spilling r5 to offset X collides with a
  callee spilling its own r5 to the same offset. BSS spill slots must be
  program-wide unique. Interrupt handlers must never share BSS spill slots
  with code they can interrupt.
- Rejected: naive push/pop around a live range (not sound for general live
  ranges that are not LIFO-nested; can produce different stack depths at CFG
  joins or pop the wrong value).

Backend helper calls must be allocator-visible: longmath routines, `math._sqrt_*`,
`prog8_lib.strcmp` are `bsr` calls with no IR-visible CALL edge, taking/returning
in d0/d1. Each must be modeled as a call boundary killing caller-saved regs.
Helpers need no changes beyond honoring §2.6 clobber policy and the d7
reservation. Callee-saved prologue/epilogue is emitted inline per subroutine by
`AsmGen`, not as a shared helper.

Recursion note: register allocation alone never made subroutines recursive; with
the frame model implemented, parameters/locals of framed subroutines are
per-activation when Stage 2 starts. Virtual registers are per-activation only
for re-entrant subs (via `frameVregSlots`), static regfile otherwise. The
recursion test stays scoped to register-state preservation.

Per-subroutine vreg-number reuse (resetting `RegisterPool` per subroutine) is a
safe optional extra because no value is live in a shared hardware register
across a CALL - but the associated spill-area shrink only applies to
stack/frame spills, per the BSS caveat above.

## Prologue / epilogue (§6)

For each subroutine, emit:
- At entry: save callee-saved registers actually used, e.g.
  `movem.l d2-d6/a2-a4, -(sp)`.
- At every exit (`rts`): restore them, e.g. `movem.l (sp)+, d2-d6/a2-a4`.

On framed subroutines this nests inside frame setup: `link a5,#-N` stays first,
`unlk a5` last before each `rts`, so movem save/restore happens with frame
active. Only used registers are saved. Reserved registers (D7, A5, A6) never
appear in the save mask by construction. FPU callee-saved regs get matching
`fmovem fp2-fpN,-(sp)` / `fmovem (sp)+,fp2-fpN`.

## Implementation steps (Stage 2)

1. Define convention constants in the m68k backend: caller-saved vs callee-saved
   split, reserved set, return-value locations, asmsub arg slots.
2. Implement/repair intraprocedural liveness and interference (reuse packer
   gen/kill dataflow, interval construction, greedy coloring; validate CFG;
   replace interference model with liveness-derived one).
3. Class-aware greedy colouring: map vregs -> physical D/A/FP registers,
   treating CALL as killing caller-saved registers.
4. Wire the mapping into `operand()` / `storeOperand()` (typed `RegisterOperand`
   lookup; float vregs resolve through the same entry point). Register-to-register
   code (`add.l d1, d0`) now falls out of existing translators with no per-opcode
   special cases - the payoff of Stage 1.
5. Emit prologue/epilogue `movem` saves for callee-saved registers used (§6).
6. Spill under pressure to frame slots, SP-reserved fallback for frameless
   subs (§5).
7. Pin the return-value boundary (§2.7): callee moves results to D0/FP0 before
   `rts`; caller consumes them from there.
8. Model fixed-register asmsub/extsub boundaries with per-call-site metadata (§2.6).
9. Restrict `ImmediateCallOptimization` to spilled operands (decision taken):
   only applies to vregs the allocator has spilled (those actually in
   `p8_regfile`/stack); register-allocated arguments handled by the
   allocator's own call-boundary marshalling. Keeps the pass (and inline-memcopy
   path depending on it) working during bring-up. Eventual goal: delete it
   entirely when register argument passing lands, folding immediate-forwarding
   into call-boundary marshalling on allocator liveness (Stage 3 teardown).
10. Optionally reset `RegisterPool` per subroutine to allow vreg reuse (§5).

Internal phasing for "still working compiler":
- Phase A (steps 1-3): analysis only, no output change; compiler builds and
  behaves identically.
- Phase B (step 4): wiring; output changes. Gate: correctness on corpus.
- Phase C (steps 5-8): prologue/epilogue, spill, return pinning, metadata.
- Phase D (steps 9-10): cleanup/optimization.

## Decisions required before implementation (§7.1)

These are now resolved and recorded. Stage 2 is delegable once the Stage 1A/1B
gates are green and the liveness analysis handles `IRLoopChunk` correctly.

1. **`operand()` / `storeOperand()` width semantics.** (DECIDED) `operand()`
   returns the allocated register name only; it does not perform width extension.
   The translator emits explicit extension at the use site against the allocated
   register, using a helper such as `extendToWidth(hwReg, fromType, toType)` on
   `AsmGen`. This replaces `loadRegOrZeroExtendToD0` for register-resident
   operands; spilled operands keep the existing d0-based extension path.

2. **Class-constraint mechanism (§4.1).** (IMPLEMENTED) IR-level annotation.
   `RegisterOperand.allocationHint` carries `PREFER_DATA` for `INDEX` operands
   and `PREFER_ADDRESS` for `POINTER_BASE` / `INDIRECT_TARGET` operands (set in
   `IRInstructionsFactory`). `RegisterOperand.resolvedAllocationHint` falls back
   to the role-based default when the hint is NONE, so the constraint survives
   even when not explicitly set. `IRTextCodec.parseRegisterOperand` derives the
   hint from the slot role, so it survives `.p8ir` round-trips. The Stage 2
   allocator reads `resolvedAllocationHint` and assigns the vreg to the
   appropriate physical register class; spilled vregs ignore the hint and the
   translator falls back to scratch staging.

3. **Spill-slot allocation policy (§5).** (DECIDED)
   - Compute worst-case spill demand per register class from the live-range
     pressure found during coloring.
   - For framed subroutines, append spill slots to the A5 frame layout and add
     their size to `IRSubroutine.frameSize`. Spills are addressed as negative A5
     offsets.
   - For frameless subroutines, reserve a fixed-size area on the stack once at
     entry (`subq.l #S,sp`) and release it before exit (`addq.l #S,sp`); SP is
     stable throughout the body, so fixed SP displacements are valid.
   - The BSS `p8_regfile` is demoted to a last-resort spill area only for
     non-reentrant code; it must not be used for values live across a call-graph
     cycle because it is shared by all activations.
   - Spill code is emitted at use points by `operand()` / `storeOperand()`, which
     know each vreg’s spill location. No IR rewrite for spilling.

4. **Call-site metadata representation (§2.6).** (DECIDED) Backend table keyed
   by IR instruction index (i.e. by the `CALL`/`CALLFAR`/`CALLI` instruction
   within the subroutine). The table records, for fixed-register boundaries
   (`asmsub`, `extsub`, Amiga library calls), the set of hardware registers read,
   defined, and destroyed. Ordinary Prog8 subs use the uniform caller-saved kill
   set (D0/D1/A0/A1/FP0/FP1) and need no table entry. Keeping the metadata in the
   backend avoids mutating the IR after frame layout.

5. **Fate of `ImmediateCallOptimization` (Stage 2 step 9).** (DECIDED) Restrict
   to spilled operands for bring-up; delete and fold into call-boundary
   marshalling when register-parameter convention lands. Register-resident
   arguments do not need forwarding because they are already in registers.

6. **Empirical validation gate.** (Human judgment, stays open by design.) Are
   the per-class pools big enough, and does liveness survive programs with
   complex control flow? Per §4.0, focus measurement on the data pool (D2-D6)
   first, then validate the address-pool estimate from real IR liveness
   (short-lived pointer temporaries are live between definition and use), with
   FP pool expected to have ample headroom. An agent can run the measurement;
   a human judges the result and decides whether a pool is adequate or the
   convention needs revisiting. See "Success metrics" below for concrete
   collection thresholds.

## Additional design notes

### D0 peephole cache retirement

The `d0CacheReg` / `d0CacheType` cache in `AsmGen` is a transitional optimization
over the old memory-everywhere model. It becomes redundant once values live in
hardware registers. Phase B of Stage 2 retires it **on the allocation path**:
direct-emission helpers (`directDataPair`, `directDataDst`, `directDataMove`,
`directDataSrc`, `directMoveSrc`, `directExtend`, `directFloatDst`,
`emitFmoveFloat`, `emitFloatOpSrc`) let every translator bypass the d0
round-trip when operands are resident, falling back to the legacy path
otherwise. The cache machinery itself is deliberately NOT deleted: removing it
would change default-path output and break the golden gate
(byte-identical empty-map output). All helpers return null unless allocation
is enabled, so default output is provably unchanged (goldens green).

- Direct paths compute in the destination register; spilled operands pay the
  explicit load/store cost; the allocator is responsible for keeping hot
  values in registers.

### Frameless-subroutine spill mechanics

For a frameless subroutine that needs `S` bytes of spill slots, the prologue is:

```asm
    movem.l d2-d6/a2-a4,-(sp)   ; if any callee-saved regs used
    subq.l  #S,sp               ; reserve spill area (lea if S > 8)
```

and the epilogue is the reverse:

```asm
    addq.l  #S,sp               ; release spill area (lea if S > 8)
    movem.l (sp)+,d2-d6/a2-a4   ; restore callee-saved
    rts
```

The spill area sits below the saved callee-saved registers, so `rts` never has
to skip over it. SP is stable between the `subq` and `addq`, so fixed SP
displacements like `move.l d0,-4(sp)` are valid anywhere in the body. Caller-
cleanup of argument areas is independent and happens above the return address.

## Tests (§8)

Unit tests in `codeGenM68k` (reusing `TestRegisterPacker.kt` structure) covering:
- Simple non-overlapping vregs coalesced into one hardware register.
- Overlapping live ranges forced into different registers.
- Cross-chunk liveness (vreg live across multiple code chunks).
- Nested loops and conditionals (layout-order vs execution-order intervals).
- Early returns / multiple exit points (prologue/epilogue symmetry).
- Value live across a CALL kept in a callee-saved register or spilled for its
  whole live range (no live-range splitting initially; simplest policy: spill
  the whole range).
- Call-site metadata: fixed-register call (stdlib asmsub clobber set, Amiga
  library call) yields correct preservation moves/interference (§2.6).
- Marshalling an `@A5`/`@D7` argument saves the reserved register and restores
  it only after all return values and status flags are captured (§2.5).
- Recursion: register-state preservation only.
- Indirect calls (`CALLI`) handled by the uniform convention.
- asmsub/extsub argument slots (D0-D2, FP0-FP1) and return values.
- Float values routed through FP registers.
- Pointers preferred into address registers.
- Reserved registers (D7, A5, A6) never allocated, even under extreme pressure.
- Values live in callee-saved registers across a `repeat` loop survive the
  loop's use of d7.
- asmsub declaring callee-saved or reserved regs in `clobbers(...)` accepted
  and honored as call-site metadata (§2.6); stdlib asmsubs keep working.
- Return value marshalled through D0/FP0 across a call, including when caller's
  destination vreg is allocated to a different physical register.
- Caller and callee both spilling in a call chain: stack/frame spills do not
  collide; BSS spill slots program-wide unique.
- Interrupt handlers save/restore every register they touch.

## Success metrics

Collect the following from the test corpus (m68k examples and representative
real-world programs with complex control flow):

1. **Register residency ratio** — for each subroutine, count vreg operand
   references (reads + writes) and how many resolve to a hardware register via
   `operand()`. Aggregate across the program. Target: ≥80% register-resident
   for typical programs.
2. **Spill pressure** — number of spilled vregs per subroutine and maximum
   simultaneous spill slots. Target: a large majority of subroutines spill 0-2
   values; no subroutine spills more than a small fraction of its live set.
3. **Pool pressure** — maximum simultaneously live vregs per class per
   subroutine. Compare against DATA=5, ADDRESS=3, FPU=6. If ADDRESS pressure is
   repeatedly at the limit, the convention may need revisiting.
4. **Code size** — total `.text` hunk size and instruction count before and
   after Stage 2. Target: measurable reduction (≥10% on average) from
   eliminated load/store round-trips, without any peephole pass enabled.
5. **Code shape** — subroutines should read as register-to-register code with
   `movem` prologue/epilogue, not as regfile memory ops with patches.

Caveat: multi-value returns stay memory-based (§2.7) and asmsub marshalling
keeps a foot in the regfile (§2.8), so `p8_regfile` remains load-bearing for
programs using those features.

## Compiler-stability gate

- Compiler builds throughout (Phase A non-behavior-changing; Phase B+ changes
  output but compiler remains functional).
- `gradle build` (full test suite) passes at each phase boundary.
- Representative programs compile and run correctly (manual verification).
- Metrics (register residency, instruction count) measured and judged by a
  human before proceeding to Stage 3.

Do not start Stage 3 until this gate is green, the Stage 1A and Stage 1B gates
are green, and the liveness analysis correctly handles `IRLoopChunk` and other
complex control flow.
