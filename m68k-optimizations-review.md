# AST Optimizer Review for IR / m68k Codegens

Analysis of `codeOptimizers/src/prog8/optimizer/StatementOptimizer.kt` and
`ExpressionSimplifier.kt`. The question: which of these AST-level rewrites
were designed with the 6502 codegen's cost model in mind, and how do they
affect the new IR (virtual) and m68k (Amiga500 / Qemu68k) targets?

Both optimizer passes run in `optimizeAst()` (see
`compiler/src/prog8/compiler/Compiler.kt`) for *every* compilation target
unless a rewrite explicitly checks `compTarget.cpu`. The resulting AST is
then handed to `codeGenIntermediate` (IR) and (for m68k) on to
`codeGenM68k`. So a 6502-specific rewrite that was never gated can either
emit useless IR or, worse, emit *wrong* code on m68k.

## Context: how the m68k codegen sees these constructs

- The m68k backend (`codeGenM68k`) consumes the IR produced by
  `codeGenIntermediate` (`BuiltinFuncGen.kt`). The IR has dedicated
  opcodes for `lsb` / `msb` / `msw` / `lsw` / `mkword`
  (`LSIGB`, `MSIGB`, `LSIGW`, `MSIGW`, `CONCAT`, `EXT`).
- The m68k codegen itself has *no* concept of `lsb()`/`msb()` etc. as
  language builtins. It just lowers the byte-extraction opcodes.
- m68k targets are big-endian and have `addq #n` (1-8), `lsl #8`,
  `cmp.w #imm`, and a 32-bit register file. None of these cost what
  they cost on 6502.

The encoding used below:

- **Gated** = already checks the target CPU.
- **NEEDS GATING** = would harm m68k and is not gated.
- **WRONG on m68k** = semantic break (big-endian / address-of-memory
  assumptions).
- **Neutral** = harmless on m68k, just useless extra IR.

The reference pattern for gating is the one already used at
`StatementOptimizer.kt:71`: `options.compTarget.cpu.is6502`
(the `is6502` property lives on the `CpuType` enum in
`codeCore/src/prog8/code/core/ICompilationTarget.kt`).

> **Note:** any optimization not listed in this document is considered
> **fine** for the IR and m68k codegens (target-neutral, or
> already correctly gated, or beneficial on m68k too).

## How to gate an optimization for m68k

The optimizer passes run for every target, so a 6502-only rewrite must
explicitly skip the non-6502 targets. The mechanism:

- Check `options.compTarget.cpu`. The 6502-family targets are
  `CpuType.CPU6502` and `CpuType.CPU65C02`. The m68k targets are
  `CpuType.M68000` (Amiga500) and `CpuType.M68020` (Qemu68k). The
  `VIRTUAL` target is also unaffected by these rewrites.
- The cleanest form (used as the reference at
  `StatementOptimizer.kt:71`) is a guard around the rewrite:

  ```kotlin
  if (options.compTarget.cpu.is6502) {
      // 6502-only rewrite
  }
  ```

  The `is6502` property already exists on the `CpuType` enum in
  `codeCore/src/prog8/code/core/ICompilationTarget.kt`, so no local
  helper is needed.
- For rewrites that assume the 6502 memory layout (little-endian,
  bank-byte at a fixed offset), gating to 6502 is mandatory. These are
  not just "suboptimal" on m68k - they are *wrong* there because m68k
  is big-endian.
- When in doubt, prefer leaving the original expression intact for
  m68k and let `codeGenIntermediate` / `codeGenM68k` lower it with the
  native m68k instructions (which are often already single-instruction
  forms like `addq`, `lsl`, `tst`, `cmp`).

---

## `ExpressionSimplifier.kt`

### `ifElse` — `WORD & $xx00` → `msb(WORD) & $xx` (lines 66-87)
Converts a 16-bit AND with a high-byte mask into a byte-level `msb()`
call, and `WORD & $00ff` → `lsb(WORD) & $ff`.

