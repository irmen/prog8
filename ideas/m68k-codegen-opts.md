# M68k Code Generation Optimization Opportunities

**Status: analysis only / not implemented.** Derived by measuring the generated IR and
assembly of `examples/amiga/3d.p8` for the `amiga1200` target, then re-checking every
finding against all 20 `examples/amiga` programs and two 6502 examples.

**Re-verified against `bd1747926`, and one finding re-measured after a failed attempt.**
The section 0 reference numbers still reproduce exactly, and A1, B1 and C1-C3 are unchanged
as findings. Four things have moved since the first draft: the folded-division note that
used to be in A3 described a bug rather than an opportunity, and that bug is now fixed; A2's
"just match one opcode" claim was wrong and its ceiling turned out to be much lower than
this document assumed (see A2); store-to-load forwarding (A4) was implemented and then
deliberately reverted; B3's "library-wide" framing is wrong; and several section 5 counts no
longer reproduce. Each correction is marked inline.

This document deliberately **excludes** the flat `p8_regfile` memory block for virtual
registers. That problem already has a design and an implementation plan in
`m68k-register-allocation.md`, and it is called out as Deferred in
`docs/source/todo.rst`. Section 4 states, per finding, whether the finding survives a real
register allocator or is independent of it.

That exclusion turned out to be harder than it looks for one finding: **A4** (store-to-load
forwarding) is not independent of the regfile at all, and in fact gets *worse* rather than
better as long as the regfile is memory. It was implemented, measured as a `.text`
regression, and reverted. See A4.

Also related: `m68k-stack-memory-model.md` (activation records for locals; would make
findings C2 and B3 cheaper but does not remove the redundancy).

## 0. How the numbers were obtained

```bash
prog8c -target amiga1200 -out /tmp/3d examples/amiga/3d.p8
```

Reference program totals: **769 IR instructions, 54 chunks, 486 virtual registers**,
`.text` 7810 bytes, **2074 assembly instructions**.

Per-frame cost (one `while` iteration of `main.start`, i.e. one rendered frame), measured
from the generated code:

| region | instrs/frame | rough cycles | share |
|---|---|---|---|
| matrix setup (`3d.p8:66-96`, once per frame) | 144 | ~950 | 3% |
| 6x `sin8_fixed`/`cos8_fixed` | 288 | ~2180 | 6% |
| vertex loop body (x20) | 4220 | ~20700 | 61% |
| edge loop body (x30) | 2340 | ~10320 | 30% |
| **total** | **~7000** | **~34k** | ~24% of a 50Hz A1200 frame |

Instruction counts are exact. Cycle counts are estimates using rough 68020 weights
(move mem 4-6, `muls.w` ~20, `divs.w` ~25, `divs.l` ~100, `bsr`+`rts` ~24, other ~4-6) and
are only meant for ranking, not budgeting. The frame budget used is 7.09MHz x 20ms =
141800 cycles.

The IR chunk labels referred to below: `p8_label_gen_1` = vertex rotation body,
`p8_label_gen_2` = perspective projection, `p8_label_gen_3`..`gen_6` = the four
`px[i]`/`py[i]` clamps, `p8_label_gen_7` = edge drawing loop. Asm line numbers refer to
the generated `3d.asm`: `2068` = `gen_1`, `2244` = `gen_2`, `2313` = `gen_3`, `2419` =
`gen_7`, `2547` = `sin8_fixed`, `2645` = `setup_copper`.

None of the findings below is a correctness bug. All of them are missed optimizations.

## 1. Group A: IR-level findings (affect every backend)

### A1. An existing index/address dedup pass does not cover the indexed-memory form

**Do not implement general CSE for this.** CSE in this compiler has a bad track record
(it is easy to get subtly wrong and there is no m68k execution test to catch it), and it is
also unnecessary: the piece of CSE that this finding needs already exists, and the problem
is coverage, not absence.

`deduplicateAddressComputations` (`codeGenIntermediate/src/prog8/codegen/intermediate/IRPeepholeOptimizer.kt:1188-1290`)
is a hand-rolled, pattern-matched version of exactly this idea. It finds groups of

```
loadm.p  ptrReg, buf
loadm.w  idxReg, idxVar
ext*     extReg, idxReg
addr.p   destReg, {ptrReg,extReg}
```

with identical `(buf, idxVar)`, and replaces later groups with a single `loadr` copy from
the first. It has a working barrier check: a group is not reused if the first group's
destination register is redefined (`VirtualRegister... in ins.definitions`) or if the
buffer/index memory is written in between.

**It does not fire on the 3d hot loop, because the compiler emits a different shape there.**
The pass only matches an explicit address computation, but indexed access is also emitted
as a single indexed memory operand:

```
loadm.b  r83.b,[p8v_i]
ext.b    r84.w,r83.b
loadx.w  r85.w,[p8v_vx+r84.w*2]     <- 20 of these in 3d
```

In `3d.p8ir` that is 20 `loadx` occurrences versus 9 of the matched `addr`/`loadm.p` form
(the pointer arithmetic in the startup code). So 19 zero-extended index values survive per
vertex, each costing 4 instructions in the m68k output:

