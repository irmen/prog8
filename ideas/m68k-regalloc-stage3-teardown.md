# M68k Register Allocation - Stage 3: Teardown of the Old Mitigation Layer

**Parent design:** `ideas/m68k-register-allocation.md`
**Prerequisite:** Stage 2 gate green (allocator correct and metrics validated).
**Status:** Decided and delegable once Stage 2 lands.
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