# M68k Register Allocation - Stage 1: Location-Agnostic Instruction Selection

**Parent design:** `ideas/m68k-register-allocation.md`
**Prerequisite:** Stage 0 complete (at minimum: byte-identical test harness from
Stage 0 item 2; named scratch constants from item 3 are part of this stage's
enforcement work).
**Status:** Decided and delegable. Behavior-neutral by construction.
**Gate to next stage:** With an empty allocation map, generated assembly is
byte-identical to pre-change output for the test corpus. Compiler builds and
tests pass.

This document covers Stage 1A: the mechanical location-agnostic refactor.
Stage 1B (indexed/indirect addressing-mode re-selection) is split out into
`ideas/m68k-regalloc-stage1b-indexed-indirect.md` because it depends on the
class-constraint mechanism and genuine per-opcode re-selection, not a string
swap.

This is the bulk of the preparatory work. Instruction selection becomes
location-agnostic: "where does this vreg live" becomes an input to codegen
rather than a rewrite of its output. No allocator exists yet; the allocation
map is empty and every query returns the regfile address.

## Background

The backend's instruction selection is currently hardwired to memory operands:
every IR vreg is emitted as an absolute memory operand (`p8_regfile+N` or an
A5-relative frame slot). An absolute memory operand and a CPU-register operand
are different instruction selections, not different spellings. Allocation layered
on top cannot turn one into the other by address computation. Hence allocation
must come first, structurally.

Under this stage, all translator call sites emit e.g.
`add${s} d0, ${operand(dst)}` without knowing where the value resides.

Reserved/scratch registers (never allocated): D0, D1, A0, A1, FP0, FP1 (scratch);
D7, A5, A6 (reserved). Allocatable pool: D2-D6, A2-A4, FP2-FP7.

## Core change

Replace every use of `regAddr(reg)` / `floatRegFileAddr(reg)` in *operand
position* with a single indirection on `AsmGen` taking the typed operand (no
`Int` vreg numbers; `IntReg` and `FloatReg` are distinct `VirtualRegister`s):

    fun operand(op: RegisterOperand): String         // "d3" if allocated, "p8_regfile+N" if spilled
    fun storeOperand(op: RegisterOperand): String    // same, for destinations

`fpOperand()` is subsumed: a `FloatReg` operand routes to `fpN` /
`p8_fregfile+N` via the same lookup. Memory operands already go through the
centralized `resolveMemory(MemoryReference)`; only the vreg side needs this
indirection. All translator files are affected: `InstrArithmetic`,
`InstrBitwise`, `InstrControl`, `InstrLoadStore`, `InstrBranch`,
`InstrSyscall`.

The vreg side of `LOADHR`/`STOREHR` also goes through `operand()` so they
degrade to register moves or no-ops under allocation (§2.8 of the design):
- `STOREHR rX, slot=D0` where rX allocated to d0 -> elided (no-op).
- `STOREHR rX, slot=D0` where rX allocated to d3 -> `move.l d3, d0`.
- `LOADHR rX, slot=D0` where rX allocated to d0 -> elided.

`CALL` boundaries are modeled through `CallSite.effects`
(`CallEffects.memoryEffect`/`statusEffect`) plus the `callSite` register
accesses (arguments/results), not via ad hoc per-opcode kill lists.

## Sites that keep lowering through the regfile path directly

These define the "must be spilled" set for the allocator essentially for free:
any vreg used in such a position must be memory-resident at that point.
- Address-of (`&`).
- Byte sub-access via `regAddrByte`.
- Inline-asm chunk references to `p8_regfile`.

## Indexed/indirect opcodes - deferred to Stage 1B

Most translators fit the "swap `regAddr` for `operand()`" pattern. A minority
do not, because they *select* addressing modes around hardcoded scratch
registers rather than merely naming an operand. They are split out into Stage 1B
(`ideas/m68k-regalloc-stage1b-indexed-indirect.md`) so the main Stage 1 refactor
can land without waiting for the class-constraint mechanism.

Stage 1A keeps the current `loadIndexToD0` / `loadPointerToA0` fallbacks for
`LOADX`, `STOREX`, `LOADI`, `STOREI`, and `STOREZX`. Stage 1B replaces them with
allocator-aware addressing-mode selection.

