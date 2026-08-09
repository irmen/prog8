# Variable Step in For-Loops

## Goal

Allow a non-constant (variable) `step` value in `for` loops over ranges:

```prog8
ubyte stepsize = 3
for i in 0 to 10 step stepsize {
    ...
}
```

Today the step value must be a compile-time constant (enforced by
`AstChecker.visit(range)` with "range step must be a constant integer").
This restricts users to static step sizes even though the start and end
values may already be variables.

## Semantics

- The step expression is **evaluated once at the start of the loop** and
  captured in a temp location. Modifying the step variable inside the loop
  body does not affect the running loop.
- **Direction is determined at runtime by the sign of the step value**
  (consistent with the current constant behavior, where the sign of the step
  sets the direction and mismatches are compile errors). The signedness of the
  step expression is preserved for this decision. An unsigned step is always
  ascending; a signed step uses its runtime sign.
- A step whose sign "points away" from `to` (e.g. `0 to 10 step -1` at
  runtime) produces an **empty loop** (body runs zero times).
- A runtime step value of exactly `0` produces an **empty loop**.
- The loop continuation test is comparison based:
  - ascending: continue while `loopvar <= to`
  - descending: continue while `loopvar >= to`
- A step that would wrap the loop variable's fixed-width type terminates the
  loop instead of allowing the wrapped value to re-enter the range. For
  example, `ubyte 254 to 255 step 3` executes for `254` and then terminates.
- The step expression must be an integer type. Its width is converted to the
  loop variable width using sign or zero extension as appropriate, while its
  original signedness is retained for direction selection. Same-width signed
  and unsigned step values must be supported so a signed negative step can be
  used with an unsigned loop variable, as existing constant negative steps
  already allow.
- No change to `when`-statement ranges, containment checks, or range array
  initializers: those keep the constant-step requirement.

### Target / type matrix

| Codegen | byte | word | long | pointer |
|---------|------|------|------|---------|
| IR codegen (new6502, m68k amiga500+qemu68k, virtual) | yes | yes | yes | yes, unsigned target-width |
| Legacy 6502 codegen (main production codegen) | yes | yes | no, explicit error | no |

The legacy 6502 codegen already does not fully support constant `|step| > 1`
over `long` loop variables (`forOverLongsRangeStepGreaterOne`). The
implementation must therefore emit an unconditional compiler error for a
variable-step `long` loop, not `romableError`, which only reports in ROMable
mode. Pointer loop variables must either be implemented explicitly in the IR
and legacy paths or rejected with a clear diagnostic before code generation.

## Required Changes

### 1. `AstChecker` - relax the constant-step requirement

`compiler/src/prog8/compiler/astprocessing/AstChecker.kt:1829-1860`
(`visit(RangeExpression)`).

- Determine whether the range is specifically the direct iterable of a
  `ForLoop`, not merely nested somewhere inside a loop body:
  ```kotlin
  val isForIterable = range.parent is ForLoop &&
      (range.parent as ForLoop).iterable === range
  ```
- For-loop ranges: allow a non-constant step when its inferred type is
  integer. Keep the `step != 0` and direction checks
  ("ascending range requires step > 0", "descending range requires step < 0")
  only when the step is a known constant.
- Non-for-loop ranges (containment checks, array initializers): keep the
  current constant requirement. Constant `when` ranges may be expanded by
  `CodeDesugarer`, while dynamic `when` values remain and are rejected by
  `AstChecker.visit(WhenChoice)` because choice values must be constant.

`AstChecker.visit(forLoop)` (`AstChecker.kt:221-232`,
`checkUnsignedLoopDownto0`) must not silently skip the existing unsigned
wrap-safety rule. For a dynamic step, either add a runtime wrap check in every
backend or reject the unsafe combination. The preferred implementation is the
runtime check described below, while retaining the compile-time diagnostic for
known constant unsafe steps.

### 2. `TypecastsAdder` - validate/cast the step to the loopvar type

`compiler/src/prog8/compiler/astprocessing/TypecastsAdder.kt:470-665`
(`after(range)` and `adjustRangeDts`).

- Do not blindly cast the step to the loopvar type, because that loses the
  signedness needed to determine runtime direction.
- Validate that the step is integer and that its width can be converted to the
  loopvar width. Preserve whether the original step is signed or unsigned.
  Use sign extension for signed steps and zero extension for unsigned steps.
