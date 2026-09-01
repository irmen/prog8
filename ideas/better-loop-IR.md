# Better Loop IR

This document proposes preserving counted loop semantics in the IR so the `m68k` backend can emit `dbra` directly, replacing the fragile `p8_regfile` peephole.

---

## 1. Current State

`repeat N` and `for` over constant ranges are lowered early:

- `codeGenIntermediate/IRCodeGen.kt:1725` `PtRepeatLoop` with const `N` becomes `load #N-1 -> store p8_regfile[off]` + label + body + `dec p8_regfile[off]` + `bne label` (hidden `p8_regfile` slot, not a user register).
- `PtForLoop` constant range `translateForInConstantRange` `:1002` and variable-step `:905` similarly use hidden or user loop-var `inc/cmp/bne`.

`m68k` recovers `dbra` via `codeGenM68k/AsmOptimizer.kt:405` `optimizeDbraRepeatLoops` which pattern-matches `move.w #N-1,(p8_regfile+off)` + `label:` + `...` + `bne label` within 12 lines, checking no `d7` use and no `bsr/jsr` in body (calls clobber `d7`). It rewrites to `move.w #N-1,d7` / `label: body` / `dbra d7,label`. Bounces if body uses `d7` or calls.

This is `REGFILE-DEPENDENT` (`:406` comment) - depends on current `p8_regfile` allocation for repeat counters. If counters move to registers, transform breaks. `new6502` `InstrLoadStore.kt` and `VM` just execute the `IR` `inc/bne` loop as-is.

---

## 2. Design Decision

**Preserve trip count as IR, not as `p8_regfile` slot.**

Add a single `IR` loop construct that carries `trip = N` (or `from/to/step` for `for`), `body` chunks, and `label`:

```
loop.counted trip=N, label=loop1 { body }
```

Effective `trip = N` (`repeat N`) or `trip = (to-from)/step+1` for constant `for`. Variable `step`/`bounds` stay as existing `PtForLoop` lowering (no `IR` loop - keep `inc/cmp/bne`).

No separate `HLIR` layer. Upgrade `IRProgram` in place (`IRInstructions.kt` `IRCodeChunk` sibling `IRLoopChunk`), `IRFileWriter`/`VM` ignore or expand. Keeps existing `IR` opcode set unchanged.

Why not derive from `MUL`? `trip` is semantic (`N`), not `N-1` `move` immediate. Peephole must reconstruct `N-1`; `IR` loop keeps `N` directly for `m68k` `move.w #trip-1,d7`.

---

## 3. IR Representation

Add `IRLoopChunk : IRCodeChunkBase` (`intermediate/IRProgram.kt`):

```
IRLoopChunk(label:String, trip:Int, body: List<IRCodeChunkBase>)
```

`trip` is `Int` 1..65536 (`repeat` max `m68k` `dbra` 16-bit word). Validation `trip>=1`.

Serialization as pseudo `loop` block in `.p8ir` text (like `sub`):

```
loop loop1 trip=5 {
  ; body chunks
  call foo
}
```

`IRFileReader`/`IRFileWriter` handle `loop` as a new block element (similar to `SUB`), not via `parseIRCodeLine`. `VM` expands `IRLoopChunk` to the current `dec/bne` sequence if the backend does not optimize it.

---

## 4. Changes by Module

### 4.1 `intermediate`
Add `IRLoopChunk`, update `IRProgram` children `is IRLoopChunk` handling, `IRFileReader`/`IRFileWriter` parse/emit the `loop` block header (`Utils.kt:92` for shared parsing helpers), `IRInstructions.kt` not needed (no new `Opcode`).

### 4.2 `codeGenIntermediate`
`IRCodeGen.kt:1725` `translateRepeat` with const `N` (and `translateForInConstantRange` `:1002` when `for` is unused-`for` or `step +-1` and bounds const) emits `IRLoopChunk` instead of `p8_regfile` sequence. Keep variable-step `for` as before (`inc/cmp/bne`).

Delete `p8_regfile` slot allocation for repeat counters (`IRCodeGen.kt:1731` `p8_regfile` store).

### 4.3 `virtualmachine`
`VmProgramLoader.kt` expands `IRLoopChunk` to `load #trip-1 -> store loopVar -> bne` if `m68k` not target, or directly `dbra` semantics (`VirtualMachine.kt` new `InsLoop` if desired). Simplest: lower to existing `IR` before `VM` execute (no `VM` change).

### 4.4 `codeGenM68k`
`AsmOptimizer.kt:405` `optimizeDbraRepeatLoops` deleted. `codeGenM68k/AsmGen.kt` `translateChunk` handles `IRLoopChunk`: `move.w #trip-1,d7` + `label:` + `body` + `dbra d7,label` directly, no `p8_regfile` check, no bounce on `d7` (allocate `d7` as loop reg, check body `d7` use at `IR` level). `dbra` supports `trip` up to 65536 because it loads `#trip-1` into the low word.

