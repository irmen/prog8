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

## 1. simpleAst optimizer rewrites that are 6502-specific / not gated

- **`MemoryOptimizers.kt:41-72`** `msb(^^field)`/`lsb(^^field)` ->
  `@(&field + offset)`. **WRONG on big-endian**: little-endian byte offsets
  (`msb` at `+1`, `lsb` at `+0`) are used, so m68k returns the swapped
  byte. Also hardcodes `DataType.UWORD` for the `PtAddressOf`, truncating
  the 4-byte m68k pointer address to 16 bits. The compiler-AST pass got this
  right (`ExpressionSimplifier.kt:759,804` use `isBigEndian`). Gate with
  `is6502`, or make it endian-aware and use `typeForUntypedAddressOf`.
- **`ExpressionOptimizers.kt:20-52`** `w + (b<<1 as uword)` ->
  `(w+b)+b`. Guard is `options.compTarget.name != VMTarget.NAME`, so m68k
  takes it. On m68k a zero-extension is not free (`EXT` lowers to 3
  instructions), so this trades one shift for a second load + second
  extension: **+3 instructions** and one extra live IR register. Change the
  guard to `cpu.is6502`.
- **`ComparisonOptimizers.kt:199-219`** `float <op> 0` ->
  `sgn(float) <op> 0`. A 6502 MFLPT trick (avoids a 5-byte compare call).
  On m68k the ideal lowering of `f == 0.0` is a 2-instruction `ftst`/`fbcc`;
  the rewrite produces `SGN.f` (6 instr) + branch = 8, and structurally
  prevents the float-compare peephole. Gate with `is6502` (and ideally fix
  `FCOMP` lowering in `IRCodeGen.kt` to emit a direct float compare+branch).
- **`ExpressionOptimizers.kt:429`** (correctly `is6502`-gated `x+=2` ->
  `x++ x++` rewrite): shares one `PtNode` instance across two
  `PtMemoryByte` parents -> double-parent hazard / double-prefixing. A live
  6502 bug; use the gate as the model for the rest of the file.
- **`ExpressionOptimizers.kt:500-526`** operand-order swap uses a 6502
  cost table but the direction is right for the IR anyway; the real gap is a
  missing `hasSideEffects` guard (swapping can reorder I/O reads).
- **`MemoryOptimizers.kt:16-34`** `@(&x)` -> `x` for `isByteOrBool`: a
  `UBYTE` node replaced by a `BYTE`/`BOOL` node silently changes a
  zero-extend into a sign-extend for signed `BYTE`. Target-agnostic bug.

---

## 2. codeOptimizers rewrites that are 6502-flavored / wrong on m68k

- **`ConstantIdentifierReplacer.kt:417,458`** and **`ConstantFoldingOptimizer.kt:120,133`**
  force const pointers into `NumericLiteral(UWORD, ...)`. `NumericLiteral`
  requires `0..65535`, so any Amiga/Qemu68k address >64KB **crashes the
  compiler**, and below that it silently narrows a 4-byte pointer to 2
  bytes. The correct idiom (`typeForUntypedAddressOf` / `POINTER_MEM_SIZE>2 -> LONG`)
  exists elsewhere but is not used here. Also reachable via
  `AddressOf.constValue` -> `UWORD` (`compilerAst AstExpressions.kt:678,686`).
- **`StatementOptimizer.kt:40`** `pokew(&ptrvar, x)` -> `ptrvar = x`. On
  m68k a pointer variable is 4 bytes, so a 2-byte store becomes a 4-byte
  store -> changed semantics. Gate by `POINTER_MEM_SIZE == 2u`.
- **`ConstExprEvaluator.kt:363-375`** `strings.isupper`/`islower`/`isletter`
  folded with PETSCII ranges for every target. The VM/ISO stdlib
  (`compiler/res/prog8lib/virtual/strings.p8`) uses the opposite ISO mapping,
  so const-folding gives an **inverted result** today on the virtual target
  and would on m68k once the m68k stdlib adds those routines. `isdigit`,
  `isspace`, `isprint` happen to agree. Gate by encoding/target.