- Handle this before every existing early return in `adjustRangeDts`, including
  the fast path at lines 577-579 and the constant-bound narrowing paths at
  lines 590-615. Any replacement `RangeExpression` must contain the validated
  step representation.
- Same-width signed/unsigned steps are valid so a signed negative step can be
  used with an unsigned loop variable. Incompatible widths or non-integer
  steps receive a clear diagnostic.
- Constant steps remain literals and retain the current constant-range
  behavior.

This guarantees codegen receives a width-normalized step together with enough
type information to preserve its signedness. Codegens still need to use that
signedness when selecting direction and when extending the step.

### 3. `SimplifiedAstMaker` - stop forcing step to `PtNumber`

`compiler/src/prog8/compiler/astprocessing/SimplifiedAstMaker.kt:1185-1201`
(`transform(RangeExpression)`).

- Drop the `as PtNumber` cast:
  `range.add(transformExpression(srcRange.step))`.

The containment-check step restriction (`SimplifiedAstMaker.kt:1151`) stays;
containment remains constant-only.

### 4. Simple AST - `PtRange.step` becomes `PtExpression`

`simpleAst/src/prog8/code/ast/AstExpressions.kt:438-472`.

- `val step: PtExpression get() = children[2] as PtExpression` (was
  `PtNumber`).
- `toConstantIntegerRange()`: return `null` when the step is not a `PtNumber`.
- `asConstInteger()` is already defined on `PtExpression`
  (`AstExpressions.kt:104`), so the existing
  `range.step.asConstInteger()!!` call sites in `ForLoopsAsmGen.kt` still
  compile.
- Update `PtRange.hasSideEffects()` to include `step.hasSideEffects(target)`.
- Update `PtRange.isSimple()` to reflect the child expressions instead of
  always returning `true`. A side-effecting step must not be duplicated or
  optimized as a harmless literal.

### 5. IR codegen - new variable-step path

`codeGenIntermediate/src/prog8/codegen/intermediate/IRCodeGen.kt`.

- **Dispatch** (`translate`, lines 686-696): the constant-range path
  additionally requires `iterable.step is PtNumber`:
  ```
  from & to & step all PtNumber -> translateForInConstantRange
  step is PtNumber              -> translateForInNonConstantRange
  else                          -> translateForInVariableStepRange (new)
  ```
- **New `translateForInVariableStepRange`**: evaluate `from`, `to`, and
  `step` once, in source order, into registers
  (`expressionEval.translateExpression`). Preserve the step's signedness
  separately from its IR width. The lowering must use direct comparison
  branches and must detect directional wrap before re-entering the loop:

  ```
  evaluate from, to, step once
  if step == 0 -> endLabel
  select ascending or descending setup from the preserved step sign

  ascSetup:   if from > to -> endLabel
              loopvar = from
              jump loopLabel
  descSetup:  if to > from -> endLabel
              loopvar = from
              jump loopLabel

  loopLabel:  [body]
              calculate next = loopvar + step in a temporary register
              if next reverses direction relative to loopvar -> endLabel
              if ascending and next > to -> endLabel
              if descending and next < to -> endLabel
              loopvar = next
              jump loopLabel
  endLabel:
  ```

  - Use `BGTSR`/`BGTR` for signed/unsigned range comparisons. Do not implement
    `<=` or `>=` using `CMP` followed by `BSTPOS`: that is incorrect for
    unsigned comparisons and signed overflow.
  - Use existing register arithmetic (`ADDR` or equivalent) to calculate the
    next value before storing it, or save the old loop value when using
    `ADDM`. The implementation must be able to compare old and next values to
    detect wrap in the progression direction.
  - The step sign is tested once before the loop and a direction flag or
    selected tail label is retained for the loop. The step expression itself
    must not be re-evaluated each iteration.
  - Keep `to`, `step`, and the arithmetic temporaries live across the loop;
    register allocation and packing must be checked for all target register
    limits.

The VM already implements the required register arithmetic, comparisons, and
branches. No new VM opcode should be needed, but VM tests must cover the
fixed-width wrap checks and signed/unsigned step semantics. The existing
`ADDM`/`ADDR` and comparison implementations must be verified for `POINTER`
and `LONG` values before claiming those types are supported.

### 6. Legacy 6502 codegen - new variable-step path

`codeGenCpu6502/src/prog8/codegen/cpu6502/ForLoopsAsmGen.kt`.