```asm
move.b  p8b_main.p8s_start.p8v_i,p8_regfile+214
moveq  #0,d0
move.b  p8_regfile+214,d0
move.w  d0,p8_regfile+216
```

**Scope, because each IR chunk is its own basic block with its own fresh virtual
registers:**

| region | basic blocks | ext.b sites | reachable by a block-local pass |
|---|---|---|---|
| `gen_1` vertex rotation | 1 | 9 | yes, 9 -> 1 |
| `gen_2` projection | 1 | 4 | yes, 4 -> 1 |
| `gen_3`,`gen_4`,`gen_5` clamps | 3 separate blocks | 2+2+2 | **no** |
| `gen_7` edge loop | 1 | 6 | yes, 6 -> 2 (`e`, plus the two `v1`/`v2` loads) |

A block-local pass reaches 13 of the 19 sites: about **44 of the 211 instructions per
vertex**, 16 of 78 per edge. The other 6 sit in the clamp chain, where `gen_2` stores
`px[i]` and `gen_3`..`gen_6` read it back; that needs cross-block store-to-load forwarding
(A4) or a value live in a register across blocks, i.e. A4 plus the register allocator. The
6 redundant `lea` in the same region are B1 and independent of both.

#### Recommended order for this finding

1. **Do A2 first.** Making the induction variable a register removes 19 of the 20 sites
   *structurally*, with no pattern matching and no aliasing reasoning, because the value is
   already a register. Whatever is left after that is much smaller and may not be worth a
   new pattern.
2. **Add one global pre-pass: the set of symbols whose address is ever taken** (used as the
   base of an `Indexed`/`Indirect` memory reference, or passed as a pointer). This turns
   "can this store alias the load?" from a per-pattern judgement call into a set-membership
   test, and it is the thing that makes the rest reviewable.
3. **Extend `deduplicateAddressComputations`** to the `loadx` form, reusing its barrier
   framework, its `definitions` redefinition check and its earliest-source/`claimed` logic,
   gated on step 2. A bounded change to one existing function, not a new CSE framework.

Note that step 2 also **retroactively justifies the existing pass**, whose barrier is
exact-key only:

```kotlin
fun writesMem(ins: IRInstruction, key: MemKey): Boolean {
    if(memKey(ins) != key) return false
    return ins.memoryEffect == MemoryEffect.WRITE || ...
}
```

It is sound only under the unstated precondition that the loaded symbol's address is never
taken. Worth confirming that precondition holds for the symbols the pass currently matches;
if it does not, that is a latent bug independent of anything new here.

Also note the pass is not a pure deleter: the redundant load has a *distinct destination
register*, so consumers must be rewritten to read the first load's register. The existing
passes already do that kind of operand rewriting (`removeNeedlessLoads` copies an
instruction with `withRegister(...)`).

#### Not m68k-specific, but the fix is not shared

`examples/cx16/cube3d.p8` emits `ldy p8v_i` 17 times, so the same redundancy exists on
6502 (correction: 17 is the file total spread over 3 loop bodies, at most 13 in any one
body, and each body also has the 4-instruction memory-counter tail). It is however **not**
fixed by anything in this section: the default 6502 backend (`codeGenCpu6502.AsmGen6502`,
`Compiler.kt:772-780`) does not consume the IR at all, so `IRPeepholeOptimizer` never runs
for it. Only `codeGenM68k`, `codeGenNew6502` (with `-newcodegen`) and the VM go through
`IRCodeGen` -> `IRPeepholeOptimizer`. The 6502 backend instead has the same idea at the asm
level in `canDropIndexReload` / `isNonVolatileIndexOperand`
(`codeGenCpu6502/.../AsmOptimizer.kt:460-502`), which drops an index reload when Y still
holds the value and nothing in between modifies Y, writes the base, transfers control, or
is volatile. That is the right level for 6502, and the payoff is smaller anyway because a
zero-page index load is 3 cycles.

Since the first draft that 6502 pass has both been repaired and widened: an unsound
index-reload fold was fixed, and the relaxed variant now also covers two consecutive
indexed accesses rather than only a matched load/store pair
(`AsmOptimizer.kt:542-558`). Its barrier list is also longer than documented: it requires
the same operand (`AsmOptimizer.kt:491`) and no intervening label (`:498`). The
conclusion for this document is unchanged.

#### Verification

There is no m68k execution test, so a wrong fold ships silently. For any change here:
recompile the 20 amiga examples and byte-diff `.text` (only the expected instructions may
disappear), and use `prog8c -compareir` against a `-noopt` build to confirm the
instruction-level delta is exactly the collapsed groups.

### A2. Counted loops are not recognized when the induction variable is read in the body

`codeGenIntermediate/src/prog8/codegen/intermediate/IRCodeGen.kt:1057` only emits an
`IRLoopChunk` (which the m68k backend turns into `dbra`) when
`!isLoopVarUsed(forLoop, loopvarSymbol)`. Both hot loops in the reference program read
their counter (`vx[i]`, `edgesFrom[e]`), so they fall back to the memory-counter form:

