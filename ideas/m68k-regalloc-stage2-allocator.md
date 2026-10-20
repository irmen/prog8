# M68k Register Allocation - Stage 2: The Allocator

**Parent design:** `ideas/m68k-register-allocation.md`
**Prerequisite:** Stage 1 gate green (byte-identical output with empty map).
**Additional prerequisite:** Decisions §7.1 items 1-4 of the design resolved and
recorded (see "Decisions required before implementation" below).
**Status:** Design decided at convention level; internals (§7.1) must be closed
before delegating.
**Gate to next stage:** Allocator produces correct output for the test corpus
and representative programs; tests pass; register-residency metrics improve.

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

2. **Class-constraint mechanism (§4.1).** (DECIDED) IR-level annotation.
   `RegisterOperand.allocationHint` already carries `PREFER_DATA` for `INDEX`
   operands and `PREFER_ADDRESS` for `POINTER_BASE` / `INDIRECT_TARGET` operands
   (set in `IRInstructionsFactory`). The Stage 2 allocator reads this hint and
   assigns the vreg to the appropriate physical register class. If the vreg is
   spilled, the hint is irrelevant and the translator falls back to the current
   scratch-register staging.

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
hardware registers. Phase B of Stage 2 removes the cache entirely:

- Delete `d0CacheReg`, `d0CacheType`, `invalidateD0Cache*`, `emitLoadD0`,
  `emitStoreD0`, and the related comment block.
- Replace remaining callers with direct `move` emission or with
  `operand()`-based helpers.
- Spilled operands pay the explicit load/store cost; the allocator is
  responsible for keeping hot values in registers.

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