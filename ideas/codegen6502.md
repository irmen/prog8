# 6502 Code Generator Performance Findings

Derived from auditing the generated 6502 assembly of `benchmark-program/benchmark.asm`
(target `cx16`, so `w65c02` / 65C02 instruction set) against the source in
`benchmark-program/*.p8`, then tracing every suspect pattern back into
`codeGenCpu6502/src/prog8/codegen/cpu6502/**`.

Scope of this document is **only** the 6502 code generator. Problems that live in
the standard library, in the front end, or in another backend are noted but not
actioned here. A section of **retracted** claims is included so the same ground
does not get re-investigated.

All asm line numbers refer to `benchmark-program/benchmark.asm` as generated on
2026-09-26 (21405 lines). The `.asm` is a build artifact and is regenerated often, so
its line numbers drift between builds; the function names and the quoted assembly are
the reliable anchors, and any line number here should be re-derived before it is
trusted.

**Instruction-set baseline.** Everything in this document is costed for `.cpu 'w65c02'`
- the WDC 65C02, which is what Prog8 emits for the cx16 target. The 65C02 cycle counts
below (`stx zp` 3, `ldy zp` 3, `txa` 2, `tay` 2, `sta zp` 3, `sty zp` 3, `ldx zp` 3,
`phy` 3, `ply` 4, a not-taken branch 2) were each read off a reference rather than
recalled, and three independent sources agree on them. See the References section of the
`.agents/skills/asm6502-coder` skill for the primary source and its timing notation
(`t` = +1 if taken, `p` = +1 if page crossed, `d` = +1 in decimal mode).

**Do not carry these numbers over to c64/c128/pet32.** An earlier draft of this
document asserted two NMOS differences - that `stx zp` is 4 cycles on NMOS rather than
3, and that the 65C02 removed the page-cross penalty on a taken branch. **Neither is
supported**: the 6502 Family CPU Reference gives `stx zp` 3 cycles on both parts, and
`2+t+p` for `bne` on both. Those claims have been removed rather than replaced, because
nothing consulted here establishes a difference. Since every measurement in this
document comes from the cx16 build, the 65C02 column is the one that matters; if a
figure for another target is ever needed, read it off the reference for that specific
CPU instead of adjusting a number learned here.

An instruction outside the 65C02 set does not quietly become a no-op: 64tass rejects it
with `error: general syntax` and refuses to assemble. (This is worth stating because an
earlier draft of this document claimed the opposite - that `txy` would be silently
accepted as an undefined label and emit nothing. It is not; it is a hard build failure.)

`txy` is worth naming explicitly, because it is the natural thing to reach for when
moving X into Y and it is the reason the sequences above are two instructions. It is
**not** on the 65C02 - and an earlier draft of this document implied it was on the
65CE02, which is also wrong. Across all eight 6502-family variants, `txy` appears only on
the **65C816**, a 16-bit part that no Prog8 target uses. It is not on `6502`, `65c02`,
`r65c02`, `65c02s` or `65ce02`. (64tass has no `w65c02s` at all; its CPU selection is
`--m65xx` default, `--m65c02` and `--m65ce02`.)

**Status: analysis complete, and reviewed 2026-09-27.** A line-by-line re-check against
the current sources corrected several substantive errors; where a claim changed, the
item says so explicitly and explains why. The headline corrections:

- **Item 3 was wrong and is now closed.** `INC <memory>` does not write A, so the `lda`
  after it is required, not redundant.
- **Items 1 and 7 proposed unsound fixes.** In both, A is live across the label the
  proposed rewrite branches to, so deleting the 0/1 materialization breaks the
  consumer. Both are now marked blocked with the shape that would actually work.
- **Item 2 is not a defect and is now closed.** It was the top-ranked item. There is no
  `txy` (it is 65C816-only across the whole 6502 family), so `X -> Y` is two
  instructions - but the proposed `txa` + `tay` **clobbers A**, which is live carrying
  the low byte of the same word. The existing `stx` / `ldy` pair is the cheapest correct
  encoding: `pha`/`txa`/`tay`/`pla` is the same 4 bytes for 11 cycles against 6. The
  store/reload pair is 4 bytes, not 2, the site count was 9 rather than 13, and the
  `AX -> XY` sub-case was wrong twice: it emits nothing anywhere, and `tay` + `tax` would
  have put the *low* byte in Y.
- **Item 9's payoff was wrong twice** (1 byte -> 3, 16 sites -> 8), and `FDIVT` *does*
  take the `_NZ` suffix.
- **Item 12's size arithmetic was wrong** - the `multiply_longs` cluster is 158 bytes,
  not 214, and the c64 register claim was false. The conclusion survives; the early-exit
  alternative is roughly twice as good as stated.

