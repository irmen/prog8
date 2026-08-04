# m68k Codegen Potential Bugs

Real bugs found in `codeGenM68k` that produce wrong or non-assembling code
on m68k. They block correct m68k output regardless of the optimizer. All
verified against source by audit.

Targets: Amiga500 (M68000), Qemu68k (M68020). m68k is big-endian with
32-bit registers; pointers are 4 bytes. The backend consumes IR from
`codeGenIntermediate` and is in `codeGenM68k/src/prog8/codegen/m68k/`.

---

## Bugs

- **DIVMOD return convention is broken on m68k**:
  - The `DIVMOD`/`DIVMODR` IR instructions now write quotient to `reg1` and
    remainder to `reg2` directly (no value stack round-trip). The builtin
    `funcDivmod()` then routes results via `STOREHR` (quotient to AY slot)
    and `STOREM` (remainder to `cx16.r15` memory address).
  - **`STOREM` into `cx16.r15` is undefined on m68k** (`BuiltinFuncGen.kt:253-267`,
    `funcDivmod`). The `cx16.r15` memory symbol does not exist on m68k, so
    assembly fails with `undefined symbol <cx16.r15>` at the `STOREM`
    (`InstrLoadStore.kt:84-90`). The m68k target needs its own remainder
    home (e.g. a dedicated BSS symbol) instead of the cx16 scratch register.
  - **`STOREHR` slot mapping may not work correctly on m68k**
    (`InstrLoadStore.kt:125-130`). Slots 0-7 (A/X/Y/AX/AY/XY/FAC1/FAC2)
    map to `d0`-`d5`/`fp0`/`fp1`, but the 6502 slot semantics (A=low byte,
    Y=high byte for word values) may not translate correctly to M68k
    register conventions.
  - Repro: `examples/test.p8` (`-target amiga500`) - currently fails with
    `cx16.r15` assembler error.

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
