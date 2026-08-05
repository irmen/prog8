M68k Codegen Performance Optimizations
========================================

The current M68k codegen has NO CPU register allocation. IR virtual registers
live in a memory "register file" (`p8_regfile`, in BSS). Every instruction
that touches a register does so through that memory slot, using D0-D2 and A0
as scratch and FP0-FP7 for floats.

This document lists low-hanging-fruit optimizations that do NOT require a
full register allocation pass. They are ordered roughly by impact and ease.

What is already good
---------------------

Some patterns already operate directly on the register-file memory slot and
should be left alone:

- NEG, INC, DEC (addq/subq/neg on memory), InstrArithmetic.kt
- ADD/SUB with immediate (addq for 1-8, else `add.x #imm, mem`)
- ADDIM/SUBIM against memory variables
- CMPI (`cmpi.x #imm, mem`, and `tst.x mem` for #0), InstrArithmetic.kt:329
- STOREZM / STOREIM zero via `clr.x mem`
- LOAD with immediate 0 via `clr.x mem` (InstrLoadStore.kt)
- LOADR (register copy) as a direct memory-to-memory `move.x mem, mem`
- register-to-register ALU: `move.x src,d0` + `op.x d0, mem`
- ANDI/ORI/EORI with immediate: `opi.x #imm, mem`
- register-source ANDM/ORM/XORM: `move.x reg,d0` + `op.x d0, mem`
- INV/INVM (bitwise not): `not.x mem`
- divide/multiply already take a memory operand where the instruction allows it
- LOAD with a labelSymbol as a direct `move.l #label+off, mem` (InstrLoadStore.kt)
- compare-and-branch immediates: `cmpi.x #imm, mem` / `tst.x mem` directly on
  the slot (InstrBranch.kt), matching `CMPI` in InstrArithmetic.kt:329


1. Bit ops directly on memory
-----------------------------
-

`bitTest`, `bitSet`, `bitClear`, `bitToggle` (InstrBitwise.kt:374,380,387,394)
round-trip through d0. `btst/bset/bclr/bchg` with an immediate bit number
accept a memory operand, but the operation applies to a byte at that address.

- For byte-sized register slots: emit directly, e.g. `bset #bit, mem`.
- For word/long slots: the bit is a compile-time immediate, so adjust the
  address by `bit/8` and use `bit % 8`:

      bset    #(bit % 8), p8_regfile+(off + bit/8)

  Only worth doing where the added offset complexity is justified.
- `btst` feeds a following BSTEQ/BSTNE branch, which only needs the Z flag,
  so the direct form is safe there.


2. Peephole "d0 cache" (biggest win, still no full allocation)
----------------------------------------------------------------

The scratch usage is very regular: D0-D2 data registers, A0 for addresses,
FP regs for floats. Most operations funnel through d0 as `move mem,d0`.

Add a tiny state machine to AsmGen that tracks "d0 currently holds virtual
register rX of type T". When the immediately preceding emitted instruction
left that slot in d0, skip the redundant `move mem,d0`.

Invalidation rules:
- any instruction that writes a register-file slot
- any instruction that clobbers d0 (nearly all ALU ops, calls, pushes)
- any branch target / basic-block boundary / chunk boundary
- any PUSH/POP (they may touch d0)

This removes a large fraction of the remaining register-file loads without
doing any real allocation. A second slot (d1) can be tracked similarly if
the first proves effective.

Correctness note: the M68k compares/branches depend on CCR flags, so the
cache must never skip a load that is followed by an instruction that relies
on the flags being set by that load. Since the cached loads are pure data
moves that do not set flags, only skip the load, never reorder anything.

3. Small items
---------------

- `shiftRegister` (InstrBitwise.kt:234): for `.w` size and count 1, m68k
  supports the memory form `lsl.w mem` directly (the memory path already
  does this in `memoryShiftRotate`, InstrBitwise.kt:264).
- The `.w` count-1 rotates (`rotateLeft`, `rotateRight`,
  `rotateLeftThroughCarry`, `rotateRightThroughCarry`, InstrBitwise.kt:336-372)
  can do the same: `roxl.w mem` / `roxr.w mem` directly on the slot. For the
  logical ROL/ROR the `andi #$ef, ccr` (clear X) still has to precede it,
  and only `.w` memory rotates exist.
- Byte multiply (InstrArithmetic.kt:353-360) reloads the same slot twice;
  it can be tidied to load once (minor).
- Float constants already use real FPU registers and `fmovecr` for 0.0/1.0;
  nothing to gain there.

Suggested order of implementation
-----------------------------------

1. Item 1: direct bit ops for byte slots first.
2. Item 2: the peephole d0 cache, as a separate pass, with the invalidation
   rules above.
3. Item 3: small items (shifts/rotates, byte multiply) as time permits.
