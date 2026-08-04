# m68k Optimizations Review

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

## 3. Remaining optimization opportunities

Lowering improvements that would shrink m68k output (no correctness risk):

- **`x >> 16` (long) -> `swap` (MSIGW)**. The m68k `swap` instruction
  exchanges the high and low 16-bit halves of a 32-bit data register.
  This is only meaningful for 32-bit `long` values; for `uword`/`byte`,
  shifting by 16 always gives 0 and the IR builder constant-folds that.
  The existing `MSIGW` handler in `codeGenM68k/InstrControl.kt:208-214`
  already uses this: `move.l src,d0; swap d0; move.w d0,dst`. The
  complementary case `x << 16` for a long is `swap; clr.w d0` (swap
  to bring the low 16 bits to the high half, then zero the low half
  to drop what was the original high 16 bits) -- also 2 m68k
  instructions versus the unoptimised `move.l x,d0; lsl.l #16,d0`
  (lsl.l #16 is slow: 8+2N = 40 cycles).
- **In-place memory shifts** (LSLNM/LSRNM/ASRNM) with a constant count.
  The IR builder still emits `LOAD #N + LSLNM` for in-place shifts in
  `multiplyByConstInplace` (IRCodeGen.kt), `divideByConstInplace`
  (IRCodeGen.kt), and the in-place array `>>=`/`<<=` paths in
  AssignmentGen.kt. Note that the m68k has no `lsl #N,<memory>` form
  (shifts only target a data register), so the saving from a constant
  count comes from skipping the regfile round-trip for the count, not
  from a single memory-shift instruction. The current `LOAD + LSLNM`
  pattern lowers to 5 m68k instructions plus 1 regfile slot
  (`move #N,regfile+rN; move regfile+rN,d1; move target,d0; lsl d1,d0;
  move d0,target`); a constant-count in-place shift would lower to
  the same 3-instruction load-shift-store sequence as the register
  case (`move target,d0; lsl #N,d0; move d0,target`), saving 2 m68k
  instructions and the regfile slot. Same applies to 6502 (skip the
  loop overhead when the count is known). To realise this, either add
  `LSLIM`/`LSRIM`/`ASRIM` IR opcodes (regfile-free; backends lower
  to load-shift-store / unroll) or fold `LOAD #N + LSLNM` in the IR
  peephole (the local peephole that was tried earlier was reverted
  in favor of the new IR opcodes; the same approach would work here).