- **`translateForOverNonconstRange`** (lines 35-50): guard the
  `range.step.asConstInteger()!!` usage (the `< -1` unsigned-wrap check only
  applies to constant steps); route a non-`PtNumber` step to a new
  `translateForOverNonconstStepRange`.
- **New byte/word variable-step assembly**:
  - evaluate `from`, `to`, and `step` once in source order into reused temp
    variables (`createTempVarReused`), preserving the step's signedness
  - runtime sign handling must treat unsigned step values as ascending and
    signed step values according to their runtime sign; do not use `bmi` for
    an unsigned step merely because its high bit is set
  - ascending: precheck `to < from -> end`; body; calculate the next value;
    use the captured direction to select the ascCont/descCont comparison;
    ascCont continues while `loopvar <= to`, descCont while
    `loopvar >= to`
  - calculate the next loop value in registers or a temporary before storing
    it, and terminate when it reverses direction due to byte/word wrap
  - signed byte/word variants reuse the existing signed-comparison patterns
    (`eor #$80` trick, `precheckFromToWord` style helpers), but use carry/overflow
    aware comparisons rather than a raw sign-bit test for ordering
  - **romable-safe**: uses temp variables, no self-modifying code (an
    improvement over the existing `|step| >= 2` paths which use
    self-modifying code)
  - `long` loopvar: report an unconditional compiler error that variable-step
    long ranges are not implemented in the legacy backend
- The constant-range and constant-step paths are untouched; the existing
  `asConstInteger()!!` sites (lines 36, 53, 68, 169, 203, 303, 362, 411, 484,
  535) are only reachable for constant steps after the dispatch change.

## Code Generation Considerations

- new6502 (`-newcodegen`) and both m68k targets (amiga500, qemu68k) share the
  IR path, so the variable-step lowering arrives for free once the IR codegen
  is done. Verify the generated assembly by hand at least once per target.
- The compiler must preserve one documented evaluation order for `from`, `to`,
  and `step` across all backends. This plan uses source order (`from`, then
  `to`, then `step`) and evaluates each exactly once before the first body
  execution. Add side-effecting-expression tests to enforce this.
- `when` ranges are not all desugared: constant ranges may be expanded by
  `CodeDesugarer`, but dynamic range values remain and are rejected by
  `AstChecker.visit(WhenChoice)` because choice values must be constant. The
  direct-iterable check in `AstChecker` must preserve that rejection.
- The existing optimizers are already tolerant of non-constant steps:
  - `AstPreprocessor` (`AstPreprocessor.kt:128-136`) only const-folds steps
    that have a constant value.
  - `ConstantIdentifierReplacer` (`ConstantIdentifierReplacer.kt:166-194`)
    already accepts non-constant integer steps (type check only).
  - `ConstantFoldingOptimizer` (`ConstantFoldingOptimizer.kt:477-545`)
    passes non-literal steps through (`adjustRangeDt`).
  - `StatementOptimizer` (`StatementOptimizer.kt:184-216`) uses
    `range.size()`, which returns null for non-constant steps, so no
    loop-removal / repeat-conversion fires. No changes needed.

## Test Gates

The **virtual target must work flawlessly before any other codegen target is
touched**. After every big code change, run the gate program below.

### Gate program

Create a real, self-checking program at
`compiler/test/vm/forstep_gate.p8` before implementing the codegen. It must
contain executable loops, expected counts or accumulated sums, PASS/FAIL
output, and `sys.poweroff_system()` so it can run unattended. Do not use a
comment-only placeholder in the plan or test suite.

Use `@shared` on step variables so the optimizer cannot const-fold them away.
The program must cover:

- ascending signed and unsigned byte loops;
- descending loops using a signed negative variable step with an unsigned
  loop variable, if the agreed type rules support that form;
- signed and unsigned word loops;
- long and pointer loops for the IR targets if supported;
- runtime step zero and wrong-direction steps, both empty;
- `from == to`, which executes once for a non-zero compatible step;
- changing the source step variable inside the body, which must not change the
  captured step;
- non-constant `from`, `to`, and `step` expressions;
- boundary cases that would wrap, proving that the loop terminates rather than
  re-entering after fixed-width overflow;
- side effects in `from`, `to`, and `step`, proving source-order single
  evaluation;
- nested loops and `break`/`continue` interaction.

Long and pointer cases that are not supported by the legacy 6502 backend must
be in a separate IR gate or be conditionally excluded from that backend's
program.

