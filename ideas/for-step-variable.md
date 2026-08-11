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

**Status: complete for the current scope.** Dynamic integer steps are accepted
for direct `for` ranges, while non-loop ranges retain the constant-step rule.
Constant zero and direction checks remain. Runtime wrap safety is handled by
the IR backend, and the legacy backend rejects dynamic steps clearly.

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

**Status: complete.** Dynamic integer steps are validated and normalized in
`TypecastsAdder` before code generation. The IR codegen now only needs a
remaining unsigned `uword` to 32-bit `long` zero-extension case.

`compiler/src/prog8/compiler/astprocessing/TypecastsAdder.kt:470-665`
(`after(range)` and `adjustRangeDts`).

- Do not blindly cast the step to the loopvar type, because that loses the
  signedness needed to determine runtime direction.
- Validate that the step is integer and that its width can be converted to the
  loopvar width. Preserve whether the original step is signed or unsigned.
  Normalize narrower steps to the loopvar width using sign or zero extension.
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
signedness when selecting direction. A 32-bit loopvar has no unsigned `long`
type, so an unsigned `uword` step remains `uword` and is zero-extended by IR
codegen when needed.

### 3. `SimplifiedAstMaker` - stop forcing step to `PtNumber`

**Status: complete.** Dynamic steps are preserved as expressions.

`compiler/src/prog8/compiler/astprocessing/SimplifiedAstMaker.kt:1185-1201`
(`transform(RangeExpression)`).

- Drop the `as PtNumber` cast:
  `range.add(transformExpression(srcRange.step))`.

The containment-check step restriction (`SimplifiedAstMaker.kt:1151`) stays;
containment remains constant-only.

### 4. Simple AST - `PtRange.step` becomes `PtExpression`

**Status: complete.** Dynamic steps, constant-range conversion, side-effect
tracking, and simplicity checks are supported.

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

**Status: implemented and validated.** The IR path evaluates bounds and step
once in source order, preserves direction, computes `next` in a temporary
register, and uses direct `BGTR`/`BGTSR` checks for bound crossing and wrap.
Step type normalization is handled by `TypecastsAdder`; IR only retains the
unsigned `uword` to 32-bit `long` zero-extension case and rejects unexpected
type mismatches. The virtual, qemu68k, amiga500, and cx16 `-newcodegen` gates
pass.

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

**Status: complete for byte and word ranges.** The legacy backend now lowers
dynamic byte/word steps with captured RAM temporaries, a shared loop body,
runtime direction selection, fixed-width wrap checks, and no self-modifying
code. Dynamic long ranges still emit a clean unconditional unsupported
diagnostic. `TestExecution6502`, `TestVariousCodeGen`, and the full Gradle
build pass.

`codeGenCpu6502/src/prog8/codegen/cpu6502/ForLoopsAsmGen.kt`.

- **`translateForOverNonconstRange`** (lines 35-50): guard the
  `range.step.asConstInteger()!!` usage (the `< -1` unsigned-wrap check only
  applies to constant steps); route a non-`PtNumber` step to a new
  `translateForOverNonconstStepRange`.
- **Byte/word variable-step assembly**:
  - evaluate `from`, `to`, and `step` once in source order into reused temp
    variables (`createTempVarReused`), preserving the step's signedness
  - runtime sign handling must treat unsigned step values as ascending and
    signed step values according to their runtime sign; do not use `bmi` for
    an unsigned step merely because its high bit is set
   - ascending: precheck `to < from -> end`; shared body; calculate the next
     value; use the captured step sign to select the ascending or descending
     update and comparison tail; ascending continues while `loopvar <= to`,
     descending while `loopvar >= to`
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

### Code generation quality review

Before considering the implementation complete, review the generated code for
size and performance on every supported IR target and on the legacy backend
once its implementation exists.

- Compare variable-step loops with equivalent constant-step loops for code
  size, register count, chunk count, and per-iteration instruction count.
- Confirm that `from`, `to`, and `step` are evaluated only once and that no
  redundant loads or expression evaluations remain inside the loop body.
- Review the cost of the temporary `next` value, direction selection, direct
  comparisons, and wrap guards in byte, word, long, and pointer loops.
- Check register pressure and register packing limits, especially for nested
  loops and long or pointer loop variables.
- Inspect generated 6502 and M68K assembly for unnecessary memory traffic,
  avoidable branches, and target-specific instruction opportunities.
- Record any acceptable performance or code-size tradeoffs and add focused
  assertions or regression tests for issues found during the review.

**Status: complete as an analysis-only review.** Representative `-noopt`
constant-step and variable-step fixtures were compiled for virtual, CX16
`-newcodegen`, qemu68k, amiga500, and legacy CX16/C64 paths.