```asm
addq.b  #1,p8b_main.p8s_start.p8v_i      ; 3d.asm:2398-2403
move.b  p8b_main.p8s_start.p8v_i,p8_regfile+212
cmpi.b  #20,p8_regfile+212
bne  p8_label_gen_1
```

4 instructions plus a memory read-modify-write per iteration, 50 times per frame. The
`dbra` machinery already exists and already handles the hard parts: `translateLoopChunk`
(`codeGenM68k/src/prog8/codegen/m68k/AsmGen.kt:801-838`) reserves d7, saves/restores it
for nested loops, and saves/restores it around calls (`AsmGen.kt:886-893`). In this
program `dbra` only ever fires in the 5 stdlib `memset`-family loops.

Fix direction: relax the gate to "the loop variable is not *assigned* in the body" and
keep the counter in d7. This also feeds A1: if the induction variable is a register, the
index rebuilds disappear with it.

#### Half of this exists, but it does not fire, and the blocker is not an opcode

The gate at `IRCodeGen.kt:1057` has not moved, but a large part of the proposed fix is
already built. `optimizeLoopCounters` (`IRPeepholeOptimizer.kt:1355-1514`) is a
**cross-chunk** peephole that keeps a loop counter in a virtual register: it turns
`LOADM loopvar -> LOADR loopReg` in both the body chunks (`:1460-1475`) and the compare
chunk (`:1486-1502`), turns `INCM`/`DECM` into `INC`/`DEC` (`:1476-1485`), and deletes the
init `STOREM` chunk (`:1455`). Its safety gate is *exactly* the "not assigned in the body"
test proposed above (`:1421-1434`, exact `memory?.symbolName == loopvar` matching rather
than general alias analysis), plus a not-live-out check (`:1437-1451`).

**Correction to an earlier version of this document.** That version claimed the remaining
work was "just match one opcode" (`STOREIM` where the pass wants `STOREM`) and therefore a
cheap change. That is wrong, and it was established by trying it. There are three separate
mismatches, and the third is a design limitation:

1. `translateForInConstantRange` initialises the counter with `STOREIM`
   (`IRCodeGen.kt:1072`), while the pass requires `STOREM` (`:1367`).
2. The pass looks for the tail compare as a *separate* chunk, but the constant-range path
   emits `INCM; LOADM; CMPI; BSTNE` fused into one 4-instruction basic block.
3. **The init block is not isolated, and this is the real blocker.** The pass's entry
   condition is `stChunk.instructions.size != 1` (`:1365`), i.e. the counter init must be
   the *only* instruction in its block. That guard is load-bearing: it is what makes
   `sub.chunks.removeAt(idx)` at `:1455` safe. But `joinChunks` (`:52`, run immediately
   before the pass) merges any unlabelled chunk into its predecessor (`:231-244`), so the
   init is absorbed into the preceding basic block. In `3d.p8` the vertex loop's init shares
   a **104-instruction** block with the entire rotation-matrix setup.

**Do not work around this by relaxing the `size != 1` guard.** It was tried, and it is
unsound. With the guard relaxed, the pass treats *any* block ending in a `STOREM` to that
symbol as a counter init and takes `loopReg` from it, which can be an unrelated value. The
observed result is that the body's counter reads are rewritten to copy the wrong register
(so `3d.p8` renders nothing) and the tail's `INCM` becomes `INC` on that register, so the
compare never matches and the loop never terminates. Concretely: `TestCompilerVirtual`
fails and `TestVariableStepForLoops` hangs.

A sound fix has to identify the loop structurally rather than by block position, or the
counter init has to be isolated into a block that `joinChunks` will not merge. That is real
design work, not a one-line change, and the unit tests that the guard protects should stay.

Note also that A4's aliasing objection ("forwarding needs a value live across blocks") has
the same answer here: a per-symbol write barrier plus a liveness check is enough when the
symbol is a loop counter that provably does not escape. That part of the earlier analysis
still holds.

**And the ceiling is much lower than the rest of this section assumes.** A partial version
of the change was measured before the regression above was found. With the counter held in
a register, the *only* per-iteration saving was **1 instruction** (the tail goes from 4 to 3);
`3d.p8` went from 8010 to 7994 bytes of `.text` (-0.2%) and 769 to 767 IR instructions. The
19 zero-extensions per vertex **survive unchanged** - the `moveq #0` count in the vertex loop
is 19 before and after - because a register-resident counter still has to be widened to a
word-sized index for each `(a0,d0.w*2)` access. So A2 does *not* deliver the index-rebuild
elimination that A1 was chasing; it delivers a smaller win of its own. Re-measure before
picking this up.

Caveat for the 6502 backends: the same gate produces the same 4-instruction tail
(`inc / lda / cmp / bne`) in `examples/cx16/cube3d.p8`, but there the counter is already
in zero page so the counter itself is cheap, and the body of that loop calls
`verafx.muls16` / `prog8_math.divmod_w_asm`, which clobber the index registers. So on 6502
the realistic win from this finding is the loop tail only, not the index rebuilds of A1.

### A3. `divmod` is fused but unreachable for `long`, and the IR does not fuse a `/` + `%` pair

