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
- LOADR (register copy) as a direct memory-to-memory `move.x mem, mem`
- register-to-register ALU: `move.x src,d0` + `op.x d0, mem`
- divide/multiply already take a memory operand where the instruction allows it


1. Immediate logical ops: 3 instructions -> 1
----------------------------------------------

Currently `andImmediate`, `orImmediate`, `xorImmediate`
(InstrBitwise.kt:139,167,195) emit:

    move.w  p8_regfile+off, d0
    and.w   #mask, d0
    move.w  d0, p8_regfile+off

The M68k ANDI/ORI/EORI instructions accept a memory destination, so emit
a single instruction:

    andi.w  #mask, p8_regfile+off

- `.b` and `.w` work on all M68k CPUs.
- `.l` on a memory operand requires 68020+; guard with the existing
  `program.options.compTarget.cpu < CpuType.M68020` check used for long
  multiply/divide.

This applies to all three opcodes (AND, OR, XOR) and their register-file
destinations. The memory-variable variants (andMemory, orMemory, xorMemory)
can also be simplified the same way: `move.x mem,d0` + `op.x d0, mem`
becomes `opi.x #mask, mem` only for immediate ops; for register sources the
current form is already optimal.


2. NOT directly on memory
--------------------------

`invertRegister` and `invertMemory` (InstrBitwise.kt:216,223) round-trip:

    move.w  mem, d0
    not.w   d0
    move.w  d0, mem

`not.b/w/l` supports a memory destination directly:

    not.w   mem


3. Zero loads -> clr
---------------------

`LOAD` with an immediate value of 0 (InstrLoadStore.kt:28) emits
`move.l #0, mem` (10 bytes for .l). `clr.l mem` is 6 bytes and faster.
Same idea for any other spot that loads the constant 0 into a slot.

For small non-zero .l constants, `moveq #imm,d0` + `move.l d0,mem` is also
shorter/faster than `move.l #imm,mem`, but it clobbers d0, so only do this
where d0 is already dead.


4. Pointer/address loads: drop the a0 round-trip
--------------------------------------------------

`LOAD` with a labelSymbol (InstrLoadStore.kt:29-33) emits:

    lea     label+off, a0
    move.l  a0, p8_regfile+off

The address is a link-time constant, so one instruction is enough:

    move.l  #label+off, p8_regfile+off

This also frees a0 for the caller.


5. Compare-and-branch immediates: skip the load
--------------------------------------------------

`cmpBranchUnsignedImm` and `cmpBranchSignedImm` (InstrBranch.kt:70,98) emit:

    move.w  p8_regfile+off, d0
    cmpi.w  #imm, d0
    bhi     label

`cmpi.x #imm, <ea>` and `tst.x <ea>` both accept memory operands:

    cmpi.w  #imm, p8_regfile+off
    bhi     label

and for imm == 0:

    tst.w   p8_regfile+off
    bhi     label

This matches what `CMPI` in InstrArithmetic.kt:329 already does.


6. Bit ops directly on memory
------------------------------

`bitTest`, `bitSet`, `bitClear`, `bitToggle` (InstrBitwise.kt:386-411)
round-trip through d0. `btst/bset/bclr/bchg` with an immediate bit number
accept a memory operand, but the operation applies to a byte at that address.

- For byte-sized register slots: emit directly, e.g. `bset #bit, mem`.
- For word/long slots: the bit is a compile-time immediate, so adjust the
  address by `bit/8` and use `bit % 8`:

      bset    #(bit % 8), p8_regfile+(off + bit/8)

  Only worth doing where the added offset complexity is justified.
- `btst` feeds a following BSTEQ/BSTNE branch, which only needs the Z flag,
  so the direct form is safe there.


7. Peephole "d0 cache" (biggest win, still no full allocation)
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

8. Small items
---------------

- `shiftRegister` (InstrBitwise.kt:246): for `.w` size and count 1, m68k
  supports the memory form `lsl.w mem` directly (the memory path already
  does this in `memoryShiftRotate`, InstrBitwise.kt:284).
- Byte multiply (InstrArithmetic.kt:353-360) reloads the same slot twice;
  it can be tidied to load once (minor).
- Float constants already use real FPU registers and `fmovecr` for 0.0/1.0;
  nothing to gain there.

Suggested order of implementation
-----------------------------------

1. Items 1, 2, 3: pure instruction selection, no state, low risk.
2. Items 4, 5: also pure selection.
3. Item 6: direct bit ops for byte slots first.
4. Item 7: the peephole d0 cache, as a separate pass, with the invalidation
   rules above.
