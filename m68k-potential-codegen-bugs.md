# m68k Codegen Potential Bugs

Real bugs found in `codeGenM68k` that produce wrong or non-assembling code
on m68k. They block correct m68k output regardless of the optimizer. All
verified against source by audit.

Targets: Amiga500 (M68000), Qemu68k (M68020). m68k is big-endian with
32-bit registers; pointers are 4 bytes. The backend consumes IR from
`codeGenIntermediate` and is in `codeGenM68k/src/prog8/codegen/m68k/`.

---

## Bugs

- **DIVMOD return convention is broken on m68k** (two distinct problems):
  - **(a) Results are not pushed onto the stack** (`InstrArithmetic.kt:578-646`,
    `emitDivModOp`). The `DIVMOD`/`DIVMODR` IR instruction must push the
    quotient then the remainder onto the value stack (the VM reference does
    `valueStack.add(division)` then `valueStack.add(remainder)`), so the two
    following `POP`s recover them. Instead `emitDivModOp` writes quotient and
    remainder into `reg1`'s regfile slots (e.g. `regAddr(dstReg)` and
    `regAddrByte(dstReg,2/4)`) and never pushes. The two `POP`s then read `sp`
    and overwrite those exact slots with stack garbage, so `q`/`r` end up wrong.
    Fix: after computing `d0`=quotient, `d1`=remainder, emit
    `move.s d0,-(sp)` then `move.s d1,-(sp)` (matching type width) and delete
    the regfile writes.
  - **(b) The builtin's return locations are cx16-specific**
    (`BuiltinFuncGen.kt:230-268`, `funcDivmod`). After the `POP`s it returns
    the quotient via `STOREHR` into the 6502 hardware register `AY` (slot `s4`
    for word, `s0`/`A` for byte) and the remainder via `STOREM` into the cx16
    memory symbol **`cx16.r15`**. Neither exists on m68k:
    - `m68kSlotRegister` (`InstrControl.kt:632`) originally handled only slots
      10-32 (`D0-D7`/`A0-A6`/`FP0-FP7`) and crashed with
      `unknown calling convention slot: s4` on the `STOREHR`. *Fixed:* slots
      0-7 (`A`/`X`/`Y`/`AX`/`AY`/`XY`/`FAC1`/`FAC2`) now map to
      `d0`-`d5`/`fp0`/`fp1`.
    - `cx16.r15` is still undefined on m68k, so assembly fails with
      `undefined symbol <cx16.r15>` at the `STOREM` (`InstrLoadStore.kt:125-130`).
      The m68k target needs its own remainder home (e.g. a dedicated BSS
      symbol) instead of the cx16 scratch register.
  - Repro: `examples/test.p8` (`-target amiga500`) - currently reaches the
    `cx16.r15` assembler error; once that is resolved the push bug (a) will
    show up as wrong printed quotient/remainder.

---

## Notes

- These are distinct from the optimizer-and-gating issues tracked in
  `m68k-optimizations-review.md`. Fix these first: the backend must
  assemble and produce correct results before optimizer/gating work on
  m68k output can be validated.
- M68000 vs M68020 split: Amiga500 is pure M68000 (no 32-bit
  `divs.l`/`divu.l`, no `move ccr`); Qemu68k is M68020 and supports them.
  The 68020-only instructions must be guarded by CPU type (e.g. `is68020`
  if available, or restricted to Qemu68k).