The obvious question for a `x / n` + `x % n` pair is "why doesn't the program just call
`divmod()`?", and for the word-sized case the answer is *it should*. There are exactly two
real instances in the example set, and they need different answers.

**textelite - a program-level miss, fixable today.** `examples/amiga/textelite.p8:1000-1004`:

```prog8
    sub print_10s(uword value) {
        txt.print_uw(value/10)
        txt.chrout('.')
        txt.print_uw(value % 10)
    }
```

Two `divu.w #10` in the output where one would do. `divmod` is available for `uword` and
maps to a single fused `DIVMOD`, so this one is a source-level change with no compiler work
at all. Correction: the call does not go through as it is usually written, because
`ExpressionSimplifier` requires all arguments to be in the same signedness class
(`ExpressionSimplifier.kt:312-315`) and the result must be assigned to exactly the right
number of targets. Neither of these compiles:

```prog8
divmod(value, 10)                              // ERROR: mixed ubyte/uword argument classes
q, r = divmod(value, 10)                      // ERROR: call returns too many values
```

The working form needs the cast and an explicit two-target declaration, and does collapse
to a single `divu.w #10` plus one `swap`:

```prog8
    sub print_10s(uword value) {
        uword q, r
        q, r = divmod(value, 10 as uword)
        txt.print_uw(q)
        txt.chrout('.')
        txt.print_uw(r)
    }
```

**adpcmbench - the language is missing a capability the backend already has.**
`examples/amiga/adpcmbench.p8:120-121` does the same thing on a `long`:

```prog8
        long blocks = wavfile.data_size / ADPCM_BLOCK_SIZE as long
        long remainder = wavfile.data_size % ADPCM_BLOCK_SIZE as long
```

and `divmod()` cannot be used, because:

- the builtin is typed for bytes and words only - `divmod`, `divmod__ubyte`,
  `divmod__uword`, `divmod__byte`, `divmod__word` (`codeCore/.../BuiltinFunctions.kt:128-132`).
  There is no `divmod__long`.
- the IR generator's return marshalling handles only `IRDataType.BYTE` and
  `IRDataType.WORD` and **throws** `AssemblyError("invalid type for DIVMOD")` for anything
  else (`codeGenIntermediate/.../BuiltinFuncGen.kt:262-290`, throws at :274 and :288).
- and, not listed in the first draft, the front end refuses the call before codegen is
  reached at all: `ExpressionSimplifier.kt:310-333` rewrites `divmod` into one of the four
  typed builtins by inferred argument type and its `else` branch (`:324-327`) rejects
  `long` with the same error. That is a third place to touch. It is also reassuring,
  because `:302-308` shows the identical `__long` dispatch already implemented for another
  builtin, so the shape the fix needs is already established.

The backend is *not* the limit. `emitDivModOp` (`codeGenM68k/.../InstrArithmetic.kt:611`)
has a `LONG` branch at :658-687 (using a helper routine on a plain 68000, which has no
`divul.l`/`divsl.l`). So the fused operation exists in the IR (`DIVMOD`/`SDIVMOD`, with the
remainder in the second destination register) and in the m68k backend; what stops at word
is the builtin surface and the IR marshalling. The program is *forced* into the two
hardware divisions, and the fix is mostly plumbing.

**Counting corrections.** An earlier version of this document claimed 3 of 20 examples.
That was too high: `blitcop` is a false positive from a detector that matched two
divisions sharing a constant, but they divide *different* operands (`cs*100/127` and
`sn*100/127`), so there is no shared dividend to fuse. That correction still stands, but the
2 of 20 figure in the first draft was itself too low. Re-measured, there are **4 fusion
pairs across 3 of 20** programs:

| program | site | dividend | divisor |
|---|---|---|---|
| `textelite.p8:1001,1003` | `value/10` + `value%10` | `uword` | 10 |
| `adpcmbench.p8:121-122` | `wavfile.data_size / % ADPCM_BLOCK_SIZE` | `long` | 256 |
| `adpcmbench.p8:168-169` | `total_us/1000000` + `% 1000000` | `long` | 1000000 |
| `wavc.p8:103-104` | `wavfile.data_size / % ADPCM_BLOCK_SIZE` | `long` | 256 |

The extra sites strengthen the case rather than weaken it: the `adpcmbench` pair at 168-169
and the whole `wavc` pair were missed, so the `/`+`%` fusion below is worth slightly more
than the first draft estimated.

Fix direction, in order of value per effort:

1. **Add `divmod` for `long`** (and check `POINTER`): a builtin signature plus a `LONG`
   branch in `BuiltinFuncGen`'s marshalling, mirroring the existing `WORD` case (m68k:
   quotient to slot 10, remainder to slot 11). The backend is already done. This turns
   adpcmbench into a source-level fix and closes a capability gap rather than papering
   over it.
2. **Independently, fuse a `/` and `%` on the same operands** into `DIVMOD`/`SDIVMOD` in the
   IR generator. This needs no change to any program, and it also catches the case where
   the two values are equal but not syntactically identical, which item 1 cannot help with.
   It is the more general fix; item 1 is the cheaper one and the better-specified API.

#### Note: division by a non-power-of-two constant

