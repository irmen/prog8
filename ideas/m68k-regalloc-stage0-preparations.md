# M68k Register Allocation - Stage 0: Preparations

**Parent design:** `ideas/m68k-register-allocation.md`
**Status:** Decided and delegable. No behavior change.
**Gate to next stage:** All five items complete and verified individually; compiler builds,
tests pass, and output remains byte-identical for items that touch code.

This stage de-risks Stages 1-2. Every item is small, contained, and independently
mergeable. None changes generated output or compiler behavior.

## Background

The m68k backend (`codeGenM68k`, `AsmGen.kt`) currently spills every virtual
register to the flat BSS block `p8_regfile` (or to A5-relative frame slots for
re-entrant subroutines). The goal of the overall effort is to allocate vregs to
real m68k registers (D2-D6, A2-A4, FP2-FP7) instead. Stage 0 prepares the
ground; it does not allocate anything.

Key constraints carried through all stages:
- Scratch registers D0/D1/A0/A1/FP0/FP1 are never allocated; they are permanent
  translator scratch (§2.3 of the design).
- Reserved registers: D7 (physical repeat loop counter), A5 (frame pointer),
  A6 (AmigaOS library base).
- Allocatable pool: 5 data (D2-D6), 3 address (A2-A4), 6 FPU (FP2-FP7).
- Caller-saved (killed by every CALL): D0, D1, A0, A1, FP0, FP1.
- Callee-saved (preserved across CALL): D2-D6, A2-A4, FP2-FP7.
- The stack-frame model is already implemented (`StackFrameLayout`); parameters
  and locals of framed subroutines live in A5 frames.

## Item 1 - Catalog every bsr/jsr the backend emits

**Goal:** Produce the definitive list of hidden call boundaries (bsr/jsr with no
IR-visible CALL edge) that the allocator must treat as killing caller-saved
registers (§5 of the design), and in the same survey collect the fixed-register
asmsub/extsub boundaries that need §2.6 call-site metadata.

**Scope:** Survey work only. No code changes.

**Known hidden boundaries to start from:**
- Longmath helpers: `p8_udivmod32`, `p8_sdivmod32`, `p8_umult32`, `p8_smult32`
  (in `amiga500/m68k_prog8_longmath.asm`).
- `math._sqrt_*`.
- `prog8_lib.strcmp`.
- Startup routines.
- Amiga library-base loads (`move.l 4.w,a6` / `move.l sys.xxxBase,a6` then
  `jsr Symbol(a6)` in CALLFAR, `InstrControl.kt`).

**Deliverable:** A checklist (in this doc or a companion note) of every emitted
bsr/jsr site, classified as (a) hidden call boundary killing caller-saved regs,
or (b) fixed-register asmsub/extsub boundary needing call-site metadata. Include
file and approximate location for each.

**Verification:** Cross-check by grepping the backend for `bsr`/`jsr`/`CALLFAR`
emission and confirming every site is classified. An unclassified site is a
latent "allocator silently corrupts a live value" bug.

**Compiler-stability gate:** No code change; compiler unaffected.

## Item 2 - Build the byte-identical-output test harness

**Goal:** A corpus of `.p8ir` -> asm (or `.p8` -> asm) where any diff fails the
test. This is the Stage-1 gate; having it ready first makes the large mechanical
refactor reviewable the moment it lands.

**Scope:** Test infrastructure only.

**Implementation:**
- Select a representative corpus of test programs (m68k examples plus programs
  with complex control flow: nested loops, conditionals, early returns, jump
  tables, calls, recursion where applicable).
- For each, compile to assembly with the current backend and store the golden
  `.asm` (or a normalized form).
- Add a test that recompiles each and diffs against golden, failing on any
  difference.

**Verification:** Harness runs green on current code; deliberately changing one
translator line makes it fail.

**Compiler-stability gate:** Test-only; compiler unaffected.

## Item 3 - Route all scratch-register references through named constants

**Goal:** Make the scratch set (D0, D1, A0, A1, FP0, FP1) explicit and auditable
before the allocator is added, so Stage 2 does not chase clobbers through
translators.

**Scope:** Mechanical refactor in `AsmGen.kt` and the translator files
(`InstrArithmetic`, `InstrBitwise`, `InstrControl`, `InstrLoadStore`,
`InstrBranch`, `InstrSyscall`).

**Implementation:**
- Define constants on `AsmGen`: `SCRATCH_D0`, `SCRATCH_D1`, `SCRATCH_A0`,
  `SCRATCH_A1`, `SCRATCH_FP0`, `SCRATCH_FP1` (string names or enum, matching
  existing style).
- Migrate scattered ad hoc `d0`/`d1`/`a0`/`a1`/`fp0`/`fp1` references in
  `emitLine` calls to use these constants.
- Helpers `loadRegOrZeroExtendToD0`, `loadPointerToA0`, `loadIndexToD0` route
  through the constants.
- No acquire/release protocol: scratch is permanently reserved (§2.3).

**Verification:** Output byte-identical to pre-change for the corpus (Item 2
harness, once available; otherwise manual diff on a sample).

**Compiler-stability gate:** Byte-identical output; compiler behavior unchanged.

## Item 4 - Make helper call sites convention-clean

**Goal:** Give each runtime helper (longmath, sqrt, strcmp, startup) a declared,
caller-saved-only clobber set per §2.6, and stop ad hoc d7 clobbering (§2.5).
Removes the "d7 clobbered regardless of IR state" special case.

**Scope:** Backend helper call sites and helper declarations.

**Implementation:**
- Declare clobber sets for each helper restricted to caller-saved registers
  where possible (D0, D1, A0, A1, FP0, FP1). Helpers that need D2-D6/A2-A4/
  FP2-FP7 must save/restore them internally.
- Remove ad hoc d7 clobbering; D7 stays reserved (§2.5) but helpers must not
  silently destroy it.

**Verification:** Helpers honor the convention; programs using longmath/sqrt/
strcmp still produce correct results (run representative programs).

**Compiler-stability gate:** Correct output preserved; compiler builds.

## Item 5 - Survey the reach of ImmediateCallOptimization

**Goal:** Determine how large the Stage-2 decision (reimplement on allocator
liveness vs restrict to spilled operands) actually is, so it can be scheduled.

**Scope:** Survey only.

**Implementation:** Locate all uses of `ImmediateCallOptimization` in `AsmGen.kt`
and measure its interaction with regfile slots and inline-memcopy paths.

**Deliverable:** A short note estimating effort and confirming the Stage-2
decision (restrict to spilled operands for bring-up).

**Compiler-stability gate:** No code change.

## Notes on "still working compiler"

All five items are designed to leave the compiler building, tests passing, and
generated output unchanged (byte-identical where code changes occur). Each item
can be merged and verified on its own before the next. No item depends on
another, so they may be completed in any order within Stage 0.