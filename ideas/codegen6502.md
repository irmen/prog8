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
2026-09-25 (21207 lines), i.e. **before** any of the changes recorded here, so they
locate the original problem rather than the current code. The `.kt` line references
may likewise have drifted; the function names are the reliable anchor.

**Status: analysis complete. Everything below is still open** - items that have since
been implemented are removed rather than marked done, so if you are looking for the
constant-multiply shift-add work, the `uword * ubyte` narrowing, the six broken
`mul_byte_*`/`mul_word_*` library routines, the long constant-shift fix, or the `x * x`
range bug, those are in the git history rather than here. Benchmark version 14.0,
weighted total **9199** (see [Measuring](#measuring)).

---

## 0. Recommended implementation order

The order below is roughly by payoff-per-risk, not by dependency - the earlier
draft put item 11 first on the assumption that it was a scoring bug. It is not
(see Retracted claims), so nothing is unblocked by doing it first.

1. **Item 1** - comparison operands of `and`/`or` (unlocks the queens case too)
2. **Item 3** - `inc` / `lda` loop-counter reload
3. **Item 2** - `stx <zp>` / `ldy <zp>` -> `tay`
4. **Item 5** - dead `phy` / `ply` around an indexed RMW
5. **Item 6**, **Item 7** - the two remaining peephole gaps
6. **Item 4** - `uword >` operand asymmetry
7. **Items 8, 9, 10, 12** - float and multiply/shift special cases
8. **Item 11** - optional `VariableAllocator` weighting refinement only; measure first

| Item | State |
|---|---|
| 1-10, 12 | open |
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
(benchmark.asm 8616-8621, from `b_mandelbrot.p8:30`)

**Where it comes from**

- `optimizedComparison`, `assignment/BinaryOpAssignmentsGen.kt:49-142` emits the
  `cmp / rol a / and #1 / eor #1` sequence for the unsigned-byte `<` fast path
  (and the same without `eor #1` for `>=`, at `:98-141`).
- The generic 0/1 path for word/signed/`==`/`!=` is
  `BinaryOpAssignmentsGen.kt:145-180`, which synthesizes a `PtIfElse` with
  `ld? #1` / `ld? #0` bodies and hands it back to the if-else generator.
- `optimizedLogicalExpr` (`BinaryOpAssignmentsGen.kt:1699-1720`) can only ask for
  a 0/1 byte in A and then `beq`/`bne` it. It has no "give me the flags and let
  me branch" facility.

**The asymmetry is directly observable in one source file.** `b_maze.p8:150`
`if cx>0 and ...` generates a direct `beq` (the AST optimizer reduced `cx>0` to
plain `cx`). `b_maze.p8:152` `if cx<numCellsHoriz-1 and ...` generates the
6-instruction form above (benchmark.asm 14930-14935). Same `if`, same block,
same conjunction.

**Direct-branch lowering already exists but is unreachable here.**
`IfElseAsmGen.kt:483-579` (`translateByteLess` and siblings) and the word
equivalents (`wordLessValue` at `:666`, `wordGreaterEqualsValue` at `:797`,
`wordEqualsValue` at `:1616`) all emit compare-and-branch directly. They are
only reached when the comparison is the *entire* `if`/`while` condition
(`IfElseAsmGen.kt:287-343`). All of them are `private`, so
`optimizedLogicalExpr` cannot reuse them.

**Second manifestation** - each `or` operand's 0/1 materialization
(benchmark.asm 10109-10116, 10129-10136, 10148-10154, from `b_queens.p8:17`):

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
`optimizedLogicalExpr` (`BinaryOpAssignmentsGen.kt:1699-1720`), when an operand
is a `PtBinaryExpression` whose operator is in `ComparisonOperators`, emit the
comparison's flags plus an inverted branch to the shortcut label, instead of
`assignExpressionToRegister(..., A)` + `beq`. This recovers both the 3 wasted
instructions per operand and the register liveness of the left-hand side.

`IfExpressionAsmGen.kt:226-235` has the same structure and the same fix.

Cheap mitigation (peephole only, recovers bytes but not liveness): two rules in
`AsmOptimizer.kt`:

1. `cmp X / rol a / and #1 / eor #1 / <branch>` -> `<inverted bcc> <branch>`
   (and the no-`eor` variant -> `<bcc>`), only when the branch target is a
   compiler-generated label.
2. `<branch> Lelse / lda #1 / bra Lafter / Lelse: lda #0 / Lafter: <branch2>`
   -> `<inverted branch> Lafter / Lafter: <branch2>`. The existing
   `beq+jmp+label -> bne` rule at `AsmOptimizer.kt:761-777` does not match
   because it requires a `jmp` (not `bra`) at position 1 and a branch at
   position 0.

Rule 2 also leaves the previously-loaded value intact in A, which then lets the
existing `optimizeStoreLoadSame` logic elide a reload.

---

## 2. `stx <zp>` + `ldy <zp>` where a single `tay` does

```asm
ldx  p8b_main.p8v_benchmark_score_msb,y
stx  P8ZP_SCRATCH_REG
ldy  P8ZP_SCRATCH_REG
jsr  txt.print_uw
```
(benchmark.asm 311-314; also 321-324, 343-346, 393-396; 14 sites in this build)

**Where it comes from**

`assignment/PrimitiveAssignmentsGen.kt:1386`, in `assignRegisterpairWord()`
(declared at `:1243`):

```kotlin
RegisterOrPair.AX -> when(target.register!!) {
    RegisterOrPair.AY -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG") }
    RegisterOrPair.AY -> { }   // (AX)
    RegisterOrPair.XY -> { asmgen.out("  stx  P8ZP_SCRATCH_REG |  ldy  P8ZP_SCRATCH_REG |  tax") }
```

Mirrors at `:1398` and `:1409` (`AY -> AX` and `AY -> XY`, which should be `txa`),
and the single-byte variants at `:1090` (`X -> Y`) and `:1141` (`Y -> X`).

**Why no peephole fires**

`optimizeStoreLoadSame` (`AsmOptimizer.kt:487`, rule registered at `:35`)
enumerates **only same-register** pairs at `:503-508`:

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
(`AsmOptimizer.kt:542-550`) converts `pha`+`ply` -> `tay` and `phy`+`pla` ->
`tya`. It simply has no store/load arm.

**Change**

Add a cross-register arm to `optimizeStoreLoadSame`: `stX <zp>` immediately
followed by `ldY <zp>` on the same non-IO zeropage operand -> rewrite to
`  tay` (or `  txa`). `getAddressArg()` (`AsmOptimizer.kt:593`) already resolves
`P8ZP_*` symbols, which are emitted as plain zeropage constants at
`ProgramAndVarsGen.kt:71`.

---

## 3. `inc <var>` immediately followed by `lda <var>`

`ForLoopsAsmGen.kt:1392` is the exact branch point:

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
        lda  $varname          // <-- reloads what inc just left in A
        cmp  #${range.last+1}
        bne  $loopLabel
$endLabel""")
}
```

- Line 1392: end-of-loop is detected by wrap-to-zero, so `inc`'s own Z flag
  suffices. Correct and optimal.
- Line 1397-1404: end-of-loop is detected by comparing against
  `range.last + 1`. The author prepends `lda $varname` to get a `cmp` operand,
  without noticing that `inc` on the previous line already left the
  post-increment value in A. Pure waste: 4 cycles and 2 bytes per iteration.

**Evidence** - two `for` loops in the same file, same lowering function:

```asm
; b_textelite.p8:495  for pi in 0 to 255  -> range.last == 255 -> GOOD
inc  p8v_pi
bne  p8_label_gen_295_for_loop          ; 9 cycles/iter

; b_textelite.p8:426  for ci in 0 to len(names)-1  -> range.last == 16 -> BAD
inc  p8v_ci
lda  p8v_ci
cmp  #17
bne  p8_label_gen_285_for_loop          ; 15 cycles/iter
```

Both dispatch through `translateForSimpleByteRangeAsc` (`ForLoopsAsmGen.kt:1382`),
reached from `translateForSimpleByteRangeAsc` selection at `:1202-1206`. The
divergence is entirely internal to that one function.

**Same defect in the sibling helpers**

| Lines | Emitted tail | Redundant instruction |
|---|---|---|
| `:1400` | `inc V` / `lda V` / `cmp #last+1` | `lda V` |
| `:1425-1426` | `dec V` / `lda V` / `cmp #255` | `lda V` |
| `:1438-1439` | `dec V` / `lda V` / `cmp #last-1` | `lda V` |
| `:1246-1247` | `inc V` / `inc V` / `lda V` / `cmp #last+2` | `lda V` (2nd `inc`) |
| `:1269-1270` | `dec V` / `dec V` / `lda V` / `cmp #last-2` | `lda V` (2nd `dec`) |

The correctly-written tails (which consume `inc`/`dec` flags directly) are at
`:1394-1395`, `:1419-1421`, `:1431-1433`, `:1238-1241`, `:1254-1258`,
`:1261-1265`. The non-const-range helpers do **not** have this problem, because
there the `lda` genuinely precedes the `inc` (`:609-613`, `:655-659`, `:706-713`).

**No peephole can catch it.** `optimizeIncDec` (`AsmOptimizer.kt:647-670`) only
cancels counterproductive pairs (`iny`/`dey`, `ina`/`dea`, ...). Nothing in the
module inspects `inc`/`dec <mem>` at all. This must be fixed at the emission site.

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
(benchmark.asm 17203-17217, from `b_btree.p8:97` `if r.value > value`)

Compare `b_btree.p8:73` `if parent.value >= value` (benchmark.asm 17081-17092),
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
spill for `complexX > simpleY`. Purely additive, no other callers affected.

Structural alternative: drop the swap at `:633-634` and add a `wordGreaterValue`
that calls `wordGreaterEqualsValue` with an inverted branch, so the original left
operand stays in A/Y and the original right operand gets the fast path. Slightly
larger diff, more robust, because the fast path then always sees the operand the
programmer wrote on the right.

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
(benchmark.asm 10985-10991; also 10993-10999, 11088-11094, 11096-11102)

Nothing between `phy` and `ply` touches Y: `lda abs,y`, `clc` and `adc abs` all
leave Y untouched. Seven wasted cycles per statement. This affects **every**
`arr[i] op= v` in every Prog8 program on 65C02 targets (the `phy`/`ply` emission
is 65C02-gated, which is why it does not appear in the C64 build).

**Where it comes from**

`assignment/AugmentableAssignmentAsmGen.kt:513-561`, the byte-array `+=`/`-=`
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
65C02 (`AsmGen.kt:437` and `AsmGen.kt:466`).

**Why the existing rule cannot fire**

`optimizeUselessPushPopStack` (`AsmOptimizer.kt:782-851`), inner helper
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
   instructions. The observed gap is 4.

The per-instruction guard is also a blunt substring test (`register !in second`)
rather than a mnemonic check, which is over-conservative in exactly this case:
`lda p8v_cargohold,y` contains the character `y`, so it would be rejected even
though it does not modify Y.

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

---

## 6. Redundant index reload between two consecutive indexed accesses

```asm
ldy  p8b_maze.p8v_stackptr
lda  p8b_maze.p8v_cx_stack,y
sta  p8v_cx
ldy  p8b_maze.p8v_stackptr      ; <-- Y is untouched by the sta above
lda  p8b_maze.p8v_cy_stack,y
```
(benchmark.asm 14703-14708; the store-side twin is 14713-14718)

**The existing rule, and its actual limitation**

`optimizeSamePointerIndexingAndUselessBeq` (`AsmOptimizer.kt:415-485`). Its
header comment states the intent exactly (`:417-423`):

```kotlin
// Optimize same pointer indexing where for instance we load and store to the same ptr index in Y
// if Y isn't modified in between we can omit the second LDY:
//    ldy  #0
//    lda  (ptr),y
//    ora  #3       ; <-- instruction(s) that don't modify Y
//    ldy  #0       ; <-- can be removed
//    sta  (ptr),y
```

The two index rules are at `:434-442` and `:443-451`. It is **not** hardcoded to
`ldy #0` or to `lda (ptr),y` - it is agnostic to the operand form. It *is*
hardcoded to:

1. `f2` must be `lda` (a load) and `f5`/`f6` must be `sta` (a store).
2. `secondvalue == fifthvalue` (`:439`) / `secondvalue == sixthvalue` (`:448`) -
   **the same array address** must appear in both the load and the store.
3. `.endsWith(",y")` on the memory operand.

So it will never match two consecutive indexed accesses to *different* arrays
(`cx_stack` vs `cy_stack`), and the read-side shape additionally fails the
offset requirement (the second `ldy` is at window index 3, the rule expects 4).

**Change**

Add a relaxed variant next to the existing pair: `ldy V` / `ld? ...,y` /
[0..2 non-Y-modifying instructions] / `ldy V` -> drop the second `ldy V`. It needs
**no** requirement that the `,y` operands be equal - only that nothing in between
modifies Y, and that the window does not cross a `jsr` or a label.

---

## 7. `jsr` / `sta X` / `lda X` survives

```asm
jsr  p8b_maze.p8s_generate.p8s_choose_uncarved_direction
sta  p8v_direction
lda  p8v_direction
bne  p8_label_gen_352_else
```
(benchmark.asm 14678-14682, from `b_maze.p8:77-78`)

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
`lines[0].trimmed` is `jsr p8b_maze...` - so `attemptRemove` is false.

The separately-mentioned `lda V / sta D / lda V / sta D2` dedup rule lives at
`AsmOptimizer.kt:249-264` (inside `optimizeSameAssignments`). It handles a
duplicated **load**, and requires a 4th `sta`. It does not apply to a value
originating in a register.

**Note on soundness**: the guard is defensible for a generic peephole - `sta`
modifies neither A nor the flags, so the values are identical but the *flags* left
by the `jsr` are not. Relaxing it requires knowing the callee returns its result
in A.

**Change**

Either relax `:511-517` for the specific case where `lines[0]` is a `jsr` to a
Prog8 routine that returns its result in A, or add a dedicated `jsr` + `sta X` +
`lda X` rule with a wider window that verifies nothing in between touches A or
the flags.

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
(benchmark.asm 8648-8651; `prog8_float_const_5 .byte $82,$00,$00,$00,$00` at
21169 is float 2.0 - biased exponent, zero mantissa)

**Where it comes from**

`optimizedMultiplyExpr` (`BinaryOpAssignmentsGen.kt:384`) has **no `isFloat`
branch at all**. Both the non-const path (`:386-434`) and the const path
(`:435-508`) end in `else -> return false` at lines **433** and **507**, dropping
through to `anyExprGen.assignAnyExpressionUsingStack` at `:34` and landing in
`AnyExprAsmGen.assignFloatBinExpr`:

`assignment/AnyExprAsmGen.kt:144-149`
```kotlin
"*" -> {
    assignFloatOperandsToFACandARG(expr.left, expr.right)
    asmgen.out("  jsr  floats.FMULTT$tSuffix")
    asmgen.assignRegister(RegisterOrPair.FAC1, assign.target)
```

The same applies to `+`/`-`: `optimizedPlusMinExpr` handles `isByte`,
`isWord|isPointer`, `isLong` and returns `false` at `:1199` for float.

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
(`AsmGen.kt:919-923`), and `FAC_ADDR+0` is the exponent byte.

**Trap for any new fast path**: `asConstInteger()` (`AstExpressions.kt:104-110`)
does `is PtNumber -> number.toInt()`, so for float `2.0` it returns **`2`, not
null**. Use `asConstValue()`, or guard on `expr.type.isFloat` before the integer
paths. The existing code gets away with it only because the `isByte`/`isWord`/
`isLong` checks at `:440`/`:457`/`:496` precede the constant tests.

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
(`AugmentableAssignmentAsmGen.inplacemodificationFloatWithLiteralval`, `:3887`)
already special-cases literals - `0.5 -> FADDH` at `:3898`, `1.0 -> inc_var_f` at
`:3895`, `10.0 -> MUL10` at `:3929` - but not `2.0`. Its comment at `:3928`
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

Used at `:134` (FADD), `:140` (FSUB - no suffix, correctly: `c64/floats.p8:56`
notes FSUBT does not need the Z flag hack), `:146` (FMUL), `:152` (FDIV).

**The T-vs-non-T choice is completely unconditional.** There is no
`if (target == cx16) use FMULT` anywhere in the module. `grep FMULT` over
`codeGenCpu6502/` returns only `AugmentableAssignmentAsmGen.kt:3757`, `:3805`,
`:3942` and `PointerAssignmentsGen.kt:492` - the non-T entries are used
unconditionally in the in-place/augmented paths, with no target guard at all.

**CX16 can use the non-T entries.** `compiler/res/prog8lib/cx16/floats.p8:33-41`:

```
extsub $fe12 = FSUB(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 = mflpt - fac1
extsub $fe15 = FSUBT() clobbers(A,X,Y)                      ; fac1 = fac2 - fac1
extsub $fe18 = FADD(uword mflpt @ AY) clobbers(A,X,Y)       ; fac1 += mflpt
extsub $fe1b = FADDT() clobbers(A,X,Y)                      ; fac1 += fac2
extsub $fe1e = FMULT(uword mflpt @ AY) clobbers(A,X,Y)      ; fac1 *= mflpt
extsub $fe21 = FMULTT() clobbers(A,X,Y)                     ; fac1 *= fac2
extsub $fe27 = FDIVT() clobbers(A,X,Y)
```

They take the operand address in A/Y - the *same* A/Y the code already computes
for `CONUPK` - so `CONUPK` is pure waste on this path. `compiler/res/prog8lib/c64/floats.p8:544-548`
confirms the C64/PET32 T-entries are the ones that skip CONUPK, which is why the
`_NZ` wrapper exists and is correctly C64/PET32-only.

**Change**

In `AnyExprAsmGen.assignFloatBinExpr` / `assignFloatOperandsToFACandARG`
(`:127-216`), when the right operand has a static 5-byte memory address
(`PtIdentifier` via `AsmGen.getStaticAddressLowHigh()` at `AsmGen.kt:278`, or a
float `PtNumber` via `allocator.getFloatAsmConst()`), skip the FAC2 load
entirely and emit the non-T entry:

- `+`: `assignExpressionToRegister(left, FAC1)`; `lda #<addr / ldy #>addr / jsr floats.FADD`
- `-`: `assignExpressionToRegister(right, FAC1)`; `lda #<addr(left) / ldy #>addr(left) / jsr floats.FSUB` (order matters: `FSUB` is `mflpt - fac1`)
- `*`: same shape with `FMULT`
- `/`: same shape with `FDIV`

**Be honest about the payoff**: 1 byte saved per binary float op (16 in this
inner loop), plus it removes the `arisgn`/Z-flag dependency on that path. It is
not a large win. The real float cost in that loop is the ROM calls themselves,
which only item 8's exponent arithmetic can avoid.

`FSUBT`/`FDIVT` (`:140`, `:152`) legitimately have no `_NZ` suffix and must keep
the FAC2 route on C64/PET32, so the target guard has to be extended, not replaced.

---

## 10. Signed word shift by a constant count calls the runtime routine

```asm
ldx  #7
jsr  prog8_math.lsr_word_AY
```
(benchmark.asm 7672-7673; 12 sites in this build - 10 with `ldx #7`, 2 with
`ldx #5`)

`b_3d.p8` uses signed `word`, which is why every one of them takes the slow path.

`BinaryOpAssignmentsGen.optimizedBitshiftExpr`, const-count branch (line 582
`// bit shift with constant value`), word case:

- unsigned, count 0..7 -> fully unrolled (`:660-666`)
- signed, count 1 -> unrolled with sign-fill via `cmp #$80 / ror` (`:648-655`)
- signed, count 2..7 -> `ldx #$shifts | jsr prog8_math.lsr_word_AY` (`:658`)
- counts 8..15 -> runtime call for both (`:682` signed, `:684` unsigned)
- counts >= 16 -> `lda #0 | ldy #0` (`:693`)

**Change**

Replace the `else` arm at `:658`: when `shifts` is small (say 2..4), emit
`repeat(shifts) { pha; tya; cmp #$80; ror a; tay; pla; ror a }` - reusing the exact
9-instruction sign-fill sequence already used for `shifts == 1` at `:648-655`.
Threshold at ~4 to keep code size in check. The `math.asm:1079-1093` loop body is
5 instructions with a `bmi` sign test, so a 7x unroll is not obviously smaller in
bytes but is clearly faster in cycles.

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

`multiply_longs` (`compiler/res/prog8lib/math.asm:189`) is a fixed 32-iteration
signed 32x32 shift-add with sign branches and up to two `_neg_*` calls. Costed
instruction by instruction it is ~52 cycles per iteration when the multiplier bit is
clear and ~91 when it is set, so `long * 19` is ~1900 cycles and `long * 1000` ~2050.
A 32-bit Horner chain would be ~220 and ~434, i.e. **5-9x**.

**Not recommended** - the blocker is size, not speed. A Horner chain needs no extra
scratch (the existing convention already holds the multiplicand in `cx16.r12`/`r13` and
the result in `r14`/`r15`, which is exactly the 8 bytes `r` and `x` need, and it works
on all 6502 targets - on c64 those symbols map to `$cff8`-`$cffe` rather than zeropage).
But a 19-expansion is ~144 bytes against a ~27-byte call site, and the `multiply_longs`
cluster including its two `_neg_*` helpers (used by nothing else) is only ~214 bytes, so
an expansion is a net size *win* only for a program with exactly one such site
(break-even ~1.8 sites) and no other long routine linked. With realistic long code -
which also divides or shifts - it is pure growth. `long` barely appears in the 6502
examples at all.

**Recommended instead**: an early exit in `multiply_longs` when the multiplier shifts
down to zero. That is ~5 bytes inside the routine once, it helps *every* long multiply
including the variable-by-variable paths no constant expansion can touch, and it skips
the ~27 dead iterations a small multiplier leaves behind (~40% of the cost).

Two further notes: `AstChecker.kt:308` already warns "for loop using a long counter could
be very slow" about code the compiler could make fast, and the long path writes
`cx16.r14`/`r15`.

`codeGenM68k` emits native `muls.l` and never calls `prog8_math`, so none of this
applies there - and on that target a Horner chain would be strictly worse anyway, since
`muls.l #19,d0` is a single instruction.

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
| `or` chains do not short-circuit | They do. `BinaryOpAssignmentsGen.kt:1715` emits `bne $shortcutLabel`, gated at `:1695` by `!expr.right.isSimple() && expr.operator != "xor"`. Both `and` (`:1699-1709`) and `or` (`:1710-1720`) are symmetric. What is materialized is each *operand comparison*, per item 1. |
| `var_fac1_less_f` returning a 0/1 byte instead of branching (asm 9321-9330) is a library problem | Reached only because of item 1: the caller wants a branch and the routine returns a boolean. Fixing item 1 removes the need to call it at all. |
| `maze`/`life` hot variables in BSS, and inconsistent placement of identical variables across sibling procs | Real and measurable, but the cause is **capacity**, not scoring - 94 zeropage bytes on CX16 BASICSAFE against 672 static variables. Item 11 (the scoring) was refuted, so there is nothing to fix first; the ranking demonstrably works. See item 11. |
| 6502 codegen for `b_textelite`'s string routines, the arena `defer` frame, `multiply_words`'s BSS accumulator, `lsr_word_AY` not specialising on a constant count | `multiply_words`'s accumulator being in BSS despite the routine's own comment ("routine could be faster if this were in Zeropage") is in `compiler/res/prog8lib/math.asm`, not the 6502 backend. The arena `defer` frame and `divmod_uw_asm`'s `_divisor` placement are likewise library-side. The `lsr_word_AY` non-specialisation is item 10. |

---

## Related

- `docs/CODEBASE-KNOWLEDGE-GRAPH.md` - module layout and the compilation pipeline
- `docs/source/_static/symboldumps/` - library routine signatures
- The `-compareir` and `-noopt` switches are the right tools for A/B measurement
  of any of these changes.
