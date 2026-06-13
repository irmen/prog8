# Register Argument Spill Sites

Detected by instrumenting `FunctionCallAsmGen.kt` and compiling with the instrumented compiler.
These are call sites where argument evaluation clobbers already-loaded register values,
requiring stack save/restore.

Consolidated example: `examples/test-spill.p8` — compiles with `prog8c -target cx16 ...`.
Covers all 4 spill patterns. Note: some patterns may not trigger with optimizations enabled
if the complex expression gets inlined; `get_value()` uses a conditional to prevent inlining.

## Spill Path A: `useCpuRegistersForArgs` (PtSub, 2 byte params)

Both arguments need A during evaluation, so arg0 is loaded into A, saved with `pha`,
arg1 loaded into Y, then A restored with `pla`.

### `textelite.p8:101`

```prog8
galaxy.travel_to(savedata[0], savedata[1])
```

- `travel_to` is a `sub(ubyte galaxynum, ubyte system)` (no register annotations)
- Both `savedata[0]` and `savedata[1]` are `PtMemoryByte` with indexed addresses
  (not simple `PtNumber`/`PtIdentifier`), so `needAsaveForExpr` returns `true` for both
- Triggers the `else` branch: load A with arg0, `pha`, load Y with arg1, `pla`

## Spill Path B: `argumentsViaRegisters` (PtAsmSub)

An argument expression may clobber registers that already hold values from previously
evaluated arguments. All used registers (X, Y, A) are saved to stack before evaluating
such an argument, then restored afterwards.

The evaluation order for single CPU registers is: **Y → X → A** (determined by
`asmsub6502ArgsEvalOrder`, which sorts registers descending by enum ordinal:
A=0, X=1, Y=2).

### `textelite.p8:539` — `txt.setchr(2+sx, 2+sy, char)`

**Signature:**
```
asmsub setchr(ubyte col @X, ubyte row @Y, ubyte character @A)
```

**Arguments by register:**
| Register | Argument | Type |
|----------|----------|------|
| Y (evaluated 1st) | `2+sy` | binary expression |
| X (evaluated 2nd) | `2+sx` | binary expression |
| A (evaluated 3rd) | `char` | identifier |

**Why spill:** The X argument `2+sx` is a binary expression that clobbers Y during
evaluation. Y already holds the row value from the first argument. So Y is saved,
X's expression evaluated, then Y restored.

### `textelite.p8:548` — `txt.plot(0, display_scale_y(64) + 4)`

**Signature:**
```
asmsub plot(ubyte col @Y, ubyte row @X)
```

| Register | Argument | Type |
|----------|----------|------|
| Y (evaluated 1st) | `0` | number — simple, no spill |
| X (evaluated 2nd) | `display_scale_y(64) + 4` | function call + binary expr |

**Why spill:** The X argument is a function call followed by addition, which may clobber
Y. But Y already holds the column value `0`. Y is saved, X's expression evaluated,
then Y restored.

### `textelite.p8:550` — `txt.plot(0, display_scale_y(256) + 4 as ubyte)`

Same as above but the second arg has a type cast as well.

### `textelite.p8:554` — `txt.plot(2+screenx-2, 2+screeny+1)`

| Register | Argument | Type |
|----------|----------|------|
| Y (evaluated 1st) | `2+screenx-2` | binary expression chain |
| X (evaluated 2nd) | `2+screeny+1` | binary expression chain |

**Why spill:** Both are complex expressions. After evaluating Y's arg (which itself
does not trigger spilling since no registers used yet), the X argument is evaluated
next. Its expression may clobber Y, so Y is saved first.

### `textelite.p8:557` — `txt.plot(2+screenx-2, 2+screeny+2)`

Same pattern as line 554, with different offset.

### `diskio.p8:752` — `cbm.SETNAM(strings.length(filenameptr), filenameptr)`

**Signature:**
```
extsub $FFBD = SETNAM(ubyte namelen @A, str filename @XY)
```

Note: `@XY` means the X and Y registers together hold a 16-bit pointer (X=low, Y=high).

| Register | Argument | Type |
|----------|----------|------|
| XY (evaluated 1st) | `filenameptr` | identifier — simple, no spill |
| A (evaluated 2nd) | `strings.length(filenameptr)` | function call |

**Why spill:** The A argument is a function call `strings.length()` which may clobber
XY during execution. XY already holds the filename pointer from the first argument.
So XY must be saved before calling `strings.length()`, then restored.

Note: Since XY is passed as a paired register, saving it requires saving X and Y
individually (X goes through A on plain 6502).

### `diskio.p8:817` — `cbm.SETNAM(strings.length(filenameptr), filenameptr)`

Same pattern as line 752, in `internal_load_routine`.

### `diskio.p8:844` — `cbm.SETNAM(flen+2, list_filename)`

**Signature:**
```
extsub $FFBD = SETNAM(ubyte namelen @A, str filename @XY)
```

| Register | Argument | Type |
|----------|----------|------|
| XY (evaluated 1st) | `list_filename` | identifier — simple, no spill |
| A (evaluated 2nd) | `flen+2` | binary expression |

**Why spill:** The A argument is a binary expression `flen+2` which may clobber
XY during evaluation. XY already holds the pointer from the first argument.
XY is saved, `flen+2` evaluated into A, then XY restored.

## Patterns

1. **PtSub spilling** occurs when both byte arguments are memory reads from arrays
   (`savedata[N]`), which aren't simple enough for `needAsaveForExpr`.

2. **Asmsub spilling** occurs when an **earlier-evaluated register** (higher priority
   by the static ordering: combined > pairs > singles, Y > X > A) gets a simple
   argument but a **later-evaluated register** gets a complex expression that
   clobbers the earlier register.

3. The most common pattern is: XY (paired) gets a simple identifier, then A gets a
   function call or arithmetic expression — the function call clobbers XY.

4. Another common pattern: Y gets a simple value, X gets an expression — the expression
   might clobber Y, so Y is saved/restored.

5. If the **reverse** order were used (low-priority registers evaluated first),
   these spills would not occur: A then XY means the function call for A runs
   before XY is loaded, so XY isn't at risk.