### 4.5 `codeGenNew6502`
`codeGenNew6502/InstrControl.kt` expands `IRLoopChunk` to `ldy #trip` / `label: body` / `dey / bne label` (byte budget `AstChecker.kt:1107` ensures `trip<=256` fits `Y`).

---

## 5. Out of Scope
Variable-step `for` `IRCodeGen.kt:905` stays `inc/cmp/bne`. Old `codeGenCpu6502` unaffected (`SimpleAst`). `for ... in ptr..ptr+len` not counted.

---

## 6. Tests
`qemu68k` `repeat 5` asserts `move.w #4,d7` and `dbra d7,` no `p8_regfile`; `amiga500` same; `new6502` `repeat 5` asserts `ldy #5` / `dey/bne`; `VM` `repeat` trip `5` executes `5` times (extend `TestCompilerVirtual`).

---

## 7. File Map
| Concern | File |
|---------|------|
| `IR` loop chunk | `intermediate/IRProgram.kt` `Utils.kt:92` |
| `repeat/for` emit | `codeGenIntermediate/IRCodeGen.kt:1725,1002` |
| `m68k` `dbra` | `codeGenM68k/AsmOptimizer.kt:405` `AsmGen.kt` |
| `new6502` | `codeGenNew6502/InstrControl.kt` |
| `VM` | `VirtualMachine.kt` `VmProgramLoader.kt:177` |
| `6502` limit | `compiler/AstChecker.kt:1107` |

---

## 8. Implementation Notes for Subagents

### 8.1 Serialization format

In `.p8ir` XML, represent `IRLoopChunk` as a new element inside a block or subroutine, not as a single instruction line. Example:

```xml
<LOOP LABEL="loop1" TRIP="5">
  <CHUNK LABEL="...">...</CHUNK>
  ...
</LOOP>
```

The text serialization shown in section 3 is for human-readable dumps only; the canonical `IRFileReader`/`IRFileWriter` use XML attributes `LABEL` and `TRIP`.

### 8.2 Chunk linking and `next` pointers

`IRLoopChunk` itself has a `label` (entry point) and a `next` pointer (the chunk after the loop). Its `body` is a list of `IRCodeChunkBase` children that are linked sequentially, and the final body chunk's `next` is conceptually the loop back-edge (not stored as a pointer; the backend emits the branch).

During `IRProgram.linkChunks()`, treat the `IRLoopChunk` as a single node: its `label` maps to the chunk itself, and its `next` maps to the following chunk. Body chunks inside the loop are linked to each other but their `next` pointer should remain `null` or self-reference; the loop back-edge is generated by the backend/VM, not resolved through `next`.

### 8.3 Target-specific loop registers and `usedRegisters`

`IRLoopChunk.usedRegisters()` is target-agnostic but must report that the chunk clobbers a loop register. Use a target-independent convention: report the chunk as reading/writing a synthetic loop register number. Map that synthetic register to the actual target register in the backend:

- `m68k`: map to `d7`.
- `new6502`: map to `Y`.
- `VM`: no register needed if expanded early.

This prevents the register allocator from reusing the loop register inside the body. If the body already uses that register, the `IRLoopChunk` lowering must spill/save it around the loop (or reject the construct).

### 8.4 Nested counted loops

Nested counted loops on the same target loop register (`d7` for `m68k`, `Y` for `new6502`) cannot share the physical register. For the first implementation, either:

- save/restore the loop register around the inner loop, or
- restrict `IRLoopChunk` to non-nested use and fall back to the old `p8_regfile` sequence for nested loops.

A smarter allocator can later assign different data registers on `m68k`; on `new6502` only `Y` is practical for `dey/bne`, so nesting requires save/restore or fallback.

### 8.5 VM expansion strategy

Expand `IRLoopChunk` in `VmProgramLoader.kt` before execution. Replace each `IRLoopChunk` with an equivalent sequence of existing `IRInstruction`s:

```
load.w  rLoop, #trip-1
loopLabel:
  ... body chunks ...
  dec.w   rLoop
  bne     loopLabel
```

Allocate `rLoop` as a fresh VM register not used by the body. This keeps `VirtualMachine.kt` unchanged.

### 8.6 `for` loop eligibility

Only emit `IRLoopChunk` for `for` loops that satisfy **all** of:

- constant `from`, `to`, and `step` (step = +1 or -1);
- the loop variable is **not read** inside the body (it is only used to drive iteration);
- the trip count `(to-from)/step+1` is in `1..65536`.

If the loop variable is used, keep the existing `inc/cmp/bne` lowering so the variable holds the correct value each iteration. Variable-step or non-constant bounds also keep the existing lowering.

### 8.7 `dbra` details for `m68k`

`dbra Dn,label` decrements the low word of `Dn` and branches while the result is not `0xFFFF`. Loading `move.w #trip-1,d7` therefore executes the body exactly `trip` times:

- `trip = 1`: `d7 = 0`, body runs once, `dbra` decrements to `0xFFFF` and falls through.
- `trip = 65536`: `d7 = 65535`, body runs 65536 times.

The branch at the bottom of the body must be `dbra d7,loopLabel`, not `bne`.
