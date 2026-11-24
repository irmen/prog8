# Better pointers: cheap memory stores for `constant + variable` addresses

Status: proposal / plan, verified against the compiler source at the time of
writing. The sections below are compiler-only: no source file changes and no
language changes are assumed or proposed. Appendix C contains the language-level
companion (memory regions), which does propose source-visible syntax.

Applies to: 6502-family targets (c64, c128, pet32, cx16). The problem described
here does not exist in the m68k and virtual backends; see section 2.5.

Verification note: every proposal in section 4 was checked against the working
tree by reading the code and by compiling test cases with the local build.
Three items turned out to be partially implemented already, and two were
misclassified in the first draft (P3 is not expressible as an assembly
peephole, P11 is not "write an inliner"). Appendix B lists the exact locations
and what already exists.

---

## 1. TL;DR

On a 6502, storing one byte at a runtime-computed address has exactly two cheap
forms: an 8-bit index against a constant base (`sta BASE,x`, 5 cycles) or a zero
page pointer (`sta (zp),y`, 6 cycles). Prog8's general memory-write syntax,
`@(base + expr)`, maps to neither, so it rebuilds a 16-bit address into a
scratch pair on every single store. That costs 26 to 29 cycles per store instead
of 5 to 10, and because the address is an expression rather than a value, it
cannot be carried across statements or loop iterations.

| id | change | kind | fraction of a drawn screen (measured) | verified state | effort |
|---|---|---|---|---|---|
| P1 | load a constant store value after the pointer build | codegen path | ~6% | IMPLEMENTED for numeric bases requiring pointer construction | done |
| P2 | hoist index and constant across consecutive absolute-indexed stores | asm peephole | applies to code using fixed-address arrays | IMPLEMENTED for repeated absolute-indexed stores | done |
| P3 | build the address once for two stores to different bases | codegen | ~7% | IMPLEMENTED for adjacent numeric-base stores with a shared word index | done |
| P4 | `@(const + ubyte_expr)` emits absolute indexing | codegen rule | closes the last fast-form gap | IMPLEMENTED for numeric bases with byte-variable indexes | done |
| P5 | pass three byte arguments in A/X/Y | calling convention | ~3-4% | two-argument cliff | 2-3 days |
| P6 | prefer inline shift-add over a library call for `*const` | codegen policy | ~1% | deliberate size choice, no policy knob | hours |
| P7a | address strength reduction within a loop | IR / Simple AST | ~20% for bulk fill loops | not present | 1 week |
| P7b | the same across a call boundary | IR / Simple AST | ~10-15% for cell-by-cell drawing | not present, needs P11 or a cache | 1-2 weeks |
| P8 | branch-chain compound conditions | codegen (port from IR) | ~1% here, general | not present, algorithm exists in the IR backend | 2-5 days |
| P9 | lint: warn on scratch-pointer stores | diagnostics | locates the problem in user code | IMPLEMENTED for slow 6502 direct stores | done |
| P10 | split loops at page boundaries (stretch) | Simple AST / IR | up to ~30% for bulk fills | not present | weeks |
| P11 | inline small leaf subroutines | optimiser | ~8-10%, enables P7b | inliner exists, blocked by a 6502 parameter limit | 1 week |

The single most important correction from verification: P7 (both halves) is the
general solution for removing repeated address rebuilds across wide-offset loops
and calls. P4 removes the rebuild for byte indexes against numeric bases, while
P1 removes the save/restore around constant stores that still need a pointer build.
P1, P2, P3, P4, and P9 are implemented.

---

## 2. Problem statement

### 2.1 The shape

Memory-mapped hardware, buffers and lookup tables are all written the same way
in 6502 programs: a constant base plus a runtime offset.

```prog8
; independent of any particular program
sub plot(ubyte x, ubyte y, ubyte value) {
    uword offset = ROW_START[y] + x
    @(SCREEN_BASE + (offset)) = value
}
```

For the C64 that is `SCREEN_BASE = 1024` for the text matrix and
`COLOUR_BASE = 55296` for colour RAM, and any drawing routine that walks a
region ends up doing this twice per cell, once per array.

### 2.2 Why the 6502 is awkward here

The instruction set offers three memory addressing modes that involve a runtime
value:

| instruction form | cost | requirement |
|---|---:|---|
| `sta abs,x` | 5 | index must be 8-bit |
| `sta abs,y` | 5 | index must be 8-bit |
| `sta (zp),y` | 6 | a complete pointer must already be in zero page |

`@(const + expr)` matches none of them: the base cannot be folded into an 8-bit
index register, and because the base is inside the expression rather than in a
pointer, there is nothing in zero page to store through. The backend therefore
materialises the complete address into a scratch pair immediately before the
store. That is correct, but it is the most expensive thing the store does, and
it happens once per store.

### 2.3 Cost model

Measured by compiling and counting emitted instructions, per store:

| source form | emitted addressing | cycles |
|---|---|---:|
| `@(const + (uword))` | scratch pair rebuild | 26 (variable in ZP), 28 (variable in BSS) |
| `@(const + (ubyte))` | scratch pair rebuild plus `pha`/`pla` | 29 |
| `@(zp_cursor)` | `sta (zp),y` | 10 |
| `@(zp_cursor + small)` | `sta (zp),y` with `ldy #small` | 8 |
| fixed-address array, 8-bit index | `sta BASE,y` | 11, or 5 if index and value are hoisted |
| ideal | `sta BASE,x` | 5 |

Two consequences that are not visible from the source:

1. **An 8-bit offset does not help.** `@(BASE + (ubyte_var))` is the slowest
   form in the table, because the value has to survive the pointer rebuild on
   the stack.
2. **A small offset does not help either, unless the base is inside the
   cursor.** `@(zp_cursor + 1024)` falls back to the scratch path. Only a bare
   `@(zp_cursor)` or an offset below 256 stays on the cheap path.

### 2.4 The second-order effect

Because the address is an expression and not a value, it cannot be reused. Two
statements that store to the same offset in two different arrays build the
address twice:

```
    ; @(SCREEN_BASE + (offset)) = value
    lda  offset / sta W2 / lda offset+1 / clc / adc #>SCREEN_BASE / sta W2+1
    ldy  #0 / lda value / sta (W2),y

    ; @(COLOUR_BASE + (offset)) = colour     ; 26 cycles, same offset
    lda  offset / sta W2 / lda offset+1 / clc / adc #>COLOUR_BASE / sta W2+1
    ldy  #0 / lda colour / sta (W2),y
```

And in a loop, the same happens every iteration even though the offset only
moved by one:

```
    ; while walking a region, 1000 iterations
    lda  offset / sta W2 / lda offset+1 / clc / adc #>BASE / sta W2+1
    ldy  #0 / lda #32 / sta (W2),y        ; 26-29 cycles to write one byte
    inc  offset / ...
```

The equivalent hand-written or C-generated code keeps one pointer and increments
it, so 1,000 stores cost about 6 cycles each instead of 26.