`adpcmbench.p8:168-169` emits `divs.l #1000000` and `% 1000000`, which no shift can help
and which want magic-number multiplication. No magic-number work exists anywhere in the
compiler today. It affects 1 of the 20 amiga examples. That is a much larger change and is
not proposed here.

### A4. No store-to-load forwarding, so loop-invariant locals are re-read constantly

The 9 rotation-matrix entries `Axx`..`Azz` (of the 11 coefficients `3d.p8:84-94` computes)
are built in the chunk immediately before the vertex loop, stored to memory, and then
re-loaded 9 times per vertex (`3d.asm:2081,2096,2113,...`). Similarly `angle_x/y/z` are
each loaded twice for the cos/sin pair, and `persp` 3 times per vertex.

Counted across the whole reference program, globals loaded from memory more than once: 190.
(The "45 to 536 across all 20" range in the first draft does not reproduce; see section 5.)

Full LICM needs registers, so the payoff depends on the register allocator
(`m68k-register-allocation.md`).

#### This was implemented, measured, and deliberately reverted

The first draft proposed store-to-load forwarding as "the cheap, allocator-independent part".
That is now known to be false on the current code, and the reason is the flat `p8_regfile`
that this document's own preamble excludes. The pass was written as `foldStoremLoadmToLoadr`
and is still in the tree, disabled, with the measurement recorded in the class comment at
`IRPeepholeOptimizer.kt:9-15` (added in commit `c8d29e6dc`):

> goal was to avoid redundant memory traffic for `storem rX,addr ; loadm rY,addr` by
> replacing the load with `loadr rY,rX`. Removed because on m68k/amiga the regfile is memory,
> so keeping the store's source register live for the loadr forces an extra spill
> (`move.b d0,regfile ; move.b regfile,mem ; move.b regfile,regfile2`) vs the original 2
> moves via memory. This increased textelite by ~344 bytes and other amiga examples by
> 20-230 bytes.

The call site is explicitly disabled for the same reason (`:67`).

So A4 is not an unimplemented finding, it is a *reverted* one, and its economics are
inverted from what the first draft assumed: with a memory-backed regfile, forwarding is a
`.text` pessimisation on exactly the target this document is about. Re-running it as written
would be a waste. What is left is:

- **General forwarding: blocked**, and should stay blocked until the regfile work lands. It
  is not allocator-independent; it is allocator-*dependent* in the opposite direction from
  what was assumed.
- **Per-symbol forwarding: viable and already precedented.** `optimizeLoopCounters`
  (`IRPeepholeOptimizer.kt:1355-1514`) does cross-chunk forwarding safely using an exact
  per-symbol write barrier plus a liveness check, not general alias analysis. That is the
  shape to extend if more of A4 is wanted before the allocator.
- **LICM: still entirely absent** (no `licm`/`loop-invariant`/`hoist` anywhere in
  `codeGenIntermediate`, `codeGenM68k`, `codeOptimizers` or `codeGenNew6502`), and remains
  blocked on allocation.

A4 should therefore be re-scoped against `m68k-register-allocation.md` rather than scheduled
as written. The redundancy it describes is real and still there; the cheap fix for it is
not.

## 2. Group B: m68k backend findings

### B1. Redundant `lea` for indexed access

Indexed array access is emitted as `lea base,a0` plus `(a0,d0.w*2)`
(`codeGenM68k/src/prog8/codegen/m68k/AsmGen.kt:511-517`). The 68020 scale-index mode is
used correctly, but the `lea` is re-emitted even when the address register already holds
the same base and has not been written in between. In the `px`/`py` region of the
reference program, **6 of the 10 `lea` are redundant**:

```asm
lea  p8b_main.p8s_start.p8v_px,a0      ; 3d.asm:2264
move.w  (a0,d0.w*2),d1
...
lea  p8b_main.p8s_start.p8v_px,a0      ; 3d.asm:2297  (a0 already holds p8v_px)
move.w  (a0,d0.w*2),d0
```

Present in 10 of 20 amiga examples, worst offenders textelite (16), window3d (6) and
stream-disk (5).

Note: absolute-indexed addressing (`base(a0,d0.w*2)`) is **not** a possible shortcut here,
it does not exist on the m68k and vasm rejects it. The `lea` itself is required; only the
repetition is removable.

Fix direction: an address-register liveness check in `AsmOptimizer.kt` (kill a0 on any
write, and on `bsr`/`jsr` conservatively). This involves a real CPU register rather than
a regfile slot, so it is independent of the register-allocator work.

### B2. Dead stores to regfile slots survive the peephole optimizer

`AsmOptimizer.kt:38-63` runs six 2-window passes plus two full-file passes
(redundant reload, bounce-to-global, jmp-to-next-label, tail call, redundant tst, msigb
spill, clamp-immediate, fuse-loadx-jumpi). There is no general dead-slot-store
elimination. In the reference program **71** regfile stores are dead, not 11 as the first
draft claimed: 10 are overwritten without an intervening read (9 of those inside the vertex
loop, plus 1 in `sin8_fixed` at `3d.asm:2599`), and 61 more are never referenced again in
their scope, mostly the `ext.b` index materialisations. The phenomenon is confirmed and
understated, not overstated.

