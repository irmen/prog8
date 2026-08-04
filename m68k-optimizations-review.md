# m68k Optimizations & Correctness Review

Audit of the Prog8 compiler for the m68k targets (Amiga500 = M68000,
Qemu68k = M68020). m68k is big-endian, has 32-bit registers, and native
`addq`/`subq`/`tst`/`cmp`/`lsl`/`lsr`/`swap`/`scc`/`dbra` instructions.
Pointers are 4 bytes (`POINTER_MEM_SIZE = 4`).

Two optimizer layers run for every target:
- The compiler-AST optimizer: `codeOptimizers/src/prog8/optimizer/`
  (`StatementOptimizer.kt`, `ExpressionSimplifier.kt`, `Inliner.kt`,
  `ConstantFoldingOptimizer.kt`, `ConstantIdentifierReplacer.kt`,
  `ConstExprEvaluator.kt`, `UnusedCodeRemover.kt`).
- The simplified-AST optimizer: `simpleAst/src/prog8/code/optimize/`
  (`ExpressionOptimizers.kt`, `ComparisonOptimizers.kt`,
  `BooleanOptimizers.kt`, `MemoryOptimizers.kt`, `VariableOptimizers.kt`,
  `ControlFlowOptimizers.kt`).

The IR backend (`codeGenIntermediate`) and the m68k backend
(`codeGenM68k`) then lower the result.

Gating reference: `options.compTarget.cpu.is6502` (6502/65C02 only) or
`cpu.isBigEndian` (m68k only). The `CpuType` enum lives in
`codeCore/src/prog8/code/core/ICompilationTarget.kt`.

---

## 1. codeOptimizers rewrites that are 6502-flavored / wrong on m68k

- **`ExpressionSimplifier.kt:249,258,275,284`** pointer `==`/`!=` 0/1
  retyped to `UWORD` -> a 16-bit compare of a 32-bit pointer (`ptr == 0`
  true for `$00010000`). Should widen to `LONG` when `POINTER_MEM_SIZE > 2`.
- **`Inliner.kt:434`** explicit "prevent code bloat on 6502" restriction
  (`isSimpleReturnExpression` only, plus `parameters.size <= 1` at `:142`,
  `:253`) is ungated and over-conservative for m68k where calls/registers
  are cheaper. `Inliner.kt:264` gates by `name != VMTarget.NAME` (so m68k
  gets the 6502 label-collision rule) instead of by backend.
- **`StatementOptimizer.kt:574-593`** `when` -> `on..goto` jump table uses
  a 6502 break-even threshold (6 cases, byte condition) and builds a UWORD
  label array; the element-size scaling should be verified on a 32-bit
  target (`AsmGen.kt:499-501` widens symbol-bearing uword arrays to 4 bytes).
- **`ExpressionSimplifier.kt:752-764,797-809`** long byte extraction ->
  `@(&var + offset)` is endianness-correct but forces the variable to memory
  (defeats register allocation on m68k) and hardcodes a UWORD offset literal.
- **`ExpressionSimplifier.kt:766-771,811-816`** `lsb(cx16.rN)` ->
  `cx16.rNL` / `msb` -> `cx16.rNH` matches purely on the `cx16` name with a
  hardcoded little-endian layout. Harmless today (no `cx16` block for m68k)
  but a latent trap on big-endian.

---

## 3. New m68k codegen optimization opportunities

Lowering improvements that would shrink m68k output (no correctness risk):

- **`cmpi #0` -> `tst`** peephole in `cmpBranchSignedImm`/
  `cmpBranchUnsignedImm` (`InstrBranch.kt:70-102`). The `Opcode.CMPI`
  already has this peephole (`InstrArithmetic.kt:324-328`) but the branch
  paths do not apply it; doing so turns the four neutral boundary-compare
  rows into 1-instruction wins.
- **Constant fast path for `operatorGreaterThan`/`operatorLessThan`**
  (`ExpressionGen`): `operatorEquals` already has one, but `>`/`<` always
  load `#0` and do a register-register branch, so `b = x > 0` (~9 instr)
  vs `b = x != 0` (~5 instr). Saves ~4 instructions in value context.
- **`lsl`/`lsr` with immediate count** instead of materializing the count
  into a register (`LSLN`/`LSRN` emit a variable shift). Also
  **`x << 16` / `x >> 16` -> `swap`** (the backend already knows this trick
  in `MSIGW`).
- Generic: redundant moves into/out of the `p8_regfile` register file (e.g.
  `move.w reg,d0` + `move.w d0,regfile`) and load-then-immediate-compare
  sequences are candidates for a m68k-specific peephole pass.

---

## Summary table

| # | File:line | Issue | m68k impact | Gated? |
|---|---|---|---|---|
| 1 | `ExpressionSimplizer.kt:249,258,275,284` | ptr `==`/`!=` 0/1 -> `UWORD` | 16-bit compare of 32-bit ptr | No |
| 2 | `Inliner.kt:434` (+`:142`,`:253`,`:264`) | 6502 code-bloat heuristics | over-conservative | No |

---

## Recommended order of work

  1. Land the m68k lowering improvements in section 3.
  2. Revisit the remaining 6502-cost-model items (Inliner, `when`->on..goto)
     for m68k benefit once the above is stable.
