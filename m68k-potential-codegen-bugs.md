# m68k Codegen Potential Bugs

Real bugs found in `codeGenM68k` that produce wrong or non-assembling code
on m68k. They block correct m68k output regardless of the optimizer. All
verified against source by audit.

Targets: Amiga500 (M68000), Qemu68k (M68020). m68k is big-endian with
32-bit registers; pointers are 4 bytes. The backend consumes IR from
`codeGenIntermediate` and is in `codeGenM68k/src/prog8/codegen/m68k/`.

---

## Bugs

- **MULM / DIVM write to a register, not memory; DIV swaps operands**
  (`InstrArithmetic.kt:360-369, 388-393, 412-417, 446-453, 471-476,
  493-497`). The `target != null` branches read memory as a source but
  store the result into the virtual register, and the DIV word case
  computes `reg/target` instead of `target/reg`.
- **`eor <ea>,Dn` is invalid 68000 syntax** (`InstrBitwise.kt:203-208`,
  `xorMemory`). Only `EOR Dn,<ea>` is legal, so this is a vasm error on
  every target. Fix also drops a redundant trailing `move`.
- **68020/68010-only instructions emitted unconditionally for Amiga500
  (M68000)**: `extb.l` (`InstrArithmetic.kt`, `InstrControl.kt:354`),
  `divu.l`/`divs.l`/`divul.l`/`divsl.l` (`InstrArithmetic.kt:397-419,
  480-499, 575-644`), and `move ccr,d0`/`move d0,ccr`
  (`InstrControl.kt:141,148`). `move ccr` fails on `amiga500`.
- **Signed div/mod zero-extends the dividend** (`InstrArithmetic.kt:459-477`
  DIV word, `534-551` MOD word, `606-613` DIVMOD word) via `moveq #0,d0`
  before `divs.w` -> wrong for negative values.
- **DIVMOD does not push on the stack** (`InstrArithmetic.kt:575-644`):
  quotient/remainder are written to regfile slots instead of pushed, so the
  IR's two following `POP`s read the wrong stack entries.
- **Wrong shift-count register type** (`codeGenIntermediate/.../IRCodeGen.kt:1105-1106,
  1223-1224, 1240-1241`): uses `dt` (WORD) for an `LSLNM` operand that
  `IRInstructions.kt` mandates be BYTE -> `register given multiple types`
  crash with `-noopt`.

---

## Notes

- These are distinct from the optimizer-and-gating issues tracked in
  `m68k-optimizations-review.md`. Fix these first: the backend must
  assemble and produce correct results before optimizer/gating work on
  m68k output can be validated.
- M68000 vs M68020 split: Amiga500 is pure M68000 (no `extb.l`, no 32-bit
  `divs.l`/`divu.l`, no `move ccr`); Qemu68k is M68020 and supports them.
  The 68020-only instructions must be guarded by CPU type (e.g. `is68020`
  if available, or restricted to Qemu68k).