| Target/path | Constant | Variable | Main increase |
|-------------|----------|----------|---------------|
| virtual IR | 327 instructions, 70 chunks, 110 registers | 434 instructions, 122 chunks, 135 registers | +107 instructions, +52 chunks, +25 registers |
| CX16 newcodegen | 271 instructions, 85 chunks, 99 registers | 378 instructions, 137 chunks, 124 registers | +107 instructions, +52 chunks, +25 registers |
| qemu68k IR | 84 instructions, 42 chunks, 19 registers | 192 instructions, 95 chunks, 44 registers | +108 instructions, +53 chunks, +25 registers |
| amiga500 IR | 111 instructions, 43 chunks, 39 registers | 219 instructions, 96 chunks, 64 registers | +108 instructions, +53 chunks, +25 registers |
| legacy C64 subset | 435-byte PRG data | 1164-byte PRG data | +729 bytes |

Findings:

- The new IR and legacy paths evaluate `from`, `to`, and `step` once. No
  repeated source expression evaluation was found.
- The shared-body legacy implementation avoids duplicate body code and keeps
  `break`/`continue` labels valid. Its extra `next` temporary and bound/wrap
  checks are necessary for fixed-width safety.
- Both IR and legacy variable-step paths still emit descending setup/update
  machinery for statically unsigned steps, even though that direction is
  unreachable. Specializing unsigned steps would reduce code size and remove
  the direction flag for those loops.
- The legacy path rechecks the captured signed step's sign on every iteration
  to select its update tail. A setup-time direction flag or specialized signed
  path could avoid that repeated load, with a code-size tradeoff.
- The IR-backed M68K output shows the expected generic register-file traffic,
  including repeated `p8_regfile` loads and byte zero-extension sequences.
  This is backend-wide overhead rather than duplicated FOR-loop expression
  evaluation.
- No register packing limits, assembler failures, or target-specific
  instruction-set violations were found. The dynamic legacy path contains no
  self-modifying code; existing constant-step paths remain unchanged.

Recommended follow-ups are unsigned-step specialization in both codegens and
possible direction-tail sharing or flag caching in the legacy backend. These
were not implemented as part of this review.

## Test Gates

The **virtual target must work flawlessly before any other codegen target is
touched**. After every big code change, run the gate program below.

### Gate program

**Status: complete.** The self-checking gate is at `forstep_gate.p8` in the
project root and covers 16 cases, including pointer, signed word, wrong
direction, side effects, `continue`, wrapping, nesting, and step mutation.

Create a real, self-checking program at
`forstep_gate.p8` before implementing the codegen. It must
contain executable loops, expected counts or accumulated sums, PASS/FAIL
output, and a portable termination call such as `sys.exit(0)` so it can run unattended. Do not use a
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

**Status: complete for the virtual, IR, and legacy byte/word target gates.**
The optimized and `-noopt` virtual runs pass, as do runtime checks on qemu68k,
amiga500, and cx16 `-newcodegen`. Legacy C64/C128/PET32 assembly checks and
CX16 simulator coverage pass. ROMable verification is targeted: existing or
necessary self-modifying constant-step paths may report the normal
`romableError`; the new dynamic byte/word path is expected to remain ROMable
because it uses temporaries instead of self-modifying code.

1. **Compiler AST**: `prog8c -target virtual -check forstep_gate.p8`
   and `prog8c -target virtual -printast1 forstep_gate.p8`.
   Confirm that the variable step remains an expression and that no constant
   direction error is emitted.
2. **Simple AST**: `prog8c -target virtual -printast2 forstep_gate.p8`.
   Confirm that `PtRange.step` is a `PtExpression`, side effects are retained,
   and no literal-only cast occurs.
3. **IR + execution (THE GATE)**:
   `prog8c -target virtual -emu forstep_gate.p8` must produce
   only PASS results. Repeat with `-noopt -emu`.
4. **IR file execution**: compile to a dedicated output directory and run the
   resulting `.p8ir` with `prog8c -vm`. Inspect the IR to confirm the step is
   evaluated once and that the loop uses direct comparisons and wrap guards.
   For failures use `-compareir` and `-vmtrace` (see AGENTS.md).