- **`ExpressionSimplifier.kt:998-1004`** signed `x / 2^n` -> `x >> n`.
  Prog8 integer division truncates toward zero; `>>` lowers to ASR (floors),
  so `-3 / 2` (== -1) becomes `-3 >> 1` (== -2). Wrong for negatives on
  every target, and `-noopt` vs optimized disagree. The simpleAst twin
  (`ExpressionOptimizers.kt:386`) is correctly unsigned-only.
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

## 3. Rewrites verified fine on m68k (leave alone)

- **`ComparisonOptimizers.kt:26-101`** boundary compares vs 0/1:
  neutral-to-beneficial. `unsigned >= 0`/`unsigned < 0`/`unsigned <= 0`/
  `unsigned > 0` win (1-2 instructions); the signed `>= 1`/`<= -1` etc. are
  ties. `ptr != 0` could additionally fold if `isPointer` were accepted
  (pointers are the dominant 32-bit type on m68k).
- **`ExpressionOptimizers.kt:126-148,386-410`** mul/div/mod -> shift/mask:
  beneficial on m68k (68000 has no 32-bit MUL/DIV; `mulu.w`/`divu.w` are
  ~70/~140 cycles). Signed division is correctly excluded.
- **`BooleanOptimizers.kt`**, **`ControlFlowOptimizers.kt`**: clean
  (no flag assumptions; pure CFG shape).
- **`ExpressionSimplifier.kt:332-458`** the `<<`/`>>` <-> `lsb`/`msb`/
  `lsw`/`msw` family: value-level, endianness-independent, eliminates a real
  shift on m68k too.
- **`ExpressionSimplifier.kt:527-541`** `x & bit == bit` -> `x & bit != 0`
  (maps to `btst` on m68k).
- **`UnusedCodeRemover.kt`** self/duplicate-assignment removal: correctly
  guarded by `hasSideEffects`/`isIOAddress`/`isIORead` (both m68k targets
  implement `isIOAddress`).

---

## 4. New m68k codegen optimization opportunities

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
| 1 | `MemoryOptimizers.kt:41-72` | `msb/lsb(^^field)` -> `@(&field+offset)` | **WRONG** (byte order + 16-bit ptr) | No |
| 2 | `ExpressionOptimizers.kt:25` | `w + (b<<1)` -> `(w+b)+b` | +3 instr | `!= VM` only |
| 3 | `ComparisonOptimizers.kt:199-219` | `float <op> 0` -> `sgn(...)` | 8 vs 2 instr | No |
| 4 | `ConstantIdentifierReplacer.kt:417,458` | const ptr -> `UWORD` literal | **crash** / truncation | No |
| 5 | `ConstantFoldingOptimizer.kt:120,133` | folded `ptr±N` -> `UWORD` | **crash** / truncation | No |
| 6 | `StatementOptimizer.kt:40` | `pokew(&ptrvar,x)` -> `ptrvar=x` | 2-byte -> 4-byte store | No |
| 7 | `ConstExprEvaluator.kt:363-375` | `strings.isupper/islower/isletter` PETSCII | **inverted result** | No |
| 8 | `ExpressionSimplifier.kt:998-1004` | signed `x / 2^n` -> `x >> n` | wrong rounding | No |
| 9 | `ExpressionSimplifier.kt:249,258,275,284` | ptr `==`/`!=` 0/1 -> `UWORD` | 16-bit compare of 32-bit ptr | No |
| 10 | `Inliner.kt:434` (+`:142`,`:253`,`:264`) | 6502 code-bloat heuristics | over-conservative | No |

---

## Recommended order of work

1. Gate the simpleAst rewrites in section 1 (`MemoryOptimizers.kt` is the
   dangerous one; `ExpressionOptimizers.kt:25` and `ComparisonOptimizers.kt:199`
   are cheap wins matching the existing `is6502` pattern).
2. Fix the const-pointer / POINTER_MEM_SIZE issues in section 2 (crasher on
   real Amiga addresses) and the `pokew`/`isupper`/`signed-div` correctness
   items.
3. Land the m68k lowering improvements in section 4.
4. Revisit the remaining 6502-cost-model items (Inliner, `when`->on..goto)
   for m68k benefit once the above is stable.
