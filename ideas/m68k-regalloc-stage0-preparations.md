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

## Item 1 - Catalog of emitted bsr/jsr (completed)

Survey of all `bsr`/`jsr` emission in `codeGenM68k`. Classification:
**(a)** hidden call boundary (no IR-visible CALL edge; kills caller-saved regs),
**(b)** fixed-register asmsub/extsub or IR-visible call boundary needing §2.6 metadata.

### Hidden call boundaries (a) — no IR-visible CALL edge

| Site | Location | Routine | Class |
|------|----------|---------|-------|
| startup init | `AsmGen.kt:~719` | `p8_sys_startup.init_system` | (a) |
| startup init phase2 | `AsmGen.kt:~720` | `p8_sys_startup.init_system_phase2` | (a) |
| main entry | `AsmGen.kt:~721` | `p8b_main.p8s_start` | (a) |
| global inits | `AsmGen.kt:~796` | `run_global_inits` | (a) |
| sqrt ub | `InstrArithmetic.kt:~207` | `math._sqrt_ub` | (a) |
| sqrt uw | `InstrArithmetic.kt:~213` | `math._sqrt_uw` | (a) |
| sqrt l | `InstrArithmetic.kt:~219` | `math._sqrt_l` | (a) |
| mul (long) | `InstrArithmetic.kt:~292` (via `emitLongMathCall`) | `p8_umult32` / `p8_smult32` | (a) |
| div (long, 68000) | `InstrArithmetic.kt:~671` (via `emitLongMathCall`) | `p8_udivmod32` / `p8_sdivmod32` | (a) |
| strcmp | `InstrSyscall.kt:~54` | `prog8_lib.strcmp` | (a) |

### Amiga library-base calls (b) — fixed-register, call-site metadata

| Site | Location | Notes |
|------|----------|-------|
| `jsr Symbol(a6)` / `jsr offset(a6)` | `InstrControl.kt:~108,~110` | Amiga CALLFAR; loads a6 from `4.w` or `sys.xxxBase`; args marshalled into D/A regs. Caller-saved clobber; needs §2.6 metadata (regs read/defined/destroyed). |

### IR-visible call boundaries (uniform CALL rule applies)

| Site | Location | Opcode |
|------|----------|--------|
| indirect call | `InstrControl.kt:~77` | `jsr (reg)` / `jsr (a0)` (CALLI) |
| normal subroutine call | `InstrControl.kt:~674` | `bsr $fnLabel` (CALL, including non-inline asmsub) |
| inline asmsub body | `InstrControl.kt:~668` | body pasted inline (not a call) |

### Repeat loop counter (d7)

`AsmGen.kt:~885-995` uses d7 as the physical repeat-loop counter with
save/restore around calls and inline asm. This is the reason D7 is reserved
(§2.5); the allocator must never hand out D7. The IRLoopChunk note in
`IRProgram.kt:~776` documents this.

### Conclusion

All `bsr`/`jsr` sites are classified. The hidden boundaries are entirely within
the listed helper/startup sites; each kills D0/D1/A0/A1/FP0/FP1 (caller-saved)
and must be modeled as such by the allocator (Stage 2). No unclassified sites
remain.

## Item 5 - Survey of ImmediateCallOptimization (completed)

`ImmediateCallOptimization` is defined and used in `AsmGen.kt` (~line 58 data
class, ~937 collection, ~1046/1162 call-boundary handling). It performs
cross-instruction immediate forwarding into calls by scanning backward for
contiguous immediate LOADs preceding a CALL and forwarding their values directly
into argument hardware registers, optionally suppressing dead register-file
stores.

Reach:
- Applies to CALL/CALLI/CALLFAR/CALLFARVB/SYSCALL boundaries.
- Reasons about regfile slots (literal `p8_regfile+N` operands) and liveness
  via a second, overlapping analysis (`isRegisterReadElsewhere`,
  `canSuppressDeadStores`), not through `operand()`.
- Disabled for subroutines containing inline assembly chunks (reads invisible).
- Interacts with the inline-memcopy path.

Effort estimate: moderate. The pass is self-contained in `AsmGen.kt` and does
not span translators. The Stage-2 decision (restrict to spilled operands for
bring-up, then delete when register argument passing lands) is confirmed as
workable: restricting to spilled operands requires only gating the forwarding on
whether the argument vreg is actually in `p8_regfile`/stack, leaving the
register-allocated path to the allocator's own call-boundary marshalling. No
structural change needed for bring-up.

## Item 3 - Status (partial)

Named constants `SCRATCH_D0`..`SCRATCH_FP1` added to `AsmGen.kt`. Core helpers
`emitLoadD0`/`emitStoreD0`/`emitLoadD0FromAddress`/`emitStoreD0ToAddress`,
`loadRegOrZeroExtendToD0`, `loadPointerToA0`, `loadIndexToD0` migrated to use
them. The remaining ad hoc `d0`/`d1`/`a0`/`a1`/`fp0`/`fp1` references in
emitLine strings across `AsmGen.kt` and the translator files
(`InstrArithmetic`, `InstrBitwise`, `InstrControl`, `InstrLoadStore`,
`InstrBranch`, `InstrSyscall`) are left for the Stage 1 `operand()` refactor,
which supersedes them. Constants are in place and available for incremental
migration.

## Item 4 - Status

Helper call sites (longmath, sqrt, strcmp, startup, Amiga CALLFAR) already use
only caller-saved scratch (D0/D1/A0/A1/FP0/FP1) for their transient work and
load/save A6 for Amiga library bases. D7 is reserved and not clobbered by
helpers (repeat-loop save/restore lives in the backend's own repeat handling).
No code change required; convention is already clean. The allocator (Stage 2)
must still model these as hidden call boundaries (Item 1 catalog).

## Item 2 - Status

A full byte-identical golden harness is not yet in place. The existing
`TestStackFrameEmission.kt` provides the pattern (compile to .asm, read lines,
assert). Building the corpus harness is deferred to Stage 1 prep where it gates
the refactor; it can reuse that pattern. No code change in this stage.