### 2.5 Target scope: 6502 versus m68k and virtual

This whole problem is an artefact of the 6502's 8-bit index registers and its
lack of base-plus-index addressing. The other backends do not have it.

**m68k.** The same source shape compiles to an indexed memory operand with the
constant as a displacement, from a single `add.w` or directly in the
instruction:

```
    ; @(CONST + v) = 32
    move.b  #32,d2
    moveq   #0,d0
    move.b  v,d0
    movea.l d0,a2
    add.w   #CONST,a2
    move.b  d2,(a2)

    ; @(CONST + (w)) = 32   inside a loop
    move.b  #32,d2
    move.w  w,a2
    move.b  d2,(CONST,a2)        ; base plus index, one instruction
```

No zero page, no scratch pair, no `pha`/`pla`, and 32-bit addresses with eight
data and eight address registers. A 16-bit offset costs a `move.w`/`movea`
pair, not a rebuild. The residual inefficiency on m68k is only that the backend
recomputes `move.w w,a2` per store instead of strengthening into `(a2)+` across
a loop body: worth a few cycles per store, not a 2.6x factor. That is tracked
separately in the m68k codegen plan (`ideas/m68k-better-codegen.md`).

**virtual.** The IR is a single opcode with base plus displacement:

```
storei.b r8.b,[r9.p+16384]
```

Again no zero page, no 8-bit index limit, and byte arrays may be large. Runtime
cost is interpreter dispatch, identical for every address form.

**cx16 / 65C02.** Same 8-bit index restriction, but `sta (zp)` without an index
and `stz` exist, and zero page is more plentiful, so the workarounds are
cheaper. The analysis still applies.

Conclusion: the plan targets the 6502 backends. Nothing in it should change
m68k or virtual codegen, and those backends should not be used as evidence for
or against any proposal here.

Note also that there are two 6502 backends in the tree: the default one
(`codeGenCpu6502`, AST based, selected in `Compiler.kt:929-930`) and an
experimental IR based one (`codeGenNew6502`, enabled with `-newcodegen`,
documented as not for production). Their instruction sets differ: the new
backend already has a `storex.b reg,[BASE+idx]` instruction that emits
`sta BASE,x`, but it also has no register allocation and routes everything
through a memory register file. Every proposal below states which backend it
targets; all of them target the default backend.

### 2.6 The same access, four source forms

The shape can be written in several ways in Prog8 today, and the cost differs
by 3x depending on how the base is expressed. Measured on the C64 target,
storing one byte at `base + index`:

| source form (index is a byte) | emitted addressing | cycles |
|---|---|---:|
| `uword base` that landed in zero page, `base[i] = v` | `ldy i / lda #v / sta (base),y` | 10 |
| same, but the variable is outside zero page | copy to scratch, then `sta (scratch),y` | 22 |
| `@(BASE + (i)) = v` with a numeric constant base | scratch rebuild plus `pha`/`pla` | 29 |
| `base[w] = v` with a 16-bit index | scratch rebuild, index low byte in Y | 26 |

Consequences:

- **A cheap form does exist today**, but it depends on the base being a
  *variable* that the allocator happened to put in zero page. That is a
  non-local, luck-dependent property: `uword` variables are promoted when their
  usage score is high enough (`VariableAllocator.kt:98-116`), and pointer-typed
  declarations are promoted outright (`AstPreprocessor.kt:308-317`). Nothing in
  the source guarantees it, and when it is not promoted the same statement costs
  22 cycles instead of 10.
- **A numeric constant base is never in that set.** `@(SCREEN + (i))` always
  takes the scratch path, so the most obvious spelling of an access that could
  be three instructions is the most expensive one.
- **A byte index is cheaper than a word index**, because the word form cannot
  use Y as the index and rebuilds the pointer even when the value is known to
  fit in a byte.

This changes the framing of the problem in two ways. First, there is no need to
invent a new idiom for users: holding the base in a `uword` (ideally `@zp`) and
indexing it with a byte is already cheap. Second, the remaining complaint is not
"no cheap form exists" but "the cheap form is fragile and the obvious form is
not it", which is exactly what P4 and P9 address.

---

## 3. How other implementations avoid it

All of these are compiled for the same CPU, so the constraint is shared. What
differs is whether the language gives the address a representation the backend
can keep.

| implementation style | how a cell is written | per-store cost |
|---|---|---:|
| hand-written assembly | `sta BASE,x` over page spans, or `sta (ptr),y` plus `inc ptr` | 5-6 |
| C (pointer value) | `ptr[i] = ch`, `*ptr++ = ch`; the same syntax covers a constant base and a variable pointer | 5-10 |
| Pascal-like with pointer variables | `screenPtr[0] := ch`, `screenPtr[i] := ch` | 6-10 |
| language with page dispatch in source | switch on the high byte, then index with the low byte | 5 plus dispatch |
| inline assembly escape hatch | hand-written store kernel | ~15 |
| **Prog8, general form** | `@(BASE + (offset))` | **26-29** |

Three properties the others have:

1. **One syntax spans both cases.** In C, `screen[pos]` is valid and sensible
   whether `screen` is a constant address or a variable pointer, and the
   compiler picks the addressing mode. In Prog8 the syntax chooses the codegen:
   `@(addr)` is always the slow path, `&ubyte[256]` is always the fast path but
   cannot span more than one page.
2. **The address is a value**, so it survives across statements and iterations.
   `ptr[i]`, `pos++`, `*text++` all mean "keep the computed address". Prog8's
   `@(...)` re-materialises it per statement; the offset variable can only ever
   be an addend. Nothing in the source can express "keep this address".
3. **Optimizing passes exist** in the C toolchains that turn
   `screen[pos] = ch; pos++` into a pointer induction variable. Prog8 has no
   such pass, so the human has to write `sp += 1` by hand.

Phrased as a compiler statement:

> `@(base + expr)` is neither a pointer value (so it cannot be carried in a
> register or a zero page cell across statements) nor an array index (so it
> cannot use absolute indexing). It exists only inside the statement that uses
> it, and must therefore be constructed from scratch every time.

Every proposal in section 4 either makes that construction cheaper, or gives the
address one of those two representations internally.

---

## 4. Proposed compiler work

Each proposal states where the code lives today, what it does now, what it
should do, the expected saving, the safety conditions, and how it would be
tested. Examples are generic; `BASE`, `BASE2`, `OFFSET` and `VALUE` stand for
any constant base, offset variable and value.

### P1. Load a constant store value after the pointer build (IMPLEMENTED)

Target: default backend.

Where: the store value path is
`PrimitiveAssignmentsGen.storeRegisterAInMemoryAddress` (:2013), which dispatches
on the address shape (:2025-2111) and reaches
`AssignmentAsmGen.storeByteInAToAddressExpression` (:658).

Original failure: two fast paths already existed, and both only covered the
case where the base was a pointer *variable*:

- `AssignmentAsmGen.kt:667-692`: when the address expression is
  `identifier + index`, a constant value emits the pointer setup first and then
  `lda #VALUE`, deliberately avoiding a register round trip. A helper,
  `isConstReloadableAddress` (:650-656), is the predicate that decides whether an
  address expression is capable of this; before P1 it required
  `left is PtIdentifier` and a byte or word index.
- `PrimitiveAssignmentsGen.kt:2089-2105`: the same idea for `pointer + constant`
  offsets, with the comment "need to load constant after setting up pointer, not
  before".

Neither matched a **numeric constant base**. `isConstReloadableAddress` returned
false, `saveA` was therefore true (`PrimitiveAssignmentsGen.kt:2020-2021`), and
the code fell through to the generic tail of `storeByteInAToAddressExpression`
(`AssignmentAsmGen.kt:731-734`):

```
    if(saveA) asmgen.out("  pha")
    assignExpressionToVariable(address, "P8ZP_SCRATCH_W2", DataType.UWORD)
    if(saveA) asmgen.out("  pla")
    asmgen.storeAIntoZpPointerVar("P8ZP_SCRATCH_W2", false)
```

which is where `lda #VALUE / pha / ... / pla / sta (W2),y` came from.

Implemented: `isConstReloadableAddress` now accepts a numeric `PtNumber` base
with an unsigned-word offset. When that address still needs a full pointer build,
`storeByteInAToAddressExpression` computes the address into `P8ZP_SCRATCH_W2`,
then loads the constant and stores it. This avoids the `pha`/`pla` pair. The
existing callers for 65C02 `stz` and constant stores use the widened predicate.
P4 handles the byte-index case directly; P1 covers numeric-base cases that still
need a pointer, such as a word index.

```
        ; compute the complete address into SCRATCH
        lda  #VALUE
        ldy  #0
        sta  (SCRATCH),y
```

Saving: 7 cycles per affected constant store by removing `pha`/`pla`. A
1,000-iteration fill with two arrays does this twice per iteration: about 14,000
cycles, 6% of screen construction in the measured workload. For non-constant
values the existing `tax`/`txa` form stays.

Conditions: none beyond "the value is a constant". The value expression must not
read the scratch pair.

Tests: `TestExecution6502` checks optimized and unoptimized C64 builds, verifies
that the constant is loaded after pointer setup with no `pha`/`pla`, and checks
the written memory byte.

### P2. Hoist index and constant across consecutive absolute-indexed stores (IMPLEMENTED)

Target: default backend, assembly peephole.

Where: `optimizeSameIndexedConstantStores` in `AsmOptimizer.kt`, registered in
the 14-line peephole passes.

Original state: the existing index-reload pass could not match this shape because
`lda #IMM` sits between `ldy` and the store:

```
        ldy  i
        lda  #$20
        sta  PAGE0,y
        ldy  i
        lda  #$20
        sta  PAGE1,y
```

Implemented: the pass matches repeated `ldy IDX / lda #IMM / sta BASE,y`
sequences within a 14-line window. It removes the later `ldy` and `lda` only
when the index and immediate operands match and intervening instructions preserve
A. The shared `canDropIndexReload` guard keeps the optimization from crossing
Y changes, control flow, labels, writes to the index operand, or volatile IO
reads. Indirect stores are treated conservatively as possible aliases.

Saving: measured on a four-page clear, 101 to about 53 cycles per iteration,
i.e. 25,000 to 13,500 cycles for 1,000 cells, equal to hand-written cost per
cell. Note this only helps code that already uses fixed-address arrays; the
general `@(BASE + offset)` shape is not affected.

Conditions: store order is preserved (this is load elimination only). Removal is
by line index and applied in reverse, so each removed line must be emitted once.

Tests: `TestAsmOptimizer.kt` covers a three-store run, volatile IO indexes,
intervening `iny`, possible aliasing via indirect stores, and differing
immediates. Store order is unchanged.

### P3. Build the address once for two stores to different bases (IMPLEMENTED)

Target: default backend codegen.

Where: adjacent-statement pairing in `AsmGen.translateStatements` and
`AssignmentAsmGen.tryTranslateMemoryStorePair`.

Implemented: consecutive direct byte stores with a shared `uword` identifier
offset and numeric bases at or above `$0100` share the first computed address.
The second base difference adjusts the scratch pointer by one byte when its low
byte is zero, or by the full word otherwise. Calls and non-matching statement
shapes retain ordinary address lowering. Simple variable and literal byte
values are accepted; complex values fall back to the regular path.

For the screen/colour pair:

```
        lda  OFFSET / sta SCRATCH / lda OFFSET+1 / clc / adc #>BASE / sta SCRATCH+1
        ldy  #0 / lda VALUE / sta (SCRATCH),y
        lda  SCRATCH+1 / clc / adc #>(BASE2-BASE) / sta SCRATCH+1   ; high byte only
        lda  VALUE2 / sta (SCRATCH),y
```

43 cycles for the pair instead of 52, and fewer when the second base's low byte
is zero (then the adjustment is a three-instruction high-byte add). For a
general base difference, a 16-bit add still beats a rebuild.

Saving: about 9 cycles per cell pair, roughly 7% of screen construction.

Conditions: the statements must be adjacent, use the same word-index variable,
and have simple values. The `BASE >= $0100` guard avoids confusing the common
zero-page-pointer-plus-small-offset form with a numeric base. With P4
implemented, byte-sized offsets to numeric bases continue to use absolute
indexing instead.

Tests: `TestExecution6502` checks the high-byte-only adjustment, the full
16-bit adjustment when low bytes differ, and fallback across an intervening
call. Both optimized and unoptimized builds are simulated for the primary pair.

### P4. `@(const + ubyte_expr)` uses absolute indexing (IMPLEMENTED)

Target: default backend.

Status: IMPLEMENTED in the default 6502 backend.

Where: `AsmGen.pointerViaIndexRegisterPossible` (:2256-2290) already detects
`word|pointer + ubyte` and returns the pair. The array store path uses it:
`PrimitiveAssignmentsGen.kt:2049-2080` emits `ldx offset / sta varname,x` for
`addressOfIdentifier != null`. The read side has an analogous fast path in
`AssignmentAsmGen.kt:574-600`.

Original failure: for a numeric base the detection succeeded, but the callers
immediately cast the left side to `PtAddressOf` and require an identifier,
which a `PtNumber` is not. Control then fell through to
`tryOptimizedPointerAccessWithA` and finally to the scratch rebuild.

Implemented: both the byte read and store paths now recognize a numeric
`PtNumber` base and byte-index identifier, and emit absolute indexed access:

```
        ldx  OFFSET
        lda  BASE,x
        ; or sta BASE,x when storing
```

The store value is already in A or is loaded as needed. More complex byte-index
expressions retain the existing fallback. Bases below `$0100` also retain the
pointer path: `zp,X` wraps within zero page instead of carrying into `$0100`.