The first draft's illustrative snippet also points at the wrong instruction. It attributes
the dead store to `3d.asm:2084`, but that store *is* read, by `3d.asm:2101 add.w d0,
p8_regfile+218`. The dead store is the one four lines earlier, at `3d.asm:2079`, which is
overwritten at 2084 with no read in between:

```asm
move.w  (a0,d0.w*2),d0             ; 3d.asm:2078  load vx[i]
move.w  d0,p8_regfile+218          ; 3d.asm:2079  DEAD: overwritten at 2084, never read
...
move.w  d0,p8_regfile+218          ; 3d.asm:2084  live: read by 2101
```

Per-file dead-store counts from the first draft are stale and do not reproduce (it claimed
circles 14, textelite 14, window3d 13; a narrow overwritten-only criterion gives circles 5,
textelite 2, window3d 11 across 11 of 20 files, and the broader never-read criterion gives
20 of 20). The finding stands on the mechanism, not on those numbers.

Note that a *targeted* dead-store suppression does now exist, at a different layer:
`AsmGen.kt:781-888` skips the regfile store when an immediate load is forwarded directly
into a hardware-register call argument and the value is not read elsewhere in the subroutine
(`isRegisterReadElsewhere`, `AsmGen.kt:916-924`; `canSuppressDeadStores`, `:787`). It covers
only that case and does not catch the store-overwritten-without-read pattern B2 is about.
`docs/source/todo.rst` line 59 tracks the equivalent work still outstanding for `new6502`.

This removes *instructions*, not slots, so it is worth doing regardless of what the
register allocator does. A forward liveness sweep over the regfile per subroutine scope
would be enough; the infrastructure it needs is already there. The `; ---- Subroutine:`
markers the optimizer delimits scopes with (`AsmOptimizer.kt:547-549`) are emitted by the
code generator (`AsmGen.kt:721`, `:770`) and are already consumed by `isSlotReadAfter`
(`:544-554`) for the bounce/msigb/clamp/fuse passes. One correction: the first draft
attributes this delimiter scheme to the AsmOptimizer's own file header, but it is
documented in the *generated* `.asm` header (`AsmGen.kt:620-625`, visible at `3d.asm:16-21`);
`AsmOptimizer.kt`'s own header comment has never mentioned the markers.

### B3. Library call arguments of one word or less travel through the callee's global

`sin8_fixed` calls `math.sin8(angle=...)` and the argument is written to the callee's
static parameter variable, which the callee then reads back:

```asm
move.b  p8_regfile+434,math.sin8.angle    ; 3d.asm:2561
bsr  math.sin8
move.b  d0,p8_regfile+436                 ; return value already in d0
```

with the callee side at `3d.asm:4889`:

```asm
move.b  math.sin8.angle,p8_regfile+1118
```

2 extra memory operations per call, 12 calls per frame in the reference program. All of
this reproduces exactly. The mechanism is the named-parameter path in
`InstrControl.kt:810-823` ("Store to the callee's parameter variable (if this is a named
param)"), which the immediate-forwarding optimisation deliberately skips (there is a test
pinning that, `TestInstructionSelectionOptimizations.kt:281-296`).

**Correction: this is not a library-wide compiler gap.** The first draft called it "a
library-wide pattern for any library routine with a small argument list". That is wrong, and
it overstates the work by a lot. Register passing already exists and is the norm for m68k
asm-backed routines: they are declared `asmsub` with explicit `@D0`/`@A0` slots, for example
`shared_amiga_blitter.p8:54 copy_rect(pointer src @A0, ... @D5)`,
`shared_m68k_memory_routines.p8:8 memset(long mem @D0, uword numbytes @D1, ubyte value @D2)`
and `shared_m68k_strings.p8:28 length(str string @A0) -> ubyte @D0`. The generated startup
code proves it: both `blitter.copy_rect` calls in `3d.p8` pass all 8 arguments purely in
registers (`3d.asm:1761-1801`).

`math.sin8` is on the wrong side of that line. It is declared a plain `sub` at
`shared_m68k_math.p8:49` (`sub sin8(ubyte angle) -> byte`), not an `asmsub`, so it gets the
memory-parameter path. **B3 is therefore a per-routine declaration problem, not a marshalling
design problem**: the fix for the specific case is to declare `sin8` as an `asmsub` with
`@D0`, or accept the 2 memory ops, and a sweep of the m68k library for plain `sub`s with
small argument lists would find any others. No backend change is implied by this case, and
that is a materially smaller piece of work than the first draft implied.

