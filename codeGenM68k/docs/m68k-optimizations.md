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
- bit ops (BITTST/BITSET/BITCLR/BITTOG) directly on the register-file slot:
  `bset #(bit % 8), mem + (size-1-bit/8)` (big-endian, so the target bit lives
  in byte `size-1-bit/8` at position `bit % 8`), a single instruction with no
  d0 round-trip (InstrBitwise.kt)


1. Peephole "d0 cache" (biggest win, still no full allocation)
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


2. Memory-form shifts and rotates for .w count=1
--------------------------------------------------

On the 68000, the shift/rotate instructions (ASL/ASR/LSL/LSR/ROL/ROR/ROXL/ROXR)
have a memory form, but only for **`.w` size and count=1**. The EA is restricted
to memory alterable (`(An)`, `d16(An)`, `abs.W`, `abs.L`, ...). For the regfile
the slot address is a static symbol+offset, so the absolute form is strictly
best: no scratch register needed.

The relevant code paths currently always d0-round-trip, even for `.w` count=1,
where the memory form would collapse 3 instructions into 1:

- `shiftRegister` (InstrBitwise.kt:234): handles LSL/LSR/ASL/ASR on a register
  slot. Currently emits `move.x mem,d0; op.x #1,d0; move.x d0,mem` for every
  count 1..8. The memory path `memoryShiftRotate` (InstrBitwise.kt:264) already
  emits the direct `op.w mem` form when the target is an explicit memory
  operand; `shiftRegister` should do the same for `.w` count=1.
- The rotates (`rotateLeft`, `rotateRight`, `rotateLeftThroughCarry`,
  `rotateRightThroughCarry`, InstrBitwise.kt:336-372) currently always
  d0-round-trip too. For `.w` count=1 they can emit `roxl.w mem` / `roxr.w mem`
  directly on the slot. For the logical ROL/ROR (not through carry) the
  `andi #$ef, ccr` (clear X) still has to precede the memory rotate; the
  through-carry forms have no extra setup.
- No `.b` or `.l` memory shifts/rotates exist on the 68000, so those sizes
  and counts 2-8 (and variable counts) must keep the d0 round-trip. Going via
  `(A0)` instead of `D0` (`lea mem,a0; op.x (a0)`) would be the same 2
  instructions and is no improvement.

This is a localized, easy fix: in `shiftRegister` and the four rotate
functions, when `.w` and count==1, emit the memory form directly. The existing
`memoryShiftRotate` already contains the right encoding logic (modulo the
`andi #$ef, ccr` for logical ROL/ROR), so this is mostly routing the
register-target path through the same helper.


3. Small items
---------------

- Byte multiply (InstrArithmetic.kt:353-360) reloads the same slot twice;
  it can be tidied to load once (minor).
- Float constants already use real FPU registers and `fmovecr` for 0.0/1.0;
  nothing to gain there.

Suggested order of implementation
-----------------------------------

1. Item 1: the peephole d0 cache, as a separate pass, with the invalidation
   rules above.
2. Item 2: the memory-form shifts/rotates for `.w` count=1 (route
   `shiftRegister` and the four rotates through the existing memory path;
   trivial mechanical change with measurable wins).
3. Item 3: small items (byte multiply) as time permits.