The absolute-indexed store itself is 5 cycles; loading X and the store value
adds to the complete source-level cost. In the small read/write test, the
generated code shrank from 170 to 154 bytes. This does not help the general
word-offset shape, but closes the byte-index gap without requiring a pointer
variable or a lucky zero page promotion.

Conditions: 8-bit index, numeric constant base at or above `$0100`, and the
natural 64K wrap is acceptable (which is what absolute indexing does).
`BASE + 255` must not cross into a region the caller does not expect.

Tests: `TestExecution6502` checks generated reads and stores plus memory results
for optimized and unoptimized C64 builds. It also checks that a zero-page base
whose byte index crosses `$00ff` retains 16-bit carry behavior.

### P5. Pass three byte arguments in A/X/Y

Target: default backend.

Where: the gate is `FunctionCallAsmGen.optimizeIntArgsViaCpuRegisters`
(:29-38); the call side is `useCpuRegistersForArgs` (:212-244, which throws for
more than two at :242); the callee prologue is in `ProgramAndVarsGen.kt:564-594`
(throws at :592). Argument staging into static cells is
`argumentViaVariable` (:308-322). The current rule is documented in
`docs/source/technical.rst:407-432`.

Current state, verified: one parameter uses A (or AY), two byte parameters use
A and Y, and anything else stages every argument into the subroutine's static
parameter cells at 8 cycles each. A four-argument helper therefore costs about
32 cycles of staging per call plus reloads inside the callee.

Proposed: add a third register-only form: up to three byte-sized parameters in
A, X and Y. A fourth and later parameter continues to use the cell. Only three
places change, and only these three:

1. the gate in `FunctionCallAsmGen.kt:29-38`;
2. the call-side loader `useCpuRegistersForArgs` (:212-244), including the
   register save/restore ordering already present for the two-argument case;
3. the callee prologue in `ProgramAndVarsGen.kt:564-594`.

X is the only free third register, and the caller must preserve it if the call
is inside an expression that uses X (`FunctionCallAsmGen.kt:43-44` documents
this constraint for the existing path). Keep the restriction to byte-sized
parameters; mixed word+byte stays on the current path.

Saving: about 16 cycles per call, 3-4% of screen construction in the measured
workload where a store helper is called roughly 600 times per screen build.

Conditions: ordinary `sub` only. `asmsub` and `extsub` use a separate path driven
by declared registers (`argumentsViaRegisters`, :270-306) and are unaffected.

Tests: assembly-output tests for 3 byte args (A/X/Y) and for 3 args where one is
a word (must fall back to cells); a `simulate()` test that runs a three-argument
subroutine and asserts the parameter values.

### P6. Prefer inline shift-add over a library call for `*const`

Target: default backend. **Corrected: this is a deliberate size choice, not a
missing optimisation.**

Where: four emitters make the same decision:

- `BinaryOpAssignmentsGen.kt:457-536` (`optimizedMultiplyExpr`),
- `AugmentableAssignmentAsmGen.kt:2247-2257` (byte, in-place `*=`),
- `AugmentableAssignmentAsmGen.kt:2561-2596` (word, in-place),
- `PointerAssignmentsGen.kt:866-873` and :1208-1229 (index scaling).

Current state, verified: for a byte multiply the order is (1) constants in
`optimizedByteMultiplications` (`AsmGen.kt:56`:
`3,5,6,7,9,10,11,12,13,14,15,20,25`) go to `jsr prog8_math.mul_byte_N`; (2)
powers of two become shifts; (3) other constants up to the instruction budget
become an inline shift-add via `byteShiftAddExpansion`
(`codeCore/.../ConstMultiply.kt:25`, budget 24 instructions, :18); (4) the rest
call the generic `multiply_bytes`. So `x * 10` always takes the `jsr` even
though an inline expansion for 10 exists and is cheaper at runtime.

Proposed: no new expansion is needed. Either take the inline expansion for the
13 table constants when the call site is in a loop, or add a compiler option
that prefers speed over the per-call-site size saving. Note there is currently
no size/speed flag anywhere in the 6502 backend: the only `options.optimize`
use gates the peephole pass (`AsmGen.kt:124-132`).

Saving: 8-10 cycles per multiply (JSR plus the routine costs about 34, the
inline form about 26), for about 7 extra bytes per site. Confined to collision
and index arithmetic, so small in this workload.

Conditions: the expansion must respect the existing instruction budget for
larger constants; only the 13 table constants are candidates.

Tests: extend the existing multiply codegen tests; assert no `jsr
prog8_math.mul_byte_10` in the preferred-speed mode and that the inline sequence
is complete.

### P7a. Address strength reduction within a loop

Target: default backend. Kind: optimiser pass (Simple AST or IR), not a
peephole.

Where: new pass, plus codegen support for "address in a zero page slot". The
existing zero page machinery is `VariableAllocator.kt:49-121` and the
pointer-access helpers `AsmGen.kt:354-429` (`loadByteFromPointerIntoA`,
`storeAIntoPointerVar`), which already choose between a direct `(zp),y` and a
copy into `P8ZP_SCRATCH_PTR` based on `isZpVar`.

Pattern:

```prog8
uword i = 0
while (i < 1000) {
    @(BASE + (i)) = 32
    @(BASE2 + (i)) = 1
    i += 1
}
```

Recognition: a variable is used only as `const + v` in memory accesses and is
updated only by constant amounts. Then `const + v` is an induction variable and
can be replaced by a hidden zero page pointer that is bumped:

```
        lda  #<BASE / ldy #>BASE / sta PTR / sty PTR+1     ; once
loop:   lda  #32 / ldy #0 / sta (PTR),y
        lda  #1 / sta (PTR+delta),y                        ; or a second pointer
        inc  PTR                                           ; or add the stride
        ... loop control ...
```

Current per iteration is about 85 cycles for the two stores plus bookkeeping;
optimal is about 44. Measured on a 1,000-cell clear: 90,000 cycles, of which
roughly 46,000 is recoverable.

Design constraints, verified:

- **Zero page is the scarce resource.** On C64 with `basicsafe` the free pool is
  14 bytes with only three 2-byte slots (`$9e`, `$b0`, `$be`) beyond the scratch
  area (`TestZeropageAllocation.kt:119`, :181-185). A per-routine pointer is not
  affordable. Use one shared, reloadable cache slot, or a single slot per
  active loop nest, and fall back to the current code when no slot is free.
- **Promotion is by declared type, not use.** `AstPreprocessor.kt:308-317`
  auto-promotes only *pointer-typed* declarations; a plain `uword` used as an
  indirect pointer is placed by the `DONTCARE` heuristic (usage count weighted
  by loop depth, `VariableAllocator.kt:137-170`). If the pass introduces a
  hidden pointer, it should be allocated as `PREFER_ZEROPAGE` and counted in the
  same budget.