Items that have since been implemented are removed rather than marked done, so if you
are looking for the constant-multiply shift-add work, the `uword * ubyte` narrowing,
the six broken `mul_byte_*`/`mul_word_*` library routines, the long constant-shift fix,
or the `x * x` range bug, those are in the git history rather than here. Benchmark
version 14.0, weighted total **9199** (see [Measuring](#measuring)).

---

## 0. Recommended implementation order

The order below is roughly by payoff-per-risk, not by dependency - the earlier
draft put item 11 first on the assumption that it was a scoring bug. It is not
(see Retracted claims), so nothing is unblocked by doing it first.

1. **Item 5** - dead `phy` / `ply` around an indexed RMW
2. **Item 4** - `uword >` operand asymmetry
3. **Items 9, 10** - float non-T entries, and signed word shifts
4. **Item 12** - `multiply_longs` early exit (the Horner chain is now marginal)
5. **Item 8** - float power-of-two, delicate, cycles only
6. **Item 1** - comparison operands of `and`/`or`; **blocked** on a codegen change, see below
7. **Item 7** - **blocked**; needs a calling-convention invariant, not a peephole
8. **Item 11** - optional `VariableAllocator` weighting refinement only; measure first

**Items 2, 3 and 6 are no longer on the list.** Items 2 and 3 never described a real
defect. Item 2's `stx <zp>` / `ldy <zp>` pair is the cheapest correct encoding, because
A is live carrying the low byte of the same word and the 6502 has no `X -> Y` move.
Item 3's `inc` / `lda` is required, not redundant, because `INC <memory>` does not write
A. Item 6 was implemented and measured at 8 bytes over 3 sites; its section has been
removed per this document's convention for finished work.

**The common cause.** Items 1, 2 and 7 all propose replacing a materialization with a
branch, on the reasoning that the value is redundant at that point. In all three it is
not: A is live across the label or instruction the rewrite branches to. Any future item
in this area should be checked against that first, and note that `optimizeStoreLoadSame`
having no cross-register arm is load-bearing, not an oversight.

**A second trap, this one verified against shipped code.** The windowing in `getLinesBy`
(`AsmOptimizer.kt:83-88`) filters only blank lines and `;` comments, so a window runs
straight through calls and labels. A rule that deletes an instruction on the reasoning
"the value is unchanged here" therefore has to *establish* that, rather than assume it,
and the conditions are: the source operand was not written in between, no call or control
transfer intervenes (a callee can clobber the register), and no label intervenes (another
path may arrive with a different value). A shipped 14-line rule checked none of the three
and dropped a load-bearing `ldy` in exactly those three shapes - it is fixed now, but the
siblings have not been looked at: `optimizeSameAssignments` (`:249-264` and `:386-402`)
and the other 14-line rules in the same file. **Audit them before trusting them.**

Two further hazards in the same area, both learned the hard way:

- The **operand equality check is load-bearing**, and is easy to conflate with the
  neighbouring *shape* check. In the rule above, `firstvalue == fourthvalue` (the two
  `ldy` load the same operand) is what makes the reload redundant; `secondvalue ==
  fifthvalue` (the `lda` and `sta` touch the same address) is only a shape restriction,
  and is the one that may be relaxed. Dropping both at once miscompiled consecutive
  statements that index *different* elements - `msb(seed[1])` then `msb(seed[0])` in
  `b_textelite.p8` - and the unit tests missed it because none of them used two
  different indices. Test the distinct-index case explicitly.
- **Unit tests alone will not catch a wrong-index store.** The miscompile above was found
  by diffing the generated `.asm` for an example before and after the change, not by the
  optimizer tests. When a peephole changes what is emitted, diff the output as well.

For contrast, the m68k backend's analogous rule, `optimizeRedundantReload`
(`codeGenM68k/src/prog8/codegen/m68k/AsmOptimizer.kt:163`), is sound: it explicitly
guards the label case (`hasLabel` on the second line), its window is only 2 lines so
there is no room for the other two hazards, and it pins both endpoints to `d0`. A tighter
pattern sidesteps the problem rather than checking for it.

| Item | State |
|---|---|
| 4, 5, 9, 10, 12 | open, fix identified |
| 1 | **blocked** - the diagnosis holds but every proposed fix is unsound as written; A is live across the shortcut label |
| 2 | **closed** - not a defect; the encoding is already optimal and the proposed rewrite clobbers A |
| 3 | **closed** - the reported defect does not exist |
| 7 | **blocked** - diagnosis holds, both proposed changes unsound or unsatisfiable |
| 8 | open, delicate (denormal/overflow guards needed) |
| 11 | refuted, see Retracted claims; two unmeasured refinements remain in its section |

## Measuring

All benchmark figures below are on benchmark **version 12.1**, which summed the raw
iteration counts of all ten sub-benchmarks. That made the two heaviest - maze solver
(~3064) and text-elite (~2240) - 57% of the total while game of life was 2%, so a
regression in a small benchmark barely registered.

The benchmark is now **version 14.0**: maze and text-elite are weighted 64 instead of
256, the other eight are unweighted, and the printed table shows the weighted scores so
the rows add up to the total. Raw per-benchmark counts are unchanged and are what the
tables in this document quote; totals are not comparable across the two versions. The
weighted total is 9199, essentially unchanged from the 9193 measured under 12.1, since
the two down-weighted benchmarks are exactly the ones that gained most from the
constant-multiply work that has since been implemented and removed from this document.

Run-to-run jitter on the raw counts is about +/-2 on the total, so only larger movements
are signal.

---

## 1. Comparison operands of `and`/`or` are forced through 0/1 boolean materialization

**Highest-value single fix.**

`cmp #n` already sets the carry flag that the branch needs. But when a
comparison is an *operand* of `and`/`or` rather than the whole condition, it is
compiled as a **value** instead:

```asm
lda  p8v_iter
cmp  #15
rol  a        ; carry -> byte
and  #1
eor  #1
beq  p8_label_gen_198_shortcut   ; byte -> branch
```
(benchmark.asm 8692-8697, from `b_mandelbrot.p8:30`)

**Where it comes from**

- `optimizedComparison`, `assignment/BinaryOpAssignmentsGen.kt:52-143` emits the
  `cmp / rol a / and #1 / eor #1` sequence for the unsigned-byte `<` fast path
  (and the same without `eor #1` for `>=`, at `:100-143`).
- The generic 0/1 path for word/signed/`==`/`!=` is
  `BinaryOpAssignmentsGen.kt:146-182`, which synthesizes a `PtIfElse` with
  `ld? #1` / `ld? #0` bodies and hands it back to the if-else generator.
- `optimizedLogicalExpr` (`BinaryOpAssignmentsGen.kt:1722-1749`) can only ask for
  a 0/1 byte in A and then `beq`/`bne` it. It has no "give me the flags and let
  me branch" facility.

**The asymmetry is directly observable in one source file.** `b_maze.p8:150`
`if cx>0 and ...` generates a direct `beq` (the AST optimizer reduced `cx>0` to
plain `cx`). `b_maze.p8:152` `if cx<numCellsHoriz-1 and ...` generates the
6-instruction form above (benchmark.asm 15028-15033). Same `if`, same block,
same conjunction.

**Direct-branch lowering already exists but is unreachable here.**
`IfElseAsmGen.kt:483-579` (`translateByteLess` and siblings) and the word
equivalents (`wordLessValue` at `:666`, `wordGreaterEqualsValue` at `:797`,
`wordEqualsValue` at `:1616`) all emit compare-and-branch directly. They are
only reached when the comparison is the *entire* `if`/`while` condition
(`IfElseAsmGen.kt:287-343`). All of them are `private`, so
`optimizedLogicalExpr` cannot reuse them.

**Second manifestation** - each `or` operand's 0/1 materialization
(benchmark.asm 10185-10192, 10202-10209, 10221-10228, from `b_queens.p8:17`):

```asm
cmp  p8v_col
bne  p8_label_gen_219_else
lda  #1
bra  p8_label_gen_218_afterif
p8_label_gen_219_else
lda  #0
p8_label_gen_218_afterif
bne  p8_label_gen_217_shortcut
```

11 cycles where `cmp / bne` is 7, three times per innermost-loop iteration.

**Change**

Real fix (codegen): add an `internal` compare-and-branch entry point on
`IfElseAsmGen`, extracted from `translateByteLess` et al. In
`optimizedLogicalExpr` (`BinaryOpAssignmentsGen.kt:1722-1749`), when an operand
is a `PtBinaryExpression` whose operator is in `ComparisonOperators`, emit the
comparison's flags plus an inverted branch to the shortcut label, instead of
`assignExpressionToRegister(..., A)` + `beq`. This recovers both the 3 wasted
instructions per operand and the register liveness of the left-hand side.

**Blocker, found on review: A is live across the shortcut label, so this cannot be
done as a plain branch substitution.** The `optimizedLogicalExpr` contract is
value-based, not flag-based: it guarantees *A == 0 iff the conjunction is false* at
`$shortcutLabel`, and the consumer there tests A. From the doc's own mandelbrot
example (benchmark.asm 8692-8715):

```asm
lda  p8v_iter
cmp  #15
rol  a
and  #1
eor  #1
beq  p8_label_gen_204_shortcut
lda  #<p8v_xsquared
...
p8_label_gen_204_shortcut
beq  p8_label_gen_52_afterwhile      ; <-- consumes A
```

Arriving by direct branch would leave A holding `p8v_iter`, which is non-zero exactly
when the comparison is true, so the `beq` would take the wrong path and the loop would
exit when it should continue. Both this fix and peephole rule 1 below need to *also*
materialise the boolean at the label - which is the very thing being removed. See the
"Rework" note under **Change** below for the shape that does work.

Making the methods `internal` is also not sufficient on its own: `ifElseAsmgen` is
itself `private` in `AsmGen.kt:92`, and `BinaryOpAssignmentsGen` only holds
`asmgen: AsmGen6502Internal`, so the instance has to be re-exposed (and the
`AsmGen -> AssignmentAsmGen -> BinaryOpAssignmentsGen -> IfElseAsmGen ->
AssignmentAsmGen` construction cycle broken with a `lateinit` or setter) as well.

`IfExpressionAsmGen.kt:229` has the same structure and the same blocker.

Cheap mitigation (peephole only, recovers bytes but not liveness): two rules in
`AsmOptimizer.kt`:

1. `cmp X / rol a / and #1 / eor #1 / <branch>` -> `<inverted bcc> <branch>`
   (and the no-`eor` variant -> `<bcc>`), only when the branch target is a
   compiler-generated label.
2. `<branch> Lelse / lda #1 / bra Lafter / Lelse: lda #0 / Lafter: <branch2>`
   -> `<inverted branch> Lafter / Lafter: <branch2>`. The existing
   `beq+jmp+label -> bne` rule at `AsmOptimizer.kt:779-800` does not match
   because it requires a `jmp` (not `bra`) at position 1 and a branch at
   position 0.

**Both rules as written are unsound, for the same A-is-live reason.** Real code at
benchmark.asm 10185-10192:

```asm
cmp  p8v_col
bne  p8_label_gen_225_else
lda  #1
bra  p8_label_gen_224_afterif
p8_label_gen_225_else
lda  #0
p8_label_gen_224_afterif
bne  p8_label_gen_223_shortcut          ; <-- consumes A
```

Rule 2's rewrite reaches that `bne` with A holding the raw `board[i]` value. When
`col == 0` and `board[i] == 0`, A is 0, the `bne` is not taken, and the second `or`
operand is evaluated instead of short-circuiting. The earlier draft of this item
offered "leaves the previously-loaded value intact in A" as a *benefit*; it is the bug.

**Change (revised)**

The sound shape is to fuse the materialization with its consumer rather than delete it.
For rule 1, note that the `beq` at `$shortcut` usually tests A and nothing else, so
`lda X / cmp #n / rol a / and #1 / eor #1 / beq $shortcut` immediately followed by
`$shortcut: beq $target` collapses to `lda X / cmp #n / <inverted bcc> $target`,
dropping 4 instructions (~9 cycles, 6 bytes) and the now-unused label. The same fusion
applies to rule 2, where `$afterif: <branch2>` is the consumer: rewrite to
`<inverted branch> $target` and delete both the materialization and the label.

This requires the peephole to look one instruction past the branch target, so the
window must span the intervening basic block, and it only applies when the target
label is compiler-generated and immediately followed by a branch on A with no
instruction in between. Both conditions are checkable; the current proposals check
neither.

---

## 2. ~~`stx <zp>` + `ldy <zp>` where a register pair transfer does~~ - **NOT A DEFECT, see below**

This was the top-ranked item in the first draft of this document. It is not a defect:
the sequence is the cheapest correct encoding available, and the proposed rewrite is
unsound because A is live. The item is retained (struck through) because it is the
clearest example of the mistake that runs through items 1, 2 and 7 alike.

```asm
        ldy  p8v_benchmark_number
        lda  p8b_main.p8v_benchmark_names_lsb,y
        ldx  p8b_main.p8v_benchmark_names_msb,y
        stx  P8ZP_SCRATCH_REG
        ldy  P8ZP_SCRATCH_REG
        jsr  txt.print
```

**9 sites in this build** (benchmark.asm 377-378, 397-398, 447-448, 5849-5850, 5868-5869,
16678-16679, 16766-16767, 16787-16788, 16864-16865), all `stx  P8ZP_SCRATCH_REG` /
`ldy  P8ZP_SCRATCH_REG` and all the `AX -> AY` branch below.

An earlier draft of this item said "13 sites", from counting every `stx
P8ZP_SCRATCH_REG` in the file. That overcounted: of the 13, three are loop bodies where
`stx` is followed by `dey` (`cx16/textio.p8:259,357,405` - `stx P8ZP_SCRATCH_REG ;
columns` / `dey`) and one is `prog8_lib.asm:153` where the `ldy` reload is separated from
the `stx` by a blank line and a different instruction. Only 9 are the store/reload pair.

There is no `txy` on this CPU, so X->Y cannot be done in one instruction. The target
assembles as `.cpu 'w65c02'` (the WDC 65C02), which does not include it, and across the
whole 6502 family the instruction exists only on the 65C816 - not on the 65C02, and not
on the 65CE02 or 65C02S either. Using it here is not a silent no-op that assembles to
nothing: 64tass fails with `error: general syntax` and produces no output.

**The obvious rewrite is unsound, because A is live.** An earlier draft of this item
proposed `txa` + `tay` as a replacement. That is wrong, and it is wrong for the same
reason items 1 and 7 are wrong. At this point the value being moved is a *word* held in
`AX`: **A carries the low byte**, which is already in the right place for the `AY` target
and is still needed by the consumer. The generator is moving only the *high* byte from X
into Y, precisely so that A survives. `txa` overwrites A with the high byte and destroys
the low half of the value.

7 of the 9 sites consume A immediately after the pair - `jsr txt.print` (377, 447),
`ora P8ZP_SCRATCH_REG` (397, 16678, 16766, 16787) or `sta P8ZP_SCRATCH_W1` (5849, 5868).
The two exceptions are 16864 (followed by `rts`) and 16787 (followed by `pha`, which does
consume A), so only one site could legally use `txa` - and a peephole cannot cheaply prove
A is dead there.

**And there is no cheaper sequence anyway.** All three candidates assemble to the same
4 bytes, verified with 64tass:

| sequence | bytes | 65C02 cycles |
|---|---|---|
| `stx zp` / `ldy zp` (current) | 4 | **6** |
| `pha` / `txa` / `tay` / `pla` | 4 | 11 |
| `stx W1+1` / `ldy W1+1` | 4 | 6 |

Routing through A costs 5 extra cycles for no size gain, and the word-scratch variant is
exactly equivalent to what the code already does. **The current emission is already
optimal**, and the apparent waste is not a codegen defect at all - it is the absence of an
X->Y move instruction on the CPU. The asymmetry with the mirror case at `:1399`
(`AY -> XY`, a single `tax`) is the tell: moving *out of Y* is free because `tax` does not
touch A, whereas moving *out of X* has to pass through A, which is occupied.

**Where it comes from**

`assignment/PrimitiveAssignmentsGen.kt:1386`, in `assignRegisterpairWord()`
(declared at `:1243`):

```kotlin
RegisterOrPair.AX -> when(target.register!!) {
    RegisterOrPair.AY -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG") }
    RegisterOrPair.AY -> { }   // (AX)
    RegisterOrPair.XY -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG |  tax") }
```

Mirrors at `:1398` (`AY -> AX`, same problem: A holds the low byte) and `:1409`
(`XY -> AX`). The `AY -> XY` mirror at `:1399` is a single `tax` and is already optimal.
The single-byte variants at `:1090` (`X -> Y`) and `:1141` (`Y -> X`) are likewise
already minimal - for a bare byte there is no second value to preserve, but `txa`/`tya`
still cost 2 instructions and 2 cycles against `stx zp`/`ldy zp` at 4 bytes and 6, so
`txa`+`tay` *would* be a small win there (2 bytes, 4 cycles) if a single-byte case ever
reached that path. That is the one part of this item with a real (unexercised) payoff.

**Why no peephole fires**

`optimizeStoreLoadSame` (`AsmOptimizer.kt:594`, rule registered at `:35`)
enumerates **only same-register** pairs at `:608-618`:

```kotlin
if ((first.startsWith("sta ") && second.startsWith("lda ")) ||
        (first.startsWith("stx ") && second.startsWith("ldx ")) ||
        (first.startsWith("sty ") && second.startsWith("ldy ")) || ...)
```

There is no `stx .../ ldy ...` arm. Note that even if one existed, the right
action here is a **rewrite to `tay`**, not deletion of the second instruction.

The only rule that emits a transfer from a store/load pair is inside
`optimizeSameAssignments` at `AsmOptimizer.kt:386-402`, which emits
`Modification(..., "  ta$reg2")`. It is hardcoded to `f1.startsWith("sta ")`
(an **A** store) and additionally requires a third `sta <same operand>`
instruction. Our shape is `stx`, and the next line is `jsr`/`ora`/
`sta P8ZP_SCRATCH_W1` - never a third `sta <same>`. Effectively dead code for
this generator.

**Note**: the compiler already knows the idiom. `optimizeUselessPushPopStack`
(`AsmOptimizer.kt:889-900`) converts `pha`+`ply` -> `tay` and `phy`+`pla` ->
`tya`. It simply has no store/load arm.

**No change. The two branches are already optimal, and the peephole idea is unsafe.**

An earlier draft proposed adding a cross-register arm to `optimizeStoreLoadSame` so
that `stX <zp>` followed by `ldY <zp>` on the same operand becomes a transfer sequence.
The byte accounting for that was fine, and it is retained below because it is how the
error was found. But the premise is wrong: **the sequence is not redundant, it is the
only encoding available**, because A is live carrying the low byte of the same word.

For the record, the sizes involved, all verified by assembling with 64tass
(`P8ZP_*` are plain zeropage constants, `ProgramAndVarsGen.kt:71`, so every pair below
is the 2-byte operand form):

| pair | bytes | vs | rewrite | bytes | apparent saving |
|---|---|---|---|---|---|
| `sta <zp>` / `ldy <zp>` | 4 | | `tay` | 1 | 3 bytes, 4 cycles |
| `stx <zp>` / `ldy <zp>` | 4 | | `txa` / `tay` | 2 | 2 bytes, 2 cycles |
| `sty <zp>` / `ldx <zp>` | 4 | | `tya` / `tax` | 2 | 2 bytes, 2 cycles |

Only the first of those is real, because only `A -> Y` has a register move that does not
disturb a value the caller still needs - and even that one does not occur here, since the
`AX -> AY` case needs `X -> Y` precisely because A is busy. A store and a reload of the
same zeropage byte is 4 bytes, not 2: each instruction is an opcode plus a one-byte
address.

**The `AX -> XY` branch at `:1388` is also already optimal**, and an earlier draft was
wrong about it twice over. It claimed `tay` + `tax` could replace
`stx scratch | ldy scratch | tax`, saving 3 bytes and 4 cycles. But the source is `AX`,
so A holds the *low* byte and X the *high* byte, while the target `XY` needs the high
byte in Y. `tay` would put the **low** byte in Y, which is simply wrong. The existing
sequence is already the correct one: `stx`/`ldy` moves the high byte into Y, and the
trailing `tax` moves the low byte from A into X. Any correct alternative has to preserve
A until the `tax`, which means `pha` / `txa` / `tay` / `pla` / `tax` - 5 instructions,
5 bytes, 13 cycles, against the current 3 instructions, 5 bytes, 8 cycles. The current
form wins on cycles at equal size.

Separately, this branch emits nothing anywhere in the repository: scanning every `.asm`
in the tree plus the standard library sources finds 0 occurrences of
`stx P8ZP_SCRATCH_REG` / `ldy P8ZP_SCRATCH_REG` / `tax`. All 9 live sites are `:1386`.

**Why no peephole fires, and why adding one would be a bug.** `optimizeStoreLoadSame`
(`AsmOptimizer.kt:594`, registered at `:35`) enumerates only same-register pairs at
`:608-618` (`sta`/`lda`, `stx`/`ldx`, `sty`/`ldy`). That omission is load-bearing here:
any cross-register arm it grew would match these 9 sites and clobber the low byte,
producing wrong results rather than faster ones. The same is true of the store/load rule
inside `optimizeSameAssignments` (`:386-402`), which emits `ta$reg2` but is hardcoded to
`f1.startsWith("sta ")` and additionally requires a third `sta <same operand>`. Note
that `optimizeUselessPushPopStack` (`:542-550`) already rewrites `pha`+`ply` -> `tay`
and `phy`+`pla` -> `tya`, which is safe precisely because a push/pop pair carries one
value and consumes nothing - the distinction this whole item turns on.

`getAddressArg()` (`AsmOptimizer.kt:700`) already resolves `P8ZP_*` symbols, which are
emitted as plain zeropage constants at `ProgramAndVarsGen.kt:71`.

---

## 3. ~~`inc <var>` immediately followed by `lda <var>`~~ - **NOT A DEFECT, see below**

This item was originally written up as the second-highest-value fix in the document.
On review it is not a defect at all, and the item is retained (struck through) rather
than deleted so the reasoning is not repeated. The branch point is
`ForLoopsAsmGen.kt:1392`:

```kotlin
asmgen.translate(stmt.statements)
if (range.last == 255) {
    asmgen.out("""
        inc  $varname
        bne  $loopLabel
$endLabel""")
} else {
    asmgen.out("""
        inc  $varname
        lda  $varname          // <-- required: inc does not write A
        cmp  #${range.last+1}
        bne  $loopLabel
$endLabel""")
}
```

- Line 1392: end-of-loop is detected by wrap-to-zero, so `inc`'s own Z flag
  suffices, because `INC` *does* set N/Z from its memory result. Correct and optimal.
- Line 1397-1404: end-of-loop is detected by comparing against
  `range.last + 1`, which needs a `cmp` operand, so `lda $varname` is
  **required** - see the correction below.

**This item was previously written up as a redundant-load bug. It is not one.**
`INC <memory>` is a read-modify-write on memory (opcode `EE` absolute, `E6` zeropage).
It sets N/Z from the memory result and leaves **A completely unchanged**; there is no
65C02 addressing mode in which it deposits into A. The accumulator form is a separate
opcode, emitted by the compiler as `inc  a`. So after `inc $varname` there is no value
in A to compare against, and the `lda` cannot be elided - at either the emission site or
in a peephole.

Note this is the same fact the first bullet relies on, and the earlier draft of this
item contradicted it by claiming `inc` left the value in A.

**Restructuring does not help either.** Both alternatives were costed:

| form | cycles (absolute) |
|---|---|
| current: `inc V` / `lda V` / `cmp #last+1` / `bne loop` | 15 |
| test-then-increment: `lda V` / `cmp #last` / `beq end` / `inc V` / `bne loop` | 18 |
| keep the counter in A: `lda V` / `cmp #last+1` / `beq end` / `inc a` / `sta V` / `jmp loop` | >= 18, and needs the initial store |

There is a real but *cycle-neutral* 2-byte win available: `inc V` / `lda V` / `cmp #k`
performs the same memory read twice, so `lda V` / `add #1` / `sta V` followed by the
`cmp` reads once and drops the `inc`. That trades a read-modify-write for a
read-modify-write plus a store and is not clearly a win; it is listed only so this is
not mistaken for an open opportunity. **Recommended action: close this item.**

**Evidence** - two `for` loops in the same file, same lowering function:

```asm
; b_textelite.p8:495  for pi in 0 to 255  -> range.last == 255 -> GOOD
inc  p8v_pi
bne  p8_label_gen_301_for_loop          ; 6 + 3 = 9 cycles/iter (p8v_pi is absolute)

; b_textelite.p8:426  for ci in 0 to len(names)-1  -> range.last == 16
inc  p8v_ci
lda  p8v_ci
cmp  #17
bne  p8_label_gen_291_for_loop          ; 5 + 3 + 2 + 3 = 13 cycles/iter (p8v_ci is zeropage)
```

The two figures are not comparable as printed in the earlier draft of this item, which
quoted 9 and 15: the `pi` loop's counter is in the zero page while the `ci` loop's is in
BSS, so they must be costed under different addressing assumptions. Corrected values are
9 and 13. (benchmark.asm 12000-12001 and 11695-11698.)

Both dispatch through `translateForSimpleByteRangeAsc` (`ForLoopsAsmGen.kt:1382`),
reached from the `translateForSimpleByteRangeAsc` selection at `:1202-1206`. The
divergence is entirely internal to that one function.

**The sibling helpers have the same shape, and the same `lda` is required there too**

| Lines | Emitted tail | `lda` |
|---|---|---|
| `:1399-1402` | `inc V` / `lda V` / `cmp #last+1` | required |
| `:1424-1426` | `dec V` / `lda V` / `cmp #255` | required |
| `:1437-1439` | `dec V` / `lda V` / `cmp #last-1` | required |
| `:1244-1247` | `inc V` / `inc V` / `lda V` / `cmp #last+2` | required (1st `inc` folds into the 2nd) |
| `:1267-1270` | `dec V` / `dec V` / `lda V` / `cmp #last-2` | required (1st `dec` folds into the 2nd) |

The tails that consume `inc`/`dec` flags directly - and so need no `lda` - are at
`:1393-1396`, `:1419-1421`, `:1431-1433`, `:1238-1241`, `:1254-1259`, `:1261-1265`.
Of those, `:1254-1259` is defect-free for a different reason than the rest: it ends in
an unconditional `jmp $loopLabel` rather than consuming the flags.

The non-const-range helpers do not have the question at all, because there the `lda`
genuinely feeds an `adc` that precedes the `inc`/`dec` (`:609-613`, `:655-659`), or
does not involve `inc`/`dec` at all (`:706-713` is `lda V` / `clc` / `adc #step` /
`sta V`).

`optimizeIncDec` (`AsmOptimizer.kt:754-780`) only cancels counterproductive pairs
(`iny`/`dey`, `inx`/`dex`, `ina`/`dea`, ...), and nothing in the module inspects
`inc`/`dec <mem>`. That is consistent with there being nothing to eliminate: the `lda`
is the only correct way to get the post-increment value into A.

---

## 4. `uword >` spills an operand to a zeropage scratch word; `uword >=` does not

```asm
ldy  #4
lda  (p8b_btree.p8s_contains.p8v_r),y
tax
iny
lda  (p8b_btree.p8s_contains.p8v_r),y
tay
txa
sta  P8ZP_SCRATCH_W2
sty  P8ZP_SCRATCH_W2+1
ldy  p8v_value+1
lda  p8v_value
cmp  P8ZP_SCRATCH_W2
tya
sbc  P8ZP_SCRATCH_W2+1
bcs  p8_label_gen_459_else
```
(benchmark.asm 17325-17339, from `b_btree.p8:97` `if r.value > value`)

Compare `b_btree.p8:73` `if parent.value >= value` (benchmark.asm 17203-17213),
which compares A/Y directly with no spill.

**Root cause: the operand swap for operator reuse**

Both generators have the same three-tier fallback and are structurally identical:

- `wordLessValue`, `IfElseAsmGen.kt:779-794` - `tryGetStaticAddress(right)`, then
  `asConstInteger()`, then generic `assignWordOperandsToAYAndVar(left, right, "P8ZP_SCRATCH_W2")`
- `wordGreaterEqualsValue`, `IfElseAsmGen.kt:909-924` - the same three tiers

The asymmetry comes entirely from `IfElseAsmGen.kt:629-637`:

```kotlin
"<=" -> wordGreaterEqualsValue(right, left, signed, jumpAfterIf, stmt)
">"  -> wordLessValue(right, left, signed, jumpAfterIf, stmt)
```

For `parent.value >= value` the right side is a `PtIdentifier`, so
`tryGetStaticAddress` (`AsmGen.kt:247-276`) hits and no spill occurs. For
`r.value > value` the swap makes the *tested* side `r.value`, a `^^Node` pointer
field - not an identifier, not a const-indexed array - so it falls through to
the generic path and spills.

**Change**

Cheapest, local: in `wordLessValue` (`IfElseAsmGen.kt:779`), before the generic
fallback, also try `asmgen.tryGetStaticAddress(left, 2)` and
`left.asConstInteger()`, and if either hits, swap the roles and call `code(...)`
with inverted branch polarity. This makes `>` symmetric with `>=` and removes the
spill for `complexX > simpleY`. `wordLessValue` is only called from `:632` and
`:634`, so the callers are unaffected - though note the unswapped `<` at `:632` will
also pick up the new tier whenever its *right* operand has no static address, which is
benign but slightly outside the stated scope.

"Purely additive" undersells it: `code()` (`IfElseAsmGen.kt:667-777`) has four
hardcoded-polarity sub-cases (signed/unsigned x `jump != null`/`jump == null`), so
polarity has to be threaded through all four, and the two indirect forms
(`:672-695`, `:729-740`) branch *around* a `jmp (label)` and cannot simply be
inverted - they have to be restructured. That is a real diff, not an additive one.

**The "structural alternative" in the earlier draft does not work and should be
dropped.** It proposed a `wordGreaterValue` delegating to `wordGreaterEqualsValue`
with an inverted branch. Inverting `a >= b` yields `a < b`, not `a > b`; expressing
`>` needs `!(a <= b)`, and there is no `wordLessEqualsValue` in `IfElseAsmGen` at all
(only `wordLessEqualsZero` at `:985`, the `== 0` special case). The other reading -
`wordGreaterEqualsValue(right, left)` inverted, which *is* `left > right` - is
byte-for-byte the call already present at `:634`, so it changes nothing.

`IfExpressionAsmGen.kt:363`/`:384` has the identical asymmetry and needs the same
treatment.

---

## 5. Dead `phy` / `ply` around an indexed read-modify-write

```asm
ldy  p8v_ci
phy
lda  p8b_elite_ship.p8v_cargohold,y
clc
adc  p8v_amount
ply
sta  p8b_elite_ship.p8v_cargohold,y
```
(benchmark.asm 11061-11067; also 11069-11075, 11164-11170, 11172-11178)

Nothing between `phy` and `ply` touches Y: `lda abs,y`, `clc` and `adc abs` all
leave Y untouched. Seven wasted cycles per statement (`PHY` 3 clocks, `PLY` 4). The
`phy`/`ply` emission is 65C02-gated - `AsmGen.kt:467` and `:496` are both inside
`if (isTargetCpu(CpuType.CPU65C02))`, with an `else` that emits `tya | pha` / `pla |
tay` instead - which is why it does not appear in the C64 build. Every other `phy`
emission in the module is gated the same way.

The earlier draft said this affects *every* `arr[i] op= v`. That overstates it: the
pair is genuinely live for `*=` and `/=`, which emit an `ldy $variable` *inside* the
window (`AugmentableAssignmentAsmGen.kt:1912-1916`), and the `MEMORY`/`ARRAY`/
`EXPRESSION` source kinds add a `sta P8ZP_SCRATCH_B1` inside it as well
(`:538-561`). `arr[i]++`/`--` never reach this path at all. The 7-cycle claim holds
for the shown `+=`/`-=` shape with a variable, literal or register operand.

**Where it comes from**

`assignment/AugmentableAssignmentAsmGen.kt:515-563`, the byte-array `+=`/`-=`
in-place path:

```kotlin
asmgen.loadScaledArrayIndexIntoRegister(target.array, CpuRegister.Y)
asmgen.saveRegisterStack(CpuRegister.Y, false)          // -> phy
asmgen.out("  lda  ${targetArrayVar.name},y")
...
    asmgen.restoreRegisterStack(CpuRegister.Y, true)    // -> ply
...
asmgen.out("  sta  ${targetArrayVar.name},y")
```

`saveRegisterStack`/`restoreRegisterStack` emit the literal `phy`/`ply` only on
65C02 (`AsmGen.kt:467` and `AsmGen.kt:496`).

**Why the existing rule cannot fire**

`optimizeUselessPushPopStack` (`AsmOptimizer.kt:889-940`), inner helper
`optimize(register, lines)` at `:785`:

```kotlin
if(lines[0].instruction.startsWith("ph$register")) {
    if(lines[2].instruction.startsWith("pl$register")) { ... }
    else if(lines[3].instruction.startsWith("pl$register")) { ... }
```

Two independent blockers:

1. **Position.** The rule anchors on `lines[0] == "phy"`. In the generated code
   `lines[0]` is `ldy p8v_ci`; `phy` is at index 1 and `ply` at index 5. The
   4-line window cannot span them.
2. **Span.** Even given the right anchor, the rule supports gaps of only 1 or 2
   instructions (it tests `lines[2]` and `lines[3]` only). The observed gap is 4.

That is the whole list. An earlier draft added a third blocker - that the
`register !in second` guard is a blunt substring test which would reject
`lda p8v_cargohold,y` for containing the character `y`. That is **not** a blocker:
the guard is `lines[1].instruction.take(6).lowercase()` (`AsmOptimizer.kt:895`), so
for this instruction it tests `"lda  p"`, which contains no `y` at all, and the
guard passes. The truncation is over-conservative in the opposite direction - it
rejects short operands that genuinely start with the register letter, e.g.
`lda  y_ptr` truncates to `"lda  y"` - not for `,y`-suffixed memory operands.

**Change**

Generalize `optimizeUselessPushPopStack` (or add a new pass) to a multi-line
`ph<r>` ... `pl<r>` elimination, with a window of at least 6, gating every
intervening instruction on `!modifiesYRegister()` and rejecting `jsr`. The
correct helper already exists and is used elsewhere in the module:

`AsmOptimizer.kt:67-71`
```kotlin
private val yModifyingMnemonics = setOf("ldy", "sty", "tay", "tya", "phy", "ply", "iny", "dey")
internal fun String.modifiesYRegister(): Boolean { ... }
```

**It must also reject windows containing a label**, which the earlier draft omitted.
`getLinesBy` (`AsmOptimizer.kt:83-88`) filters only blank lines and `;` comments, so
label lines stay in the window; an intervening label is a potential branch target, so
control could enter the middle of the sequence and a surviving `ply` would pop
garbage. Section 6's proposal does say "or a label" - this one should too.

---

## 7. `jsr` / `sta X` / `lda X` survives

```asm
jsr  p8b_maze.p8s_generate.p8s_choose_uncarved_direction
sta  p8v_direction
lda  p8v_direction
bne  p8_label_gen_352_else
```
(benchmark.asm 14776-14780, from `b_maze.p8:77-78`)

The value was in A and is written to memory and read back one instruction later.

**Why the existing rule is blocked**

`optimizeStoreLoadSame` matches at `:503`, but then takes the branch-follows
guard at `:511-517`:

```kotlin
if(third.isBranch()) {
    // a branch instruction follows, we can only remove the load instruction if
    // another load instruction of the same register precedes the store instruction
    // (otherwise wrong cpu flags are used)
    val loadinstruction = second.take(3)
    lines[0].trimmed.startsWith(loadinstruction)
}
```

`third.isBranch()` is true, `loadinstruction` is `"lda"`, and
`lines[0].trimmed` is `jsr p8b_maze...` - so `attemptRemove` is false. (The `else`
half at `:518-526` additionally does an IO-address check, so the branch guard really
is the only thing blocking this case.)

The separately-mentioned `lda V / sta D / lda V / sta D2` dedup rule lives at
`AsmOptimizer.kt:249-264` (inside `optimizeSameAssignments`). It handles a
duplicated **load**, and requires a 4th `sta`. It does not apply to a value
originating in a register.

**Note on soundness**: the guard is defensible for a generic peephole - `sta`
modifies neither A nor the flags, so the values are identical but the *flags* left
by the `jsr` are not.

**Change**

Both options proposed in the earlier draft are unsound, and the draft's soundness
note above does not cover them:

1. *"relax `:511-517` when `lines[0]` is a `jsr` to a routine that returns its result
   in A"* does not establish what the guard protects. "Returns in A" says nothing
   about which instruction last set N/Z: a callee that ends `lda result / tax / rts`
   returns the right value in A with wrong flags. This contradicts the note directly
   above it.
2. *"a dedicated rule that verifies nothing in between touches A or the flags"* can
   never fire as worded, because the `jsr` **is** in between.

A sound fix needs a different mechanism rather than a relaxed peephole. Two
candidates, in order of preference:

- Establish the invariant once, callee-side: that a Prog8 subroutine returns with A
  and N/Z mutually consistent. That is checkable in the code generator (a property of
  the calling convention) rather than at every peephole site. Note it does not even
  hold universally for the callee in this very example - its primary exit is
  `lda p8v_choice / rts` (benchmark.asm 15128-15129), but the alternate path at
  `:15130-15132` loops back to `:15116` and can then exit at `:15112 lda #0 / rts`.
- Or attack it from the store side instead: when the target is single-assignment,
  drop the `sta` and keep the `lda`. That removes a store rather than a load and
  sidesteps the flags entirely, at the cost of needing single-assignment
  information that the peephole does not currently have.

Until one of those lands, treat this item as **blocked**, not as a cheap peephole
win.

---

## 8. Float: no power-of-two multiply special case

`b_mandelbrot.p8:31` `y = x*y*2.0 + yy` generates a full KERNAL float multiply to
add 1 to the exponent byte:

```asm
lda  #<prog8_float_const_5
ldy  #>prog8_float_const_5
jsr  floats.CONUPK
jsr  floats.FMULTT
```
(benchmark.asm 8724-8727; `prog8_float_const_5 .byte $82,$00,$00,$00,$00` at
21272 is float 2.0 - biased exponent, zero mantissa)

**Where it comes from**

`optimizedMultiplyExpr` (`BinaryOpAssignmentsGen.kt:386`) has **no `isFloat`
branch at all**. Both the non-const path and the const path end in
`else -> return false` at lines **455** and **534**, dropping
through to `anyExprGen.assignAnyExpressionUsingStack` at `:36` and landing in
`AnyExprAsmGen.assignFloatBinExpr`:

`assignment/AnyExprAsmGen.kt:144-149`
```kotlin
"*" -> {
    assignFloatOperandsToFACandARG(expr.left, expr.right)
    asmgen.out("  jsr  floats.FMULTT$tSuffix")
    asmgen.assignRegister(RegisterOrPair.FAC1, assign.target)
```

The same applies to `+`/`-`: `optimizedPlusMinExpr` handles `isByte`,
`isWord|isPointer`, `isLong` and returns `false` at `:1235` for float.

**Why the upstream strength reduction does not save us here.** The simple-AST
optimizer *does* rewrite `x * 2^n` into `x << n`
(`ExpressionOptimizers.kt:124-134`, using `powersOfTwoFloat` from
`Conversions.kt:6`), but that whole block is gated on:

```kotlin
// x * power-of-two -> bitshift
else if (!node.right.type.isFloat) {
```

so it is **deliberately skipped for floats** and never fires for `2.0`. That gate
is the whole reason the miss surfaces in the 6502 backend, and it is why the
fix belongs here rather than in the AST optimizer (a float left-shift would be
nonsense; the exponent-byte increment is a codegen-level concern).

**The constant's value is available at codegen time** -
`expr.right.asConstValue()` (`simpleAst/.../AstExpressions.kt:113`), and
`VariableAllocator.getFloatAsmConst` (`VariableAllocator.kt:35-43`) already
interns the value. Codegen also already emits the symbolic `floats.FAC_ADDR+n`
(`AsmGen.kt:949-971`), and `FAC_ADDR+0` is the exponent byte.

**Trap for any new fast path**: `asConstInteger()` (`AstExpressions.kt:104-110`)
does `is PtNumber -> number.toInt()`, so for float `2.0` it returns **`2`, not
null**. Use `asConstValue()`, or guard on `expr.type.isFloat` before the integer
paths. The existing code gets away with it only because the `isByte`/`isWord`/
`isLong` checks at `:462`/`:481`/`:523` precede the constant tests.
(`BinaryOpAssignmentsGen.kt:459` additionally throws an explicit `AssemblyError`
for a float `0`/`1` right operand, so the hazard is already known to the code.)

**Change**

Add a power-of-two case before the `else -> return false` at
`BinaryOpAssignmentsGen.kt:507`: detect `d == 2^k` (or `2^-k`) via
`asConstValue()`, then emit exponent arithmetic on `FAC1`:

```
lda  floats.FAC_ADDR+0
clc
adc  #k
bcs  overflow_check
sta  floats.FAC_ADDR+0
```

Requires a guard for denormals/zero (exponent byte 0 needs mantissa
normalisation) and for overflow (>= 255), so this is a delicate optimisation, not
a one-liner.

**Payoff caveat**: no byte is saved - the four-instruction `lda/ldy/CONUPK/FMULT`
block is replaced by roughly the same number of instructions. The win is purely
cycles: `FMULT` (CX16 KERNAL `$fe1e`) is a full multi-byte multiply, on the order
of hundreds of cycles, versus about 15 for an exponent add.

**Easier second site**: the augmented form `x *= 2.0`
(`AugmentableAssignmentAsmGen.inplacemodificationFloatWithLiteralval`, `:3898`)
already special-cases literals - `0.5 -> FADDH` at `:3913`, `1.0 -> inc_var_f` at
`:3906`, `10.0 -> MUL10` at `:3945` - but not `2.0`. Its comment at `:3939`
claims "assume that code optimization is already done on the AST level for
special cases such as 0, 1, 2", which is wrong for the non-augmented expression
form the benchmark actually hits.

---

## 9. Float: unconditional `CONUPK` + T-variant even on CX16

Eight sites in the mandelbrot inner loop (benchmark.asm 8627-8628, 8646-8647,
8650-8651, 8654-8655, 8665-8666, 8669-8670, 8680-8681, 8691-8692), every one of
the form `jsr floats.CONUPK` followed by `jsr floats.FMULTT`/`FADDT`/`FSUBT`.

**The only target-conditional line**

`assignment/AnyExprAsmGen.kt:127-130`:

```kotlin
private fun assignFloatBinExpr(expr: PtBinaryExpression, assign: AsmAssignment): Boolean {
    // C64/PET32 ROM T-variant entries skip CONUPK and need arisgn+Z flag set up beforehand.
    // CX16 ROM entries are already fixed and handle this internally.
    val tSuffix = if(asmgen.options.compTarget.name in listOf(C64Target.NAME, PETTarget.NAME)) "_NZ" else ""
```

Used at `:134` (FADD), `:140` (FSUB - no suffix), `:146` (FMUL), `:152` (FDIV).
Only **`FSUBT`** is genuinely suffix-free; `c64/floats.p8:56` documents that it does
the `arisgn`/Z setup internally. `FDIVT` **does** take `$tSuffix`
(`AnyExprAsmGen.kt:152`), and `c64/floats.p8:60` carries the warning
`"bare entry, needs Z flag=facexp + arisgn; use FDIVT_NZ wrapper"` - as do `FADDT`
and `FMULTT`. An earlier draft of this item claimed `FSUBT` *and* `FDIVT` were both
suffix-free; only the former is, which narrows the guard the change below has to
extend.

**The T-vs-non-T choice is completely unconditional.** There is no
`if (target == cx16) use FMULT` anywhere in the module. `grep FMULT` over
`codeGenCpu6502/` returns `AnyExprAsmGen.kt:146`, `AugmentableAssignmentAsmGen.kt`
`:3768`, `:3816`, `:3953`, `PointerAssignmentsGen.kt:492` and `:1858` - the non-T
entries are used unconditionally in the in-place/augmented paths, with no target
guard at all.

**CX16 can use the non-T entries.** `compiler/res/prog8lib/cx16/floats.p8:34-42`:

```
extsub $fe12 = FSUB(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt from A/Y - fac1
extsub $fe15 = FSUBT() clobbers(A,X,Y)                      ; fac1 = fac2 - fac1
extsub $fe18 = FADD(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 += mflpt
extsub $fe1b = FADDT() clobbers(A,X,Y)                      ; fac1 += fac2
extsub $fe1e = FMULT(uword mflpt @ AY) clobbers(A,X,Y)      ; fac1 *= mflpt
extsub $fe21 = FMULTT() clobbers(A,X,Y)                     ; fac1 *= fac2
extsub $fe24 = FDIV(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt in A/Y / fac1
extsub $fe27 = FDIVT() clobbers(A,X,Y)                      ; fac1 = fac2 / fac1
```

They take the operand address in A/Y - the *same* A/Y the code already computes
for `CONUPK` - so `CONUPK` is pure waste on this path.
`compiler/res/prog8lib/c64/floats.p8:54-62` confirms the C64/PET32 T-entries are
the ones that skip CONUPK, which is why the `_NZ` wrapper exists and is correctly
C64/PET32-only.

**Change**

In `AnyExprAsmGen.assignFloatBinExpr` / `assignFloatOperandsToFACandARG`
(`:127-216`), when the right operand has a static 5-byte memory address
(`PtIdentifier` via `AsmGen.getStaticAddressLowHigh()` at `AsmGen.kt:308`, or a
float `PtNumber` via `allocator.getFloatAsmConst()`), skip the FAC2 load
entirely and emit the non-T entry:

- `+`: `assignExpressionToRegister(left, FAC1)`; `lda #<addr / ldy #>addr / jsr floats.FADD`
- `-`: `assignExpressionToRegister(right, FAC1)`; `lda #<addr(left) / ldy #>addr(left) / jsr floats.FSUB` (order matters: `FSUB` is `mflpt - fac1`)
- `*`: same shape with `FMULT`
- `/`: same shape with `FDIV`

**Payoff, corrected: 3 bytes saved per site, not 1, over 8 sites (24 bytes).** The
earlier draft said 1 byte and 16 sites, which is wrong twice over. The arithmetic is:

```
current:   lda #<addr   2 | ldy #>addr   2 | jsr floats.CONUPK  3 | jsr floats.FMULTT  3  = 10
proposed:  lda #<addr   2 | ldy #>addr   2 | jsr floats.FMULT   3                            =  7
```

The point the earlier draft missed is that the FAC2-load block and the non-T-call
block are the *same shape* - `lda #imm / ldy #imm / jsr` either way, 7 bytes - so
nothing shrinks except the extra `jsr` for the T-variant. That is 3 bytes. And the
section's own opening counts **8** sites in the mandelbrot inner loop (all of the
stated `CONUPK` + T-op form; benchmark.asm 8703, 8722, 8726, 8730, 8741, 8745,
8756, 8767), not 16. So 8 x 3 = 24 bytes, and it also removes the
`arisgn`/Z-flag dependency on that path. It is still not a large win: the real float
cost in that loop is the ROM calls themselves, which only item 8's exponent
arithmetic can avoid.

Note the augmented path (`x *= 2.0`) already emits exactly this non-T shape, so this
change is really "make the non-augmented path do what the augmented path already
does" - which strengthens the case.

`FSUBT` (`:140`) has no `_NZ` suffix and must keep the FAC2 route on C64/PET32.
`FDIVT` (`:152`) does take the suffix, so the target guard is extended only for `/`
and can otherwise be applied unconditionally - a smaller special case than the
earlier draft described.

---

## 10. Signed word shift by a constant count calls the runtime routine

```asm
ldx  #7
jsr  prog8_math.lsr_word_AY
```
(benchmark.asm 7508-7509 and 9 more; 12 sites in this build - 10 with `ldx #7`,
2 with `ldx #5`)

`b_3d.p8` uses signed `word`, which is why every one of them takes the slow path.

`BinaryOpAssignmentsGen.optimizedBitshiftExpr`, const-count branch (line 610
`// bit shift with constant value`), word case:

- unsigned, count 0..7 -> fully unrolled (`:686-695`)
- signed, count 1 -> unrolled with sign-fill via `cmp #$80 / ror` (`:674-681`)
- signed, count 2..7 -> `ldx #$shifts | jsr prog8_math.lsr_word_AY` (`:685`)
- counts 8..15 -> runtime call for both (`:699` signed, `:701` unsigned)
- counts >= 16 -> `lda #0 | ldy #0` (`:720`)

**Change**

Replace the `else` arm at `:685`: when `shifts` is small (say 2..4), emit
`repeat(shifts) { pha; tya; cmp #$80; ror a; tay; pla; ror a }` - reusing the exact
9-instruction sign-fill sequence already used for `shifts == 1` at `:674-681`.
Threshold at ~4 to keep code size in check. The loop body at
`compiler/res/prog8lib/math.asm:1067-1071` is 5 instructions
(`sec / ror P8ZP_SCRATCH_B1 / ror a / dex / bne -`), so a 7x unroll is not obviously
smaller in bytes but is clearly faster in cycles. The sign test is *outside* the loop,
at `math.asm:1063-1064` (`cpy  #0` / `bpl lsr_uword_AY`) - there is no `bmi` in this
routine at all, as an earlier draft of this item claimed.

**Reconciling the two paths is wider than this item states.** The in-place `>>=`
path handles `value == 8` and `value >= 16` inline too
(`AugmentableAssignmentAsmGen.kt:2710-2728`), not just 1..7, so the augmented and
non-augmented paths disagree across 8..15 as well as 2..7. The signed **byte** path
has the same shape (`:624` emits `ldy #$shifts | jsr prog8_math.lsr_byte_A` for
counts 2..7). Any threshold chosen here should be applied consistently across all of
these.

**Note**: the in-place `>>=` path already unrolls and has no runtime call at all -
`AugmentableAssignmentAsmGen.kt:2717-2727` emits a sign-fill loop for `value > 2`
and a fully unrolled `lda $msb | asl a | ror $msb | ror $lsb` for 1-2. So the
`ForLoopsAsmGen`-adjacent behaviour is inconsistent with the augmented path.

---

## 11. ~~`VariableAllocator` hotness scoring is inert~~ - **REFUTED, see Retracted claims**

This was originally written up here as a confirmed bug. It is not. The full
story is in the Retracted claims table below. The short version: at codegen time
`PtIdentifier.name` is **already** the fully scoped, symbol-prefixed name
(e.g. `p8b_maze.p8s_solve.p8v_cx`), which is exactly the key
`StStaticVariable.scopedNameString` uses, so the original `varInfo[node.name]`
lookup was correct all along.

The remaining real constraint in this area is **capacity, not scoring**. On CX16
with `%zeropage basicsafe` the pool is `$22-$7F` (94 bytes,
`codeCore/src/prog8/code/target/zp/CX16Zeropage.kt:39-41`) minus the reserved
scratch bytes, while this benchmark program has 672 static variables. 54 win
zeropage. The dumped top of the (working) score table is:

```
54  p8b_life.p8s_next_gen.p8v_ptr
44  p8b_elite_planet.p8s_soup.p8v_result_ptr
38  p8b_btree.p8s_remove.p8v_n
35  p8b_elite_galaxy.p8v_seed
31  p8b_main.p8s_start.p8v_benchmark_number
28  p8b_sprites.p8v_sprite_reg
26  p8b_rotate3d.p8s_draw_edges.p8v_rz
26  p8b_matrix_math.p8s_rotate_vertices.p8v_i
26  p8b_btree.p8v_root
24  p8b_maze.p8s_generate.p8v_cx
24  p8b_maze.p8s_generate.p8v_cy
22  p8b_maze.p8s_solve.p8v_pathstackptr
20  p8b_adpcm.p8v_pstep
...
```

which is a sensible ranking. `maze.solve.cx`/`cy` losing zeropage to
`maze.generate.cx`/`cy` is the scoring working as designed, not a defect.

Two **optional** refinements remain worth considering, both minor:

- `VariableAllocator.kt:101` filters on
  `variable.dt.isIntegerOrBool || variable.dt.isPointer`, so floats and arrays are
  excluded from the ranked allocation regardless of usage. For floats this costs
  almost nothing on CX16 (they are only touched via `MOVFM`/`MOVMF`/`CONUPK`,
  which take an address in A/Y at identical cost either way). It does mean
  *arrays* never compete, which is defensible.
- The weighting `isPointer || isWord -> 2, else 1` does not distinguish a pointer
  that is used as an **indirect base** (which costs an extra
  `sta P8ZP_SCRATCH_PTR / sty P8ZP_SCRATCH_PTR+1` copy on every dereference when it
  is not in zeropage) from one that is merely stored and loaded. In `b_btree` this
  is visible: `find_successor.p` loses zeropage to `succ`, which is never
  dereferenced. A weight of 3 for indirect-base pointers would be a cheap
  improvement. Not measured, so speculative.

---

## 12. `long * <const>` always calls `multiply_longs`, the most expensive routine in the area

`multiply_longs` (`compiler/res/prog8lib/math.asm:234`) is a fixed 32-iteration
signed 32x32 shift-add with sign branches and up to two `_neg_*` calls. Costed
instruction by instruction it is ~51 cycles per iteration when the multiplier bit is
clear and ~89 when it is set, so `long * 19` is ~1830 cycles and `long * 1000` ~1940.
A 32-bit Horner chain would be ~204 and ~570, i.e. **3.4-9x**.

**Not recommended** - the blocker is size, not speed. A Horner chain needs no extra
scratch (the existing convention already holds the multiplicand in `cx16.r12`/`r13` and
the result in `r14`/`r15`, which is exactly the 8 bytes `r` and `x` need, and it works
on all 6502 targets - on c64 those symbols are *also* in the zero page, at `$1c`/`$20`
rather than `$cff8`; an earlier draft of this item claimed otherwise, confusing them
with the unrelated `p8v_r12r13sl` at `c64/syslib.p8:892`).

The size figures, measured by assembling the real routine for cx16 rather than
estimated:

| | earlier draft | measured |
|---|---|---|
| `multiply_longs` main body | | 106 bytes |
| `_neg_r12r13` | | 26 bytes |
| `_neg_r14r15` | | 26 bytes |
| **cluster total** | ~214 | **158** |
| 19-expansion | ~144 | **~98** |
| const-long call site | ~27 | **~35** |

So the cluster is 158 bytes, not 214. A Horner chain for 19 (`10011`) folds the first
add into the accumulator initialisation, needing 4 doublings and 2 conditional adds:
16 bytes of init + 32 of shifts + 50 of adds = ~98. (For `* 1000` it is ~313 bytes.)
The call site is ~35 because `BinaryOpAssignmentsGen.kt:520-522` now materialises the
constant inline into `cx16.r14`/`r15`, 16 bytes of that being pure setup. There is
still no `longShiftAddExpansion` counterpart to `byteShiftAddExpansion` /
`wordShiftAddExpansion` (`codeCore/src/prog8/code/cpu6502/ConstMultiply.kt:25,55`),
which are byte and word only.

Net: 158 bytes saved once, ~79 bytes cost per site (98 expansion vs 19 bytes of inline
const materialisation), so break-even is **exactly 2 sites** - not the 1.8 the earlier
draft derived, and its own numbers did not produce 1.8 either (214/144 = 1.49). The
conclusion survives but is marginal at two sites rather than clearly negative, so this
item is closer than it looked: worth doing for a program with exactly two or three
`long * <const>` sites and no other long routine linked, and not otherwise. `long`
barely appears in the 6502 examples at all (4 of 152 example `.p8` files).

**Recommended instead**: an early exit in `multiply_longs` when the multiplier shifts
down to zero. That is ~6 bytes inside the routine once (`lda W1 / ora W2 / beq _done`),
it helps *every* long multiply including the variable-by-variable paths no constant
expansion can touch, and it skips the 27 dead iterations a small multiplier leaves
behind. The earlier draft put that at "~40% of the cost", which understates it by about
2x: for `* 19` the multiplier `10011` has 5 significant bits, so iterations 6..32 are
dead, and 27 x 51 = 1377 of the 1746 loop cycles is **~79%** (for `* 1000`, 22 dead
iterations is ~60%). That makes the early exit clearly the better recommendation.

Two further notes: `AstChecker.kt:308` already warns "for loop using a long counter could
be very slow" about code the compiler could make fast, and the long path writes
`cx16.r14`/`r15`.

`codeGenM68k` never calls `prog8_math`, so none of this applies there. On a 68020+
target a Horner chain would be strictly worse anyway, since `muls.l #19,d0` is a
single instruction. Note that this is **68020+ only**:
`codeGenM68k/src/prog8/codegen/m68k/InstrArithmetic.kt:368-382` branches on
`cpu < CpuType.M68020` and falls back to a `p8_umult32`/`p8_smult32` helper routine
for a plain 68000, which is the case for `amiga500`.

---

## Retracted claims

These patterns were investigated and found **not** to be 6502-backend problems.
Listed so the same ground is not re-covered.

| Pattern | Why it is not a backend bug |
|---|---|
| maze `19 * cy` misses a fast path because `BinaryOpAssignmentsGen.kt:385` only tests `expr.right.asConstInteger()` | The premise is true - line 385 is the only constness test in the function, and `expr.left.asConstInteger()` appears nowhere in the module - but the conclusion is false. `optimizeOperandOrder` (`simpleAst/.../ExpressionOptimizers.kt:532-565`, running in the fixpoint loop at `Optimizer.kt:120`) normalises operand order *before* codegen, swapping so the simplest term is on the right (`complexity()` at `:498-508` rates a const as 0 and an identifier as 1; `maySwapOperandOrder()` at `AstExpressions.kt:279` permits `*`). A minimal repro dumps the Simple AST with `$13` on the right and produces byte-identical assembly for both `19*x` and `x*19`. The real cause was that 19 was in neither hand-written constant set and there was no generic shift-add form to fall back on - both since fixed, so this pattern no longer occurs. Position-dependence only appears under `-noopt`, which returns early at `Optimizer.kt:82-83`. |
| **~~`VariableAllocator.computeUsageScores` never matches, so the hotness ranking is inert~~** | **REFUTED by direct instrumentation.** The claim was that `varInfo` is keyed by `scopedNameString` (`:139`) but queried with the *bare* `PtIdentifier.name` (`:147`), so every lookup misses. In fact, at codegen time the Simple AST identifiers are **already** fully scoped and symbol-prefixed: a sample dump of `PtIdentifier.name` values is `p8b_main.p8s_start.p8v_benchmark_number`, `p8b_adpcm.p8s_decode_benchmark.p8v_nibble`, `txt.color2.bgcol` - and `StStaticVariable.scopedNameString` for the same variables is character-for-character identical. The original `varInfo[node.name]` lookup, the `scores[node.name]` key, and the `usageScores[it.scopedNameString]` read at `:99` are all consistent. Dumping the score map confirms it is well populated and sensibly ranked (1941 identifiers, 672 variables, top score 54 for `life.next_gen.ptr`). The *apparent* arbitrary placement (e.g. `maze.solve.cx`/`cy` in BSS while `maze.generate.cx`/`cy` get zeropage) is a **capacity** limit - 94 zeropage bytes on CX16 BASICSAFE versus 672 static variables - not a scoring defect. An attempted "fix" that resolved identifiers by walking up to the nearest `PtNamedNode` and appending the bare name made the score map **empty** and dropped zeropage occupancy from 54 to a differently-ordered 62, which is how the error was caught. **Do not "fix" this.** |
| textelite `population * 8` uses a full `multiply_words` | Misread of benchmark.asm 12648-12658. The three `asl a` **are** the intended strength reduction, produced by the simple-AST rewrite `x * 2^n -> x << n` at `ExpressionOptimizers.kt:124-134`. The following `multiply_words` is `productivity * (...)` - a genuine 16x16 operation (`b_textelite.p8:801-802` declares `ubyte population` and `uword productivity`). No ubyte multiplicand is being widened here. Note this same rewrite is gated on `!node.right.type.isFloat` (`ExpressionOptimizers.kt:125`), which is why the float case in item 8 is a genuine miss rather than another misreading. |
| queens reloads `board[i]` three times (asm 10107, 10117, 10136) | Three independent AST evaluations of `board[i]` in three different sub-expressions of `b_queens.p8:17` (`board[i]`, `board[i]-i`, `board[i]+i`). Each operand genuinely needs the value; the 6502 backend has no CSE and is not expected to. The item 1 boolean materialization *does* destroy A and thereby block the existing `optimizeStoreLoadSame` reload elision, which is a real but second-order effect of item 1, not a separate bug. |
| `or` chains do not short-circuit | They do. `BinaryOpAssignmentsGen.kt:1742` emits `bne $shortcutLabel`, gated at `:1722` by `!expr.right.isSimple() && expr.operator != "xor"`. Both `and` (`:1726-1736`) and `or` (`:1737-1747`) are symmetric. What is materialized is each *operand comparison*, per item 1. |
| **`inc V` immediately followed by `lda V` in a byte `for` loop is a redundant reload** (former item 3) | **NOT A DEFECT - retracted on review.** `INC <memory>` is a read-modify-write on memory (opcode `EE` absolute, `E6` zeropage): it sets N/Z from the memory result and leaves A untouched. The accumulator form is a separate opcode, emitted as `inc  a`. So there is no value in A to compare and the `lda` is required, at the emission site and in any peephole. The earlier write-up contradicted itself, correctly noting that `inc`'s Z flag suffices for the wrap-to-zero case and then claiming the same instruction left the value in A. Restructuring was costed and is worse (`lda/cmp/beq/inc/bne` = 18 cycles against 15). `DEC` has the same property, so the whole sibling-helper table retracted with it. |
| `var_fac1_less_f` returning a 0/1 byte instead of branching (asm 9321-9330) is a library problem | Reached only because of item 1: the caller wants a branch and the routine returns a boolean. Fixing item 1 removes the need to call it at all. |
| `maze`/`life` hot variables in BSS, and inconsistent placement of identical variables across sibling procs | Real and measurable, but the cause is **capacity**, not scoring - 94 zeropage bytes on CX16 BASICSAFE against 672 static variables. Item 11 (the scoring) was refuted, so there is nothing to fix first; the ranking demonstrably works. See item 11. |
| 6502 codegen for `b_textelite`'s string routines, the arena `defer` frame, `multiply_words`'s BSS accumulator, `lsr_word_AY` not specialising on a constant count | `multiply_words`'s accumulator being in BSS despite the routine's own comment ("routine could be faster if this were in Zeropage") is in `compiler/res/prog8lib/math.asm`, not the 6502 backend. The arena `defer` frame and `divmod_uw_asm`'s `_divisor` placement are likewise library-side. The `lsr_word_AY` non-specialisation is item 10. |

---

## Related

- `docs/CODEBASE-KNOWLEDGE-GRAPH.md` - module layout and the compilation pipeline
- `docs/source/_static/symboldumps/` - library routine signatures
- The `-compareir` and `-noopt` switches are the right tools for A/B measurement
  of any of these changes.