**Correction to the section 4 cross-reference.** The first draft said B3 is "covered by
§2.1/§2.7" of `m68k-register-allocation.md`. That overstates what those sections settle.
§2.1 (lines 101-125) describes the memory-parameter convention but explicitly refuses to
settle it ("This memory-parameter convention is the *interim* convention, not the permanent
one ... a future reader should not treat §2.1 as fixed"), pointing at
`m68k-stack-memory-model.md` §6.1. §2.7 (lines 301-316) is about *return values* only and
does not address argument passing at all. `m68k-stack-memory-model.md` §6.1 lists three
options and leaves the choice explicitly undecided. So the permanent answer to B3 is
genuinely still open, and it should be coordinated with that decision rather than assumed
to be settled.

## 3. Group C: reference-program specific

These are not general compiler gaps; they are the shape of `3d.p8` making a general gap
expensive. Listed for completeness.

- **C1: the clamp chain re-derives everything.** `px[i]` is stored, then re-indexed and
  reloaded 2-4 times; the four clamps become four separate conditional stores
  (`clr.w (a0,d0.w*2)` and friends) where one clamped store would do. Fixing this is
  mostly A1 plus keeping the element in a register across the if-chain.
- **C2: small per-frame memory traffic.** The bitplane swap uses 3 memory variables
  (6 memory ops/frame where `exg` would do), and
  `update_copper_bitplane` reloads both of its parameters to perform 2 word stores at
  fixed offsets, and is called once, so inlining it saves ~8 memory ops/frame. These are
  mem2reg/SROA on the compiler AST for locals that never escape their subroutine.
- **C3: `setup_copper` is 347 asm instructions and 99 `bsr` calls** (`3d.asm:2645-3341`)
  to build a static 202-word table. As a const data array it would be ~404 bytes of `.data`
  and no code. (The instruction and `bsr` counts both reproduce exactly. The first draft's
  "4.4% of `.text`" is a unit error: 347 is an instruction count being divided by 7810, a
  byte count. In bytes the routine is roughly 8-12% of `.text`, since the 99 `bsr` alone are
  396 bytes.) Likewise the two `blitter.copy_rect` call sequences at startup duplicate 16
  loads and 2 calls where a 2-iteration loop would do. These are library/source-level
  changes, not codegen.

## 4. Interaction with the planned register allocator

`m68k-register-allocation.md` addresses values living in `p8_regfile`. That work would
absorb some findings and leave others completely untouched:

| finding | survives a real register allocator? |
|---|---|
| A1 index/address dedup coverage | **yes** for the block-local part - the duplicated loads are upstream of allocation. The cross-block remainder needs allocation |
| A2 counted-loop recognition | **yes** - the gate is in shared IR code, not in allocation. But note the payoff is smaller than this document first claimed: see A2 |
| A3 `divmod` reachability + `/`+`%` fusion | **yes** - no allocation involved |
| A4 store-to-load forwarding | **no, as originally formulated.** General forwarding was implemented (`foldStoremLoadmToLoadr`, `IRPeepholeOptimizer.kt:9-15`) and reverted because with a memory-backed regfile it *grew* m68k `.text` by ~344 bytes on textelite. It is blocked on allocation in the opposite direction to what was assumed. Only the per-symbol variant is viable today |
| B1 redundant `lea` | **partly** - a register allocator with proper address-register classes largely removes the repetition, but the peephole is still correct to have |
| B2 dead regfile stores | **no** - a real allocator has no dead stores to eliminate; the pass is a stopgap that stops paying off at Stage 2 |
| B3 argument marshalling | **no for the allocator, but not because §2.1/§2.7 settle it.** Those sections defer rather than decide (§2.1 explicitly, §2.7 not at all). More importantly the specific case needs no allocator work: it is a plain `sub` that should be an `asmsub @D0` |
| C1/C2/C3 | **partly/no** - C1 follows A1, C2 is a frontend mem2reg concern, C3 is not codegen |

Practical consequence: A3's `divmod` items and B1 are worth implementing now, because they
are independent of (and not made obsolete by) the register-allocator work. A2 is **not** the
cheap item an earlier version of this document claimed: making the pass fire needs a sound
structural fix for the un-isolated counter-init block, and the measured ceiling is about 1
instruction per iteration and -0.2% of `.text` (see A2). A1 is *not* a new optimization to
design but an extension of an existing pass, and it should be sized on its own merit now that
A2 is unlikely to arrive first. B2 is a stopgap and should be scheduled with an eye to being
deleted at Stage 2 rather than polished. B3 has collapsed to a library declaration sweep.
A4 should be re-scoped, not attempted as written.

## 5. Generality check

Same patterns counted over all 20 `examples/amiga` programs compiled for `amiga1200`. Several
first-draft counts did not reproduce; those cells are marked with their corrected value and a
short reason. All 20 programs compile cleanly for this target.

| pattern | files affected | worst offenders |
|---|---|---|
| `ext.b` index rebuild | 17/20 (was 19/20) | textelite 89 (was 97), circles 31, stream-disk 29 (was 31), window3d 27 |
| global loaded more than once | 19/20 (was 20/20) | textelite 190 (was 536), wavc 125 (was 293), window3d 88 |
| memory-counter counted loop (missed `dbra`) | method-dependent, see below | method-dependent, see below |
| dead regfile store | 11/20 narrow, 20/20 broad (was 12/20) | window3d 11, circles 5, textelite 2 (first draft claimed 14/14/13) |
| redundant `lea` | 10/20 (confirmed) | textelite 16, window3d 6, stream-disk 5 |
| div+mod pair on the same dividend (A3) | **4 pairs in 3/20** (was 2/20) | adpcmbench x2, textelite, wavc - see the table in A3 |
| division by a non-power-of-two constant | 1/20 (confirmed) | adpcmbench: `divs.l #1000000` and `#1000` |

Division by a power-of-two immediate was also counted (4/20 files, 5 sites, all
`divs.l #256`: 3d 1, window3d 1, wavc 2, adpcmbench 1) but is no longer listed as an
opportunity: the fold is correct for unsigned, and a real signed division is the only
correct lowering. Only the non-power-of-two case is still worth anything.

Notes on the cells that changed:

- **`ext.b`, 19/20 -> 17/20.** The three files with zero are `ahimodes`, `izx0reader` and
  `rasters`. 19/20 only holds if `ext.w`/`ext.l` are included as well.
- **Globals loaded more than once, 20/20 -> 19/20 and much lower counts.** `hlaudio` is 0.
  The first draft's stream-disk figure of 273 is not reachable: that program has only 23
  direct `p8b_`/`p8w_` loads in total, and measures 14 under this criterion. The counting
  rule needs to be stated before these numbers are trusted, since the original detector is
  not recorded. A4's reference-program figure of 190 does reproduce.
- **Memory-counter loops is method-dependent.** Counting only the strict 4-instruction tail
  gives 14 loops in 6 files; including the `addq`+`bra` variant gives 29 in 12; a broad
  criterion that also counts library-internal counters gives 46 in 16. The first draft's
  "12/20, 27 loops" sits between these, and its per-file figures (textelite 6, stream-disk 4)
  are not reproducible as stated. The finding itself is solid for `3d.p8` (the tail is at
  `3d.asm:2397-2403`, and `dbra` appears only 5 times in the whole file, all in stdlib
  `memset`-family loops).

Cross-backend check of the IR-level findings (A1, A2): `examples/cx16/cube3d.p8` re-emits
`ldy p8v_i` 17 times, but that is the file total across 3 loop bodies (at most 13 in any one
body, not 17 as the first draft said); all 3 bodies do use the same 4-instruction
memory-counter loop tail. The `virtual` target IR shows the same `incm.b` memory counters in
`examples/virtual/sincos.p8` (8) and `bouncegfx.p8` (3), both confirmed exactly. The
redundancy is therefore not m68k-specific, but note that the *default* 6502 backend does not
consume the IR, so A1's fix (an `IRPeepholeOptimizer` pass) does not reach it; see A1.

One asymmetry worth remembering: the *size* of A1 is much larger on m68k than on 6502, and
that is not a compiler difference. On 6502 the index is already in zero page, so `ldy zp`
is 3 cycles; on m68k the same index costs 4 instructions including a regfile round trip,
and there is no zero page. Expect the payoff to be m68k-heavy, and note that the 6502 side
needs its own change in `codeGenCpu6502` rather than the shared IR one.

## 6. Suggested order

1. **A3's `divmod` items** - the `long` builtin plus the `/`+`%` fusion. Self-contained, no
   design decisions, three known call sites (four pairs) in the example set. The textelite
   fix is source-level and needs no compiler work, but note it requires the `10 as uword`
   cast and a two-target assignment, as shown in A3. Now first: it is the largest item still
   known to be cheap.
2. **B1** - small, local, low risk, and independent of allocation.
3. **A1** - only as an extension of `deduplicateAddressComputations` to the `loadx` form,
   preceded by the address-taken pre-pass that makes aliasing decidable. This used to be
   gated behind A2 on the assumption that A2 removed the index rebuilds structurally. A2
   demonstrably does not (the zero-extensions survive), so A1 should be sized on its own
   measured benefit: about 19% of frame instructions in the reference program.
4. **B2** - a stopgap by nature (see section 4): cheap, and it pays off until the register
   allocator lands. The scope-delimiter infrastructure it needs already exists.
5. **B3** - no longer a marshalling design question. Declaring the affected m68k library
   routines as `asmsub` with register slots is a source-level sweep; `math.sin8` is the known
   case. The permanent convention is still open and belongs with the stack-memory-model
   decision, not here.
6. **C1** - follows A1 and the same register-resident-element question; coordinate with the
   register allocator.
7. **A2** - now well down the list. It needs a sound structural fix for the un-isolated
   counter-init block before it can be attempted, and its ceiling is small (see A2). Do not
   relax the `instructions.size != 1` guard to force it; that is unsound and breaks the
   build and `3d.p8`.
8. **A4** - do not attempt as written. General forwarding was tried and reverted because it
   grows `.text` on a memory-backed regfile. Only extend the per-symbol pattern
   (`optimizeLoopCounters`) if more is wanted before the allocator; the rest waits for
   `m68k-register-allocation.md`.
9. **Magic-number division** - the one remaining arithmetic item, in A3. It is a much larger
   change than anything else on this list and affects only 1 of 20 examples, so it is last
   on value as well as effort.

Verification for each step: recompile the 20 amiga examples, re-run the per-region
instruction counts, and check `.text` size. Behaviour must be unchanged, so
`gradle build` plus the existing `codeGenM68k` tests (`TestCodegen.kt`,
`TestInstructionSelectionOptimizations.kt`) are the gate.