- The pointer must be routine-owned (a normal variable) so calls cannot clobber
  it; the shared `P8ZP_SCRATCH_*` pairs cannot be cached across statements.
- Invalidate on any write to the variable that is not a constant add, and on
  control flow entering the loop other than at its head.

Where: a new pass after the AST optimisers, or an extension of the existing
optimiser modules; it needs the loop structure, so Simple AST is the natural
level, and codegen support for addressing through a chosen ZP slot.

Tests: `simulate()`-based tests are the right tool (they run generated code and
assert on memory); use a loop that writes a region and assert both the written
bytes and that the generated assembly contains the pointer increment and no
per-iteration address rebuild. Also test invalidation: a write to the index
variable inside the loop must fall back to the current form.

### P7b. The same idea across a call boundary

Target: default backend. Kind: optimiser pass or codegen cache. Harder half.

In idiomatic code the address is usually computed *inside* a small store helper
while the loop that walks the region lives in the caller:

```prog8
; helper: computes the address for every cell
sub plot(ubyte x, ubyte y, ubyte value) {
    uword offset = ROW_START[y] + x
    @(BASE + (offset)) = value
}

; caller: walks a row, one call per cell
while (x <= x2) {
    plot(x, y, VALUE)
    x += 1
}
```

Every call rebuilds an address that is exactly one greater than the previous
one, but no loop in the helper's body knows that. Two routes, both verified as
feasible:

- **P11 first.** Once the helper is inlined into the caller's loop, the address
  expression is in the loop body and P7a applies unchanged. Today P11 is blocked
  by the one-parameter limit for 6502 (see P11), so lifting that limit comes
  first.
- **Or a small cache in the helper.** Remember the last address inputs and the
  pointer built for them; if the next call differs by a small constant in one
  coordinate, adjust the pointer instead of rebuilding. Two compares pay for
  themselves immediately; any non-matching call invalidates. This needs no
  inlining and covers row and column walks.

Saving: 25,000 to 35,000 cycles on a 1,800-cell screen build, with the upper end
requiring the inliner.

Conditions: the cache must be routine-owned state; invalidation must be
conservative. The address-adjustment must handle the case where the row changes
(stride 40) as well as the +1 column walk.

Tests: as P7a, plus a test that alternates two different rows to prove the cache
invalidates rather than producing a wrong pointer.

### P8. Branch-chain compound conditions

Target: default backend. **Corrected: port an algorithm that already exists in
the same repository for the IR backend.**

Where, current behaviour verified:

- `BinaryOpAssignmentsGen.optimizedLogicalExpr` (:1617-1765) always produces a
  0/1 value in A, materialising each comparison with `lda #1 / jmp / lda #0`.
- `IfElseAsmGen.translateIfByte` intercepts logical operators on purpose
  (:290-307) and then branches on that value with `beq`/`bne`.
- The branch-chained machinery exists per comparison
  (`translateIfByte/Word/Long/Float`, `translateIfElseBodies` :252-269,
  `translateJumpElseBodies` :271-285) but throws on non-comparison operators
  (:339-340).
- `while` has no codegen node: it is desugared to an `IfElse` with the condition
  wrapped in `not` (`CodeDesugarer.kt:188-201`), and `not` is handled by
  materialising the child (`IfElseAsmGen.kt:55-67`). `invertCondition` only
  inverts comparisons (`AstExpressions.kt:2318-2329`, `Operators.kt:9-18`), so a
  compound condition always arrives as `not(or(...))` and always takes the
  materialising route.

Proposed: port `IRCodeGen.translateCondition` (:1501-1542) to `IfElseAsmGen`.
That function already does the right thing recursively:

```
if operator == "or"  -> branch left to the body label, then translate right
if operator == "and" -> branch left to a skip label, then translate right
if comparison        -> existing per-width comparison emitters
if prefix == "not"   -> translate the child with the true/false labels swapped
```

The 6502 side needs a `translateCondition(condition, onTrue, onFalse)` that
reuses the existing `translateIfByte/Word/Long/Float` emitters for the leaves and
handles `or`/`and`/`not` recursively. Value contexts keep using
`optimizedLogicalExpr`; only condition contexts change.

Saving: about 100 cycles per evaluation of a three-term condition. Decimal
conversion loops evaluate such a condition ten times per digit position, so this
is worth a few thousand cycles in the measured workload and more in programs
with many compound `if`s.

Conditions: flag lifetime. The existing leaf emitters already handle flag use
correctly; a recursive wrapper must not insert anything between a comparison and
its branch. Only branch, never reorder.

Tests: assembly-output tests for `or`, `and`, nested combinations, and `not` over
a compound condition, asserting that no `lda #1`/`lda #0` materialisation appears
in a condition context. `simulate()` tests for truth values, especially
short-circuit cases with side effects in the right operand.

### P9. Lint warning on scratch-pointer stores (IMPLEMENTED)

Target: diagnostics.

Where: `AstChecker.visit(DirectMemoryWrite)` in
`compiler/src/prog8/compiler/astprocessing/AstChecker.kt`. Infos go through
`errors.info(msg, position)` and are gated by `-ignorefootguns`.

Original state: there was no lint for slow `@(...)` stores or address arithmetic.

Implemented: on 6502-family targets, direct stores with a dynamic `+` address
offset are reported when the suggested fast form can actually be used. The
checker skips library code, constant-only addresses, the P4 absolute-indexed
form, and byte-indexed accesses through a base known to be in zero page, in
either operand order. It also stays quiet when the base is only *possibly*
allocated to zero page (the real allocation happens later, during codegen) or
when the index is a word whose range is not known to fit a byte.

The diagnostic is:

> slow store; use a zero-page base with a ubyte index if possible

It is advisory only; it does not change code generation.

Tests: `TestAstChecks` verifies infos for explicitly non-zero-page pointer bases
with a byte index, and no info for the P4 byte-indexed path, constant offsets,
a required-zero-page cursor, the reversed byte-plus-pointer form with a
possibly zero-page pointer, or a word index that cannot be narrowed safely.

### P10 (stretch). Split loops at page boundaries

Target: default backend. Kind: Simple AST / IR pass.

For `@(BASE + v)` where `BASE` is constant and `v` is an induction variable
with a known range, split the loop into segments in which `BASE + v` keeps a
constant high byte. Within a segment the store becomes `sta BASE_PLUS_PAGE,x`
with the low byte in X, 5 cycles.

For a 1,000-cell clear this reproduces the hand-written structure: four spans,
one index register, four stores per iteration. 90,000 cycles become roughly
14,000.

This is the transformation that removes the need for the page-array idiom
entirely, so it is the one that puts general Prog8 source at hand-written cost
for bulk fills. It needs range information for the induction variable (the loop
bound is usually a constant) and must preserve store order. Consider it after
P7a.

Tests: as P7a, plus boundary cases where the range starts mid-page or spans a
single page, and a case where the bound is not a constant (must fall back).

### P11. Inline small leaf subroutines

