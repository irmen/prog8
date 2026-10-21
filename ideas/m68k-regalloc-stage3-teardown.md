# M68k Register Allocation - Stage 3: Teardown of the Old Mitigation Layer

**Parent design:** `ideas/m68k-register-allocation.md`
**Prerequisite:** Stage 2 gate green (allocator correct and metrics validated).
**Status:** Complete (Oct 2026). Steps 3a to 3d are done. See "Progress" below.
**Gate:** Tests pass after churn; peepholes deleted; remaining optimizations
match against `operand()` output, not literal regfile strings.

Much of `AsmOptimizer` exists only to undo what memory-everywhere codegen
produces (spill-then-reload bounces, immediate forwarding matching literal
`p8_regfile+N` operand strings). Once Stage 2 lands, those peepholes are
subsumed.

## Background

Stage 1 made instruction selection location-agnostic (`operand()`/`storeOperand()`).
Stage 2 made the allocator decide the mapping. Now the old mitigation layer is
redundant: register-to-register code falls out of the translators directly, and
spill-then-reload bounces largely disappear. Keeping both allocator and peepholes
is how the first attempt became "fragmented and complicated" (§1.1).

## Work

- Delete the peepholes that the allocator subsumes. Do not adapt them; deleting
  is the point.
- Keep instruction-selection optimizations that remain meaningful:
  - Constant folding into `moveq`.
  - Addressing-mode choices for true memory operands.
  These must match against `operand()` output, not literal regfile strings.
- Expect heavy churn in `TestInstructionSelectionOptimizations.kt` (~100 tests
  asserting exact `p8_regfile` instruction sequences). Rewrite these to assert
  properties instead of full listings:
  - Allocated vregs never appear as `p8_regfile` operands.
  - Specific sequences for known-small examples.
- Remove or update `REGFILE-DEPENDENT` comments that reference literal regfile
  operand strings.

## Compiler-stability gate

- `gradle build` (full test suite) passes after the churn.
- Representative programs still compile and run correctly.
- No regression in instruction count / hunk size vs. Stage 2 (should improve or
  stay level; a large regression indicates a peephole was doing real work the
  allocator does not cover and must be investigated, not silently dropped).

## Notes on "still working compiler"

The compiler builds throughout. Output changes (peephole removal) but functional
correctness is preserved. The gate is test-suite green plus representative
program verification. This stage is intentionally last because it depends on
Stage 2 having landed and its metrics validated.

## Progress (Oct 2026)

**Step 3a - done (working tree, not yet committed).**
- Deleted the five REGFILE-DEPENDENT passes from `AsmOptimizer.kt`:
  `optimizeRedundantReload`, `optimizeBounceToGlobal`, `optimizeMsigbSpill`,
  `optimizeClampImmediate`, `optimizeFuseLoadxJumpi`, with their helpers. The
  obsolete dbra comment is removed. The register-independent passes remain:
  `optimizeJmpToNextLabel`, `optimizeTailCall`, `optimizeRedundantTst`,
  `optimizeRedundantLea`.
- Regenerated the 41 `-opt` goldens. Unoptimized goldens are unchanged.
- Removed the dead `d0IndexOperand` regex left behind by the fusion pass.

**Regression and decision (gate item "no large regression" is not met).** The
default regfile path, which is still the production path, grows. Line counts for
the full `.asm` outputs, HEAD optimizer to Stage 3a:

| path                     | programs | lines before | lines after | change |
|--------------------------|----------|--------------|-------------|--------|
| default, amiga500        | 20       | 120153       | 120390      | +237   |
| default, qemu68k         | 7        | 21876        | 21968       | +92    |

The added lines are `p8_regfile` store and reload pairs, for example
`move.w d0,p8_regfile+106` followed by `move.w p8_regfile+106,<global>`. The
largest growth is `qemu68k/textelite` (+100/-39 in its golden).

The peepholes are not needed by allocated code. Under `-m68kregalloc` the only
removed-pattern hits are in the `%option noframe` startup routine `init_system`,
which the allocator never allocates. That is 5 bounce pairs per amiga program
(10 added and 5 removed lines), and zero on qemu68k.

Decision: the default-path regression was accepted for now. It is reversed by
Stage 4, when the regfile path is removed. Until then, default-path output is
larger than HEAD.

**Step 3b - done.** The surviving `AsmOptimizer` passes contain no literal
`p8_regfile` matches (only comments). The backend has no literal `p8_regfile`
operand matching outside the `REG_FILE_LABEL` constant.

**Step 3c - done.**
- Removed the 7 LOADX/CALLI fusion tests that asserted the deleted passes.
- Rewrote the `p8_regfile`-sequence assertions in
  `TestInstructionSelectionOptimizations.kt` as shape and count checks. They
  ignore regfile offsets and use a `subroutineLines()` helper, so only one
  `p8_regfile` reference remains (an inline-asm input string).
- Added one 68000 test for `JUMPI`/`CALLI` (`Amiga500Target`, `movea.l ...,a0`
  then `jmp (a0)` / `jsr (a0)`). The existing 68020 test covers the
  memory-indirect form.

**Step 3d - done.** `gradle build` passes (Oct 2026). No `REGFILE-DEPENDENT`
references remain in the Kotlin sources. Untracked `.actual.asm` and experiment
artifacts remain in the repository and are not part of this stage.