5. Only when virtual is flawless, move to the per-target gates:
   - new6502: `prog8c -target cx16 -newcodegen forstep_gate.p8`
     plus assembly assertions or simulator execution.
   - m68k: compile for both `qemu68k` and `amiga500`, inspect the generated
     assembly, and add backend-specific assembly assertions. Runtime execution
     is optional only where no emulator harness exists.
    - legacy 6502: compile `forstep_gate.p8` for `c64`, `c128`, `pet32`, and
      `cx16`; the dynamic long-loop case is commented out for this shared gate
      because long ranges remain unsupported by the legacy backend. Inspect
      assembly for each target. Run the CX16 version with
      `x16emu -echo iso -run -prg ...` when available. In ROMable mode, verify
      that the dynamic byte/word path runs and that existing self-modifying
      paths report the normal `romableError` where applicable.

## Tests

- `compiler/test/TestCompilerOnRanges.kt`: variable-step for-loop compiles
  without "range step must be a constant integer"; the error still fires for
  containment checks, dynamic `when` choices, and other non-for-loop ranges;
  constant step `0` still errors.
- VM functional tests are implemented in
  `compiler/test/vm/TestVariableStepForLoops.kt`. The 15 tests cover byte,
  word, long, and pointer loopvars; signed and unsigned steps; ascending and
  descending runtime steps; step `0`; wrong-direction steps; `from==to`; body
  mutation of the step variable; non-constant bounds; fixed-width overflow;
  side-effecting operands; nested `break`; and `continue`.
- `compiler/test/arithmetic/testforloops.p8` (manual x16emu): add byte/word
  variable-step cases, including signed negative steps and overflow guards.
- Add AST/SimpleAST assertions for `PtRange.step` type and side-effect
  metadata.
- Add IR assertions that the step expression is translated once, direct
  `BGTR`/`BGTSR` comparisons are used, and the wrap guard is present.
- New6502 legacy byte/word simulator coverage and C64/C128/PET32 assembly
  checks are implemented. The dedicated
  `compiler/test/codegeneration/TestVariableStepForLoops6502.kt` suite covers
  the supported runtime cases through `ksim65` and checks the explicit
  unsupported diagnostic for dynamic long ranges. Shared IR tests are not
  sufficient to validate backend-specific signed comparison, extension, and
  multi-byte arithmetic. ROMable-mode verification is limited to confirming
  that the new dynamic path remains ROMable and that necessary existing
  self-modifying paths use the established `romableError` diagnostic.
- Compile `forstep_gate.p8` for `c64`, `c128`, `pet32`, and `cx16`; the dynamic
  long-loop case is currently commented out for this shared legacy gate. Run
  the CX16 version under `x16emu` when available. Verify the dynamic path and
  expected `romableError` diagnostics separately in ROMable mode.
  Restore or use a separate IR gate for long-loop coverage.

## Verification

```bash
gradle :compiler:compileKotlin --console=plain        # quick check after Kotlin edits
gradle installdist installshadowdist --console=plain  # after compiler or embedded-library changes
prog8c -target virtual -emu forstep_gate.p8
prog8c -target virtual -out /tmp/forstep-gate forstep_gate.p8
prog8c -vm /tmp/forstep-gate/forstep_gate.p8ir
prog8c -target cx16 -newcodegen forstep_gate.p8
prog8c -target cx16 forstep_gate.p8
gradle build --console=plain                          # final gate, all targets and tests
```

The install task is not needed for changes only under `compiler/test`; use it
after compiler Kotlin, grammar, or embedded standard-library changes. Gradle
commands must be run sequentially, never in parallel.

## Documentation

**Status: pending.**

Update the for-loop / range documentation:

- `docs/source/variables.rst:673` - remove "the step value must be a constant";
  document that the step may be an expression evaluated once at loop start,
  that signedness and runtime sign determine direction, that unsigned steps
  are ascending, that a step of zero or a step pointing away from `to` makes
  the loop empty, and that fixed-width wrap terminates the loop.
- `docs/source/programming.rst:610` - same update.
- Check `docs/source/todo.rst` for related entries.

## Final FOR-loop Codegen Review

**Status: pending.**

As the final step, review the IR and legacy 6502 FOR-loop code generators in
general, including both constant-step and variable-step paths. This is an
analysis-only review; do not change code as part of this step unless a separate
follow-up task is created.

- Assess whether the implementations are clear, appropriately factored, and
  maintainable, or whether control flow and target-specific cases are overly
  complicated or convoluted.
- Identify duplicated logic between byte, word, long, pointer, signed, and
  unsigned loop variants, and between constant-step and variable-step paths.
- Check whether existing helpers, labels, temporary values, comparison logic,
  wrap handling, and register allocation can be simplified or shared safely.
- Review the IR and legacy 6502 implementations separately for correctness,
  target-specific constraints, generated code quality, and consistency of
  semantics.
- Record concrete findings, possible simplifications, and any recommended
  refactoring work as follow-up items, without performing those refactorings in
  this plan step.