**NEEDS GATING.** On 6502 the high byte of a 16-bit value is already
isolated in a register, so the byte-level rewrite is genuinely cheaper.
On m68k a `and.w #imm` is one instruction while a `msb()` call lowers to
`MSIGB` + masking — strictly more work, never less. Restrict to
6502/65C02.

### `BinaryExpression` — comparison vs 0/1 rewrites (lines 194-237)
- `x >= 1` → `x > 0` (line 194)
- `X <= -1` → `X < 0` (line 201)
- `unsigned >= 0` → `true` (line 212)
- `unsigned > 0` → `!= 0` (line 216)
- `x < 1` → `x <= 0` (line 222)
- `unsigned < 0` → `false` (line 229)
- `unsigned <= 0` → `== 0` (line 233)

**Neutral.** The `unsigned >= 0 → true`, `unsigned < 0 → false`
folds are always good. The `>= 1` → `> 0`, `< 1` → `<= 0` and the
signed `> -1` / `<= -1` rewrites are 6502-motivated (compare against 0
is free with the Z flag), but on m68k they are equivalent or slightly
better (`tst` vs `cmp #imm`) and never harmful. Leave as-is.

### `BinaryExpression` — `(WORD & $xx00) == y` → `msb()` (lines 543-568)

**NEEDS GATING.** Same rationale as the `ifElse` rewrite at lines 66-87.
Restrict to 6502/65C02.

### `BinaryExpression` — `uword` vs $ff/$ff00 boundary compares (lines 582-632)
Rewrites:
- `uword > 255` → `msb(value) != 0`
- `uword >= 256` → `msb(value) != 0`
- `uword >= $xx00` → `msb(value) >= xx`
- `uword < 256` → `msb(value) == 0`
- `uword <= 255` → `msb(value) == 0`
- `uword <= $xxFF` → `msb(value) <= xx`

**NEEDS GATING.** On 6502 a 16-bit compare against $ff/$ff00 reduces to
a single 8-bit compare of `msb()`. On m68k `cmp.w #256` is one
instruction; the rewrite to `msb()` lowers to `MSIGB` + compare, which
is *more* IR, never less. Restrict to 6502/65C02.

### `optimizeShiftLeft` — `word << 8` → `mkword(lsb(x), 0)` (lines 1090-1119)
On 6502 a word left-shift by 8 is "just" moving the low byte to the
high byte and zeroing the low byte, which is free register allocation.
On m68k `lsl.w #8` is one instruction, while `mkword(lsb(x), 0)`
lowers to `LSIGB` + zero-extend + `CONCAT` — more IR, never less.

**NEEDS GATING.** Restrict to 6502/65C02.

### `optimizeShiftRight` — `word >> 8` → `msb(x)` (lines 1151-1183)
On m68k `lsr.w #8` / `asr.w #8` is one instruction.

**NEEDS GATING.** Same rationale as `optimizeShiftLeft`. Restrict to
6502/65C02.

---

## Summary table

| File:line | Rewrite | Status |
|---|---|---|
| **`ExpressionSimplifier.kt:66-87`** | `WORD & $xx00` → `msb(WORD) & $xx` | **NEEDS GATING** |
| **`ExpressionSimplifier.kt:543-568`** | `(WORD & $xx00) == y` → msb | **NEEDS GATING** |
| **`ExpressionSimplifier.kt:582-632`** | `uword` vs 255/256/`$xx00` → msb | **NEEDS GATING** |
| **`ExpressionSimplifier.kt:1090-1119`** | `word << 8` → `mkword(lsb(x), 0)` | **NEEDS GATING** |
| **`ExpressionSimplifier.kt:1151-1183`** | `word >> 8` → `msb(x)` | **NEEDS GATING** |

---

## Recommended changes

1. Gate each of the entries marked **NEEDS GATING** with
   `if (options.compTarget.cpu.is6502)` (the `is6502` property already
   exists on `CpuType` in `codeCore/src/prog8/code/core/ICompilationTarget.kt`).
 2. Leave the boundary compares vs 0/1 (lines 194-237) alone — they
    are mildly 6502-flavored but never harmful.