### Gate commands

1. **Compiler AST**: `prog8c -target virtual -check compiler/test/vm/forstep_gate.p8`
   and `prog8c -target virtual -printast1 compiler/test/vm/forstep_gate.p8`.
   Confirm that the variable step remains an expression and that no constant
   direction error is emitted.
2. **Simple AST**: `prog8c -target virtual -printast2 compiler/test/vm/forstep_gate.p8`.
   Confirm that `PtRange.step` is a `PtExpression`, side effects are retained,
   and no literal-only cast occurs.
3. **IR + execution (THE GATE)**:
   `prog8c -target virtual -emu compiler/test/vm/forstep_gate.p8` must produce
   only PASS results. Repeat with `-noopt -emu`.
4. **IR file execution**: compile to a dedicated output directory and run the
   resulting `.p8ir` with `prog8c -vm`. Inspect the IR to confirm the step is
   evaluated once and that the loop uses direct comparisons and wrap guards.
   For failures use `-compareir` and `-vmtrace` (see AGENTS.md).
5. Only when virtual is flawless, move to the per-target gates:
   - new6502: `prog8c -target cx16 -newcodegen compiler/test/vm/forstep_gate.p8`
     plus assembly assertions or simulator execution.
   - m68k: compile for both `qemu68k` and `amiga500`, inspect the generated
     assembly, and add backend-specific assembly assertions. Runtime execution
     is optional only where no emulator harness exists.
   - legacy 6502: compile the byte/word gate for `c64`, `c128`, `pet32`, and
     `cx16`; inspect assembly for each target. Run the CX16 version with
     `x16emu -echo iso -run -prg ...` when available, and test ROMable mode
     separately.

## Tests

- `compiler/test/TestCompilerOnRanges.kt`: variable-step for-loop compiles
  without "range step must be a constant integer"; the error still fires for
  containment checks, dynamic `when` choices, and other non-for-loop ranges;
  constant step `0` still errors.
- VM functional tests (`compiler/test/vm/`): execute variable-step loops on
  the virtual target for byte/word/long, signed and unsigned loopvars, and
  pointer loopvars if supported;
  ascending and descending runtime steps; step `0`; wrong-direction step;
  `from==to`; body mutating the step variable; non-const from/to combined with
  variable step; fixed-width overflow; side-effecting operands; nested loops;
  `break`; and `continue`.
- `compiler/test/arithmetic/testforloops.p8` (manual x16emu): add byte/word
  variable-step cases, including signed negative steps and overflow guards.
- Add AST/SimpleAST assertions for `PtRange.step` type and side-effect
  metadata.
- Add IR assertions that the step expression is translated once, direct
  `BGTR`/`BGTSR` comparisons are used, and the wrap guard is present.
- Add new6502 assembly/simulation tests and m68k assembly assertions. Shared
  IR tests are not sufficient to validate backend-specific signed comparison,
  extension, and multi-byte arithmetic.
- Compile the legacy 6502 gate for `c64`, `c128`, `pet32`, and `cx16`; run the
  CX16 version under `x16emu` when available. Test ROMable mode separately.

## Verification

```bash
gradle :compiler:compileKotlin --console=plain        # quick check after Kotlin edits
gradle installdist installshadowdist --console=plain  # after compiler or embedded-library changes
prog8c -target virtual -emu compiler/test/vm/forstep_gate.p8
prog8c -target virtual -out /tmp/forstep-gate compiler/test/vm/forstep_gate.p8
prog8c -vm /tmp/forstep-gate/forstep_gate.p8ir
prog8c -target cx16 -newcodegen compiler/test/vm/forstep_gate.p8
prog8c -target cx16 compiler/test/vm/forstep_gate.p8
gradle build --console=plain                          # final gate, all targets and tests
```

The install task is not needed for changes only under `compiler/test`; use it
after compiler Kotlin, grammar, or embedded standard-library changes. Gradle
commands must be run sequentially, never in parallel.

## Documentation

Update the for-loop / range documentation:

- `docs/source/variables.rst:673` - remove "the step value must be a constant";
  document that the step may be an expression evaluated once at loop start,
  that signedness and runtime sign determine direction, that unsigned steps
  are ascending, that a step of zero or a step pointing away from `to` makes
  the loop empty, and that fixed-width wrap terminates the loop.
- `docs/source/programming.rst:610` - same update.
- Check `docs/source/todo.rst` for related entries.