## Scratch registers - enforcement, not a reservation protocol

Because D0/D1/A0/A1/FP0/FP1 are permanently reserved pure scratch that never
holds an allocated vreg (§2.3), Stage 1 does not need an allocator-aware
acquire/release system. Work:
- Route existing helpers (`loadRegOrZeroExtendToD0`, `loadPointerToA0`,
  `loadIndexToD0`) and scattered `emitLine` references to d0/d1/a0/a1/fp0/fp1
  through named constants (`SCRATCH_D0`, etc.) - from Stage 0 item 3.
- Add a debug-time assertion that the allocator never assigns a vreg to any of
  D0/D1/A0/A1/FP0/FP1 (translator scratch) or D7/A5/A6 (reserved). (The
  allocator does not exist yet, so this assertion is a placeholder/contract
  for Stage 2; implement as a guard in `operand()` that rejects those registers
  in the map, or as a documented invariant until Stage 2 wires the map.)
- Translators must not introduce a new hardcoded scratch register outside the
  D0/D1/A0/A1/FP0/FP1 set.

## Implementation order within this stage

1. Add `operand()` / `storeOperand()` to `AsmGen` with an empty map returning
   regfile addresses (behavior-neutral by construction). Include float routing.
2. Migrate translator call sites mechanically (swap `regAddr`/`floatRegFileAddr`
   for `operand()`/`storeOperand()`), keeping `regAddr`/`regAddrByte` available
   for the direct-lowering exceptions.
   - Skip `LOADX`/`STOREX`/`LOADI`/`STOREI`/`STOREZX` operand positions for now;
     Stage 1B handles them.
3. Migrate the vreg side of `LOADHR`/`STOREHR`.
4. Route `CALL` boundaries through `CallSite.effects` rather than ad hoc kills.
5. Named scratch constants + helper routing (Stage 0 item 3, if not done).
6. Debug assertion for reserved/scratch registers.
7. Hand off to Stage 1B for indexed/indirect re-selection.

Intermediate states (partial migration) remain compilable as long as `regAddr()`
stays available as fallback; migrate translator-by-translator.

## Checkpoint (gate)

With an empty allocation map, the generated assembly must be byte-identical to
the pre-change output for the test corpus (Stage 0 item 2 harness). This is
what makes the large mechanical refactor safe to land and review on its own.

## Status (implemented) — Stage 1b

- `operand()` / `storeOperand()` added to `AsmGen` (Int and `RegisterOperand`
  overloads) plus `floatOperand()`; all consult the (empty) `regAllocation` map
  and delegate to `regAddr`/`floatRegFileAddr` when not mapped — byte-identical
  by construction.
- All `regAddr(` → `operand(` and `floatRegFileAddr(` → `floatOperand(` in the
  six translator files (`InstrArithmetic`, `InstrBitwise`, `InstrBranch`,
  `InstrControl`, `InstrLoadStore`, `InstrSyscall`); `regAddrByte` (byte
  sub-access, must-be-spilled) left untouched.
- `regAllocation` map property added (empty in Stage 1).
- `regAddr`/`floatRegFileAddr`/`regAddrByte` remain as the spill primitives.
- Debug assertion added: `operand()`/`floatOperand()` throw if the map assigns
  a vreg to a scratch or reserved register (d0/d1/a0/a1/fp0/fp1/d7/a5/a6).
- CALL boundary modeling: no ad hoc kill lists existed in the backend; CALL
  handling already consumes `CallSite.effects` (`memoryEffect`/`statusEffect`)
  via `insn.memoryEffect`/`insn.statusEffect`. Nothing further required for
  Stage 1.
- Build and tests pass (`:codeGenM68k:compileKotlin`, `:codeGenM68k:test`).

Still blocked on open decision §7.1 #2 (class-constraint mechanism):
- Indexed/indirect addressing-mode re-selection (LOADX/STOREX/LOADI/STOREI/
  STOREZX and float variants). The current code keeps using scratch registers
  (a0/d0) for these, which is the conservative fallback; no behavior change.
  This will be implemented once the class-constraint mechanism is decided.