Target: optimiser. **Corrected: an inliner exists and works; the limitation is a
6502-only parameter count restriction.**

Where: `codeOptimizers/src/prog8/optimizer/Inliner.kt` (registration and
eligibility at :53-186; per-call-site checks at :271-346), invoked from
`optimizeAst()` via `Extensions.kt:60-62`, which skips it when optimisations are
disabled. `inline` is a real language keyword (grammar :32, `sub` :362,
`asmsub` :379; parser sets it at `Antlr2KotlinVisitor.kt:639`/`:684`).

Current state, verified:

- Auto-inline requires: not an `asmsub`, **at most one parameter**, no register
  parameters, no locals, one body statement (or two where the second is an empty
  return), no recursion (:166-186).
- More than one parameter is only supported on m68k (:283-319). On 6502 an
  explicit `inline` on a multi-parameter sub is therefore still rejected at the
  call site.
- Statement call sites additionally require "one body statement, optionally
  followed by empty return" (:481-490).
- `inline asmsub` is different: it is copied literally at the call site
  regardless of optimisation (`FunctionCallAsmGen.kt:78-85`).

So a small store helper with three or four byte parameters can never be
inlined today, on either the automatic or the explicit path.

Proposed: lift the one-parameter restriction for 6502. Since P5 establishes
A/X/Y as the argument registers, inlining a three-byte-parameter helper is
straightforward: substitute the arguments for the parameters, and require the
argument expressions to be side-effect free (the m68k path already has the
machinery and the checks at :283-319). Keep the single-body-statement rule for
the explicit `inline` keyword at first; relax it later if needed.

Saving: about 8-10% of screen construction directly (two staged calls per cell in
a redraw loop), and it is the precondition for the upper range of P7b.

Conditions: no recursion, no address-taken subroutines, and a size cap. Note
that `TestCompilerOnExamples` asserts exact output sizes per example
(`compiler/test/TestCompilerOnExamples.kt:43-51`), so inlining more code will
shift those values; expect to update them and attribute each delta.

Tests: inliner unit tests for 2- and 3-parameter subs on 6502, for a call site
with a side-effecting argument (must not inline), and for the existing
single-parameter paths (must not regress).

---

## 5. Ordering and expected outcome

Percentages are of the measured screen-construction cost in a representative
workload: a roughly 5 KB C64 program that writes about 1,800 cells per screen
build. Estimates assume the changes land as described; only the reference points
are measured.

| step | changes | expected effect | risk |
|---|---|---|---|
| 0 | today | 100% (reference) | - |
| 1 | P9 | no change; the problem becomes visible | none |
| 2 | P1, P4 | about 12% faster | low, local codegen |
| 3 | P3 | up to 7% | low |
| 4 | P2 | nothing for `@(...)` code; large for array-based drawing | low |
| 5 | P8 | about 1-2% here, more in condition-heavy code | low |
| 6 | P5 | about 4% | medium: calling convention |
| 7 | P7a | about 20% faster (bulk fill loops) | medium: ZP budget |
| 8 | P11 | about 8-10% faster, opens P7b | medium: inliner rules |
| 9 | P7b | about 12% faster again (cell-by-cell drawing) | medium |
| 10 | P10 | a further 20-30% on bulk fills (stretch) | high |

Cumulative if everything except the stretch lands: roughly 45-50% faster, with
the unchanged source. The hand-rewritten equivalent measured 2.0x, so the two
numbers are consistent.

Steps 1 to 4 are local and independently testable, and should be done first
because they are cheap and carry no design risk. Steps 7 to 9 are the ones that
matter: they give the address a representation, which is the only way the
difference becomes general rather than case-by-case.

---

## 6. Verification method

Cycle-exact, no instrumentation in the program, cheap enough to run per commit:

1. Compile with the target, keeping the generated `.asm` and the label file
   (`--vice-labels`).
2. Start an emulator with the binary monitor enabled and CPU history of at least
   one entry: `x64sc -autostart prog.prg -binarymonitor
   -binarymonitoraddress ip4://127.0.0.1:PORT -monchislines 64`.
3. Set stop checkpoints (command 0x12) on the routine to measure and on the
   first routine executed after it.
4. Resume, set up the state the fixture describes, read the clock, resume to the
   second stop, read the clock again. The delta is the exact inclusive cycle
   count, including nested calls and `rts`.
5. To compare observable behaviour, dump the affected memory ranges at the
   second stop (screen matrix and colour RAM for a C64 workload) and diff
   against the reference build.

The clock comes from the CPU history command (0x86, count 1), which returns the
64-bit absolute CPU clock of the most recent instruction. Call counts come from
hit counters on non-stopping checkpoints in the same run.

Acceptance criteria per proposal:

- observable memory (screen and colour RAM) byte-identical to the previous
  build at the fixture's stop point;
- no regression in the other fixtures of the workload (input handling, single
  cell moves, collision checks, interval computation);
- the generated `.asm` for a fixed source stays stable enough to diff;
- `gradle build` passes, and any change to `TestCompilerOnExamples` expected
  sizes is attributed (rebuild with the change reverted and diff the two
  generated `.asm` files, as the project notes recommend).

Additional requirement for P5, P7 and P11: they change calling conventions,
optimiser behaviour, or add compiler-managed state, so they need the full test
suite, not just this workload.

---

## 7. Risks, and open questions

- **Store order and side effects.** Every proposal here is load elimination,
  common subexpression elimination, or address strength reduction. None of them
  reorders stores. Keep it that way: stores can target hardware registers whose
  order matters, and there is no `volatile` qualifier to protect them. The
  existing asm passes enforce this with `machine.isIOAddress` and
  `looksLikeIOAddress`; new passes must use the same guards.
- **Zero page pressure is the binding constraint for P7.** 14 free bytes on C64
  with `basicsafe`, only three 2-byte slots beyond scratch. Prefer one shared
  reloadable cache slot over per-routine state, and fall back cleanly when the
  pool is exhausted. Losing a promotion is expensive: moving two cursors from
  zero page to BSS costs 12% on the measured workload.
- **Two 6502 backends.** The experimental `-newcodegen` backend already has a
  `storex` instruction that emits absolute indexed stores, but no register
  allocation. Decide explicitly whether a proposal targets the default backend
  (all of the above do) and whether the new backend gets the equivalent later.
- **Size assertions.** `TestCompilerOnExamples` asserts exact output sizes.
  Every peephole and every inlining change will shift them. This is expected;
  attribute each delta rather than bulk-updating.
- **Open question: how much should the compiler spend to avoid the poke
  syntax?** Fixed-address page arrays already reach 5 to 6 cycles per store and
  need no compiler work; a better diagnostic plus P4 may be enough to steer
  users there, with P7 and P10 as the deeper fixes.
- **P3 after P4.** P4 handles byte-indexed pairs as absolute-indexed accesses;
  P3 remains useful for word offsets and is implemented for that case.
