# Register Reuser - IR Register Packing Pass

Reduce distinct virtual registers in IR by coalescing non-overlapping live ranges per datatype.

## Algorithm (per subroutine)

### Step 1 — Build CFG
- Iterate `sub.chunks`, scan instructions for non-null `branchTarget`
- Unconditional branch → 1 successor; conditional branch → 2 (target + next); return/no branch → 0
- `IRInlineAsmChunk`/`IRInlineBinaryChunk` = opaque barriers (no liveness across)
- Build `successors: Map<IRCodeChunkBase, List<IRCodeChunkBase>>`

### Step 2 — Liveness analysis (backward data-flow)
- `liveOut[chunk] = ∪ { liveIn[s] for s in successors[chunk] }`
- `liveIn[chunk] = (liveOut[chunk] - written[chunk]) ∪ read[chunk]`
- Fixed-point iteration over chunks in reverse topological order

### Step 3 — Per-chunk register intervals
- Backward scan: `(firstDef[reg], lastUse[reg])` per register per chunk
- Registers in `liveIn[chunk]` extend to chunk start; `liveOut[chunk]` extend to chunk end
- Group by `IRDataType` (BYTE/WORD/LONG/FLOAT — separate pools)

### Step 4 — Greedy interval coloring (per chunk, per type)
- Sort intervals by `firstDef`
- For each interval, find a slot whose `lastUse < this.firstDef` or allocate new
- Build `packing: Map<Int, Int>` (original → packed register number)

### Step 5 — Rewrite instructions
- Remap `reg1`, `reg2`, `reg3`, `fpReg1`, `fpReg2` via `instr.copy()`
- Remap `FunctionCallArgs.arguments[].reg.registerNum` and `returns[].registerNum`

### Step 6 — Update register type map
- Rebuild next register counter and type map from max packed register

## Skipped
- `IRAsmSubroutine` — no parseable IR
- `globalInits` — small, infrequent
- `virtual` target — unlimited registers already

## Integration
In `IRCodeGen.generate()`, after `IRUnusedCodeRemover.optimize()` + final `linkChunks()`:
```kotlin
if(options.compTarget.name != "virtual") {
    RegisterPacker.pack(irProg)
}
```

## Files
| File | Purpose |
|------|---------|
| `codeGenIntermediate/src/.../RegisterPacker.kt` | Implementation |
| `codeGenIntermediate/test/TestRegisterPacker.kt` | Test suite |

## Test Cases
1. **Simple coalescing** — two non-overlapping regs, same type → 1 slot
2. **No coalescing** — overlapping regs, same type → 3 slots
3. **Cross-chunk liveness** — register live across boundary, blocks reuse
4. **Different types share slots** — BYTE & WORD overlap → same slot number
5. **Unconditional branch** — CFG has only 1 successor, no liveness across skipped chunk
6. **Conditional branch** — CFG has 2 successors, liveness flows to both
7. **Multi-chunk subroutine** — 3 chunks, 8 regs → reduced count
8. **FP registers** — packed independently from integer regs
9. **Function call args** — `fcallArgs` register numbers remapped
10. **Empty subroutine** — no crash
11. **Register type map** — updated after packing

## Progress
- [x] Plan written
- [ ] Implement RegisterPacker.kt
- [ ] Integrate into IRCodeGen.kt
- [ ] Implement tests
- [ ] Build and run