- **Open question: is the 256-byte cap on byte arrays load bearing?** If it
  exists only because of the 8-bit index, P4 makes it less relevant; if it
  exists for layout reasons, a memory-mapped array spanning pages would need its
  own dispatch strategy, which is effectively P10 in data form.

---

## Appendix A: measurements this document is based on

Environment: Prog8 14.0-BETA3 (git b9fae182, the same commit as this working
tree), 64tass 1.60.3270M, PAL C64 under `x64sc` with the binary monitor.

Workload: a roughly 5 KB C64 program using the general `@(BASE + offset)` shape
for all screen writes. Screen construction for one full screen build costs about
224,000 cycles in this build (the same source published at 216,821 cycles with
the earlier compiler build; ratios matter, not absolutes).

Distribution of that cost, measured per stage:

| stage | share | work performed |
|---|---:|---|
| bulk clear | 40.2% | 1,000 cells, two arrays |
| cell-by-cell redraw | 22.8% | 204 cells through a two-level helper chain |
| frame drawing | 14.0% | 194 store-helper calls |
| text drawing | 13.2% | 132 characters, one helper call each |
| numeric conversion | 7.0% | 5 numbers, 10 digit positions each |
| small auxiliary area | 2.7% | 28 store-helper calls |

Supporting facts, all measured:

- The store helper body is 107 to 112 cycles, of which 41 to 52 are the two
  stores and about 25 are the row lookup and offset add.
- The redraw path reaches the store helper through a second helper, so those
  cells pay two staged calls.
- Argument staging: two byte arguments use A and Y; three or more use one static
  cell per argument at 8 cycles each.
- `pha`/`pla` around the pointer build is emitted only for constant store values.
- Constant multiply by 10 becomes a call to a shift-and-add helper: 12 cycles of
  call plus about 20, where the inline expansion is about 26 total.
- A comparison with three terms in a `while` condition costs about 120 cycles per
  evaluation because each term is materialised as 0/1.
- Addressing-mode costs per store: 26 (constant base plus ZP word), 28 (BSS
  word), 29 (byte index, with stack save), 10 (zero page cursor), 8 (zero page
  cursor plus constant below 256), 11 (fixed-address array, 8-bit index), 5
  (ideal absolute index).

For reference only, not part of this plan: rewriting the *source* to use zero
page cursors, fixed-address page arrays and branch-chained conditions reaches
about 110,000 cycles for the same screen, with byte-identical output. That is
2.0x, and it is the practical upper bound of what the proposals above are trying
to achieve without any source change.

---

## Appendix B: verification status

Everything here was checked by reading the working tree and, where noted, by
compiling test programs with the local build.

| id | verified state | where |
|---|---|---|
| P1 | IMPLEMENTED: numeric constant bases reload constant stores after pointer setup without `pha`/`pla` | `AssignmentAsmGen.kt:660-666,668-748`; `PrimitiveAssignmentsGen.kt:1654-1761,2013-2122`; `TestExecution6502.kt` |
| P2 | IMPLEMENTED: removes repeated index and constant loads across safe absolute-indexed stores | `AsmOptimizer.kt:511-560`; `TestAsmOptimizer.kt` |
| P3 | IMPLEMENTED: adjacent same-offset direct stores reuse one scratch pointer | `AsmGen.kt`; `AssignmentAsmGen.kt`; `TestExecution6502.kt` |
| P4 | IMPLEMENTED: numeric-base byte-indexed reads and stores use absolute indexing; zero-page bases retain pointer lowering | `PrimitiveAssignmentsGen.kt:2055-2095`; `AssignmentAsmGen.kt:573-605`; `TestExecution6502.kt` |
| P5 | two-argument cliff confirmed | `FunctionCallAsmGen.kt:29-38,212-244,242`; `ProgramAndVarsGen.kt:564-594,592`; `technical.rst:407-432` |
| P6 | deliberate size choice; inline expansion exists but is not preferred for the 13 table constants | `AsmGen.kt:56,60`; `BinaryOpAssignmentsGen.kt:457-536`; `ConstMultiply.kt:17-18,25` |
| P7a | not implemented; only 14 free ZP bytes, three 2-byte slots | `VariableAllocator.kt:49-121,137-170`; `AstPreprocessor.kt:308-317`; `TestZeropageAllocation.kt:119,181-185` |
| P7b | not implemented; blocked on inlining or on a callee-side cache | as P7a, plus `Inliner.kt:283-319` |
| P8 | not implemented; the IR backend already has the recursive algorithm | `BinaryOpAssignmentsGen.kt:1617-1765`; `IfElseAsmGen.kt:55-67,290-307,339-340`; `CodeDesugarer.kt:188-201`; `IRCodeGen.kt:1501-1542` |
| P9 | IMPLEMENTED: warns on slow 6502 direct memory stores; `-ignorefootguns` suppresses it | `AstChecker.kt:2672-2733`; `TestAstChecks.kt` |
| P10 | not implemented | - |
| P11 | inliner exists and is on by default, but 6502 is limited to one parameter | `Inliner.kt:166-186,271-346,283-319`; `Extensions.kt:60-62`; grammar `:362,:379` |

Two further facts that affect planning:

- The assembly optimiser runs for all 6502 targets whenever optimisations are
  enabled (`AsmGen.kt:124-132`) and is only disabled globally by `-noopt`; there
  is no per-pass switch.
- Its test suite is `codeGenCpu6502/test/TestAsmOptimizer.kt` (109 cases,
  including every `canDropIndexReload` guard and P2 regressions).

Behavioural checks run while verifying (compiled with the local build, target
c64, assembly inspected):

| check | result |
|---|---|
| `@(BASE + (ubyte)) = v` | scratch rebuild with `pha`/`pla`, 29 cycles |
| `uword base` in zero page, `base[i] = v` | `ldy i / lda #v / sta (base),y`, 10 cycles |
| `uword base` outside zero page, `base[i] = v` | copy to scratch then `sta (scratch),y`, 22 cycles |
| `base[w] = v` with a uword index | scratch rebuild, index low byte in Y, 26 cycles |
| page arrays, `page0[i] = v` and `page1[i] = v` | `ldy i / lda #v` emitted twice; nothing hoisted |
| `ubyte_var * 10` | `jsr prog8_math.mul_byte_10`, not the inline expansion |
| `-newcodegen`, page array store | `storex.b` / `sta $0400,x` (the new backend has the instruction) |
| `-newcodegen`, `@(const + var) = v` | `storei.b` through a fixed zero page pointer at `$04` |

## Appendix C: memory regions, named bases for cheap 6502 memory access

Status: design proposal, no implementation. Language-level companion to the
compiler-only proposals above; proposes a language addition so the efficient
access forms do not depend on luck.

### 0. Open questions (answer these before implementing)

1. `fill`/`copyfrom` as builtins, or range assignment (`region[0 to len-1] = v`)?
2. Should `for cell in region` yield an lvalue (proposed) or a read-only value plus a separate write form?
3. Lift the length cap for fixed-address regions only, or does the 256 limit serve a purpose beyond index width?
4. ZP contract for cursor variables: hard requirement (`@zp`, error if impossible) or warning with documented fallback?
5. 2D regions (`&ubyte[40][25] screen = $0400`, `screen[x, y]`) in the same change, or later?

### 1. Problem

A memory access in Prog8 is an anonymous address expression, `@(BASE + offset)`.
It has no representation the backend can keep, so the 6502 backend rebuilds a
16-bit address in a scratch pair on every store (26-29 cycles). The cheap forms
exist but are fragile:

- `uword base; base[i] = v` is 10 cycles if `base` lands in ZP, 22 otherwise.
  Placement is a usage-score heuristic, not a contract.
- `&ubyte[N] row = ADDR; row[j] = 1` emits `sta row,y` (5-7 cycles), but `N` is
  capped at 256 and the index must be a byte (`row[w]` is a compile error).

Verified: `&ubyte[1000] big = $0400` errors (byte array length 1-256);
`row[w] = 2` errors (array indexing limited to byte size 0..255). A plain
`uword` base in ZP uses `(base),y`; outside ZP it copies to scratch first.

Other languages give the base a name and a type (`screen[pos]`, `ptr[i]`), which
is what lets the compiler pick an addressing mode and reuse an address.

### 2. Proposal

#### 2.1 Regions: fixed-address arrays of any length

Lift the length cap for fixed-address arrays to 65535 elements and allow any
integer index, with lowering defined by what the compiler can prove:

| access | compiler knows | lowering | per cell |
|---|---|---|---|
| `region[const]` | constant offset | absolute | 5-7 |
| `region[ubyte]` | index is 8-bit | `sta BASE,x` / `BASE,y` | 5-7 |
| `region[uword]`, length <= 256 | index provably < 256 | low byte as index | 5-7 |
| `region[uword]`, length > 256 | word index, unknown range | page + index via ZP pointer | ~20-26 |
| `for i in 0 to len-1 { region[i] = v }` | range + induction | page spans or cursor | 4-6 |
| `fill(region, v)` | range only | page spans | 3-6 |

The third line is the key: a declared length proves a word index cannot leave
the base page, making it as cheap as a byte index. That information does not
exist in the language today.

Example:

```prog8
&ubyte[1000] buf = $0400
buf[w] = 1        ; word index; cheap iff compiler proves w < len(buf)
buf[j] = 2        ; byte index; always absolute indexed
```

#### 2.2 Bulk intent: `fill` / `copyfrom`

Builtins next to `len`/`peek`, no new grammar:

```prog8
fill(screen, 32)
copyfrom(screen, other)
```

A `fill` on a fixed-address region is the one case where base, length and
stride are all known at compile time, so the backend can emit page-span stores
(four `sta base,x` per iteration) and reach hand-written cost. This covers the
bulk-clear stage, which is ~40% of a full screen build and cannot be expressed
as intent today.

#### 2.3 Iteration over a region

```prog8
for cell in screen { cell = 32 }
```

Semantically identical to `for i in 0 to len(screen)-1 { screen[i] = 32 }`,
but the compiler sees the whole range and can pick cursor or page-span
lowering. Element is an lvalue.

#### 2.4 Word-element regions

`&uword[N] @nosplit` already works and scales the index. Require `@nosplit`
for memory-mapped word regions (already an error without it) and document the
element layout.

#### 2.5 Hardware registers unchanged

A single memory-mapped variable is a region of length one; `@(...)` keeps
working. No special case.

### 3. Cursor contract

Regions fix indexed access. Computed positions (`plot(x, y, ...)`) need a
cursor, and the language already has it:

```prog8
uword @zp p = BASE + offset
@(p) = value     ; -> ldy #0 / lda #v / sta (p),y   (10 cycles)
p += 1           ; -> inc / bne / inc                 (9 cycles)
```

Proposal:

1. Document `uword` dereferenced via `@(p)` as the cursor type, requiring ZP
   for the 10-cycle form.
2. Strengthen the allocator: a variable used as the base of `@(var)` or
   `var[i]` becomes a ZP candidate ahead of other DONTCARE variables. Today it
   competes on usage score while pointer-typed declarations jump the queue
   (`AstPreprocessor.kt:308-317`), so identical code can cost 10 or 22 cycles
   depending on unrelated allocation pressure.
3. Warn when such a variable misses ZP, naming the fix (`@zp`, or a region).

This turns a luck-dependent outcome into a documented contract with a visible
warning. See P7/P9 above for the compiler side.

### 4. What this removes

After this, no program needs `@(BASE + offset)` for a buffer:

- scanning/filling: region iteration, `fill`, or a cursor
- random access, byte offset: `region[byte]`, absolute indexed
- random access, word offset: `region[word]`, one pointer build, cheaper when
  the region proves the page

`@(...)` remains for hardware registers and computed jumps, where a rebuild
per access does not matter.

### 5. Migration

- Existing `@(...)` code compiles exactly as today. Opt-in by declaration.
- The P9 lint becomes a migration aid: "this access could be a region".
- Docs guidance: name buffers, then never mention their address again.
- Lifting the length cap is source-compatible; word indices on regions are
  additive (an error today).

### 6. Portability

| target | `region[ubyte]` | `region[uword]` | `fill` |
|---|---|---|---|
| 6502 | `sta BASE,x` | ZP pointer, or absolute when provable | page spans |
| m68k | `move.b d,(CONST,a2)` | same, one instruction | `move.b` loop or `memset` |
| virtual | `storei.b` with displacement | same opcode | `memset` |

Base + length + element size is the same input on all backends; no
target-specific source needed.

### 7. Costs and risks

- Bounds analysis is the new machinery. Where `index < length` cannot be
  proved, lowering falls back to the current pointer build, so it degrades
  safely. Loop ranges plus constant comparisons cover the cases that matter.
- `fill` and range iteration can duplicate a few instructions per page; apply a
  size cap as with the existing shift-add budgets.
- The 256 cap also limits `str` and general arrays; this lifts it for
  fixed-address regions only, leaving split-word and general layout alone.
- Making `@nosplit` mandatory for all memory-mapped regions is a small
  source-compatibility question (an error today only for words).
- Tests: length > 256, word indices with/without provable ranges, `fill`,
  iteration, plus `simulate()` tests asserting written bytes and no
  per-iteration rebuild.

### 8. Alternatives considered

- Codegen-only fix (`@(const + ubyte)` becomes absolute indexed). Cheapest,
  still worth doing (P4), but the base stays anonymous so word offsets and
  scanning get no help, and users must still know which spelling is fast.
- Addressing-mode suffix on `@`, e.g. `@[i]`. Renames the problem.
- First-class cursor type. `uword` + `@(p)` already lowers to the optimal
  three instructions, so a new type buys safety, not speed. Revisit if safe
  cursors become a goal.
