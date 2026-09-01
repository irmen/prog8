# Better Addressing IR — Deferred: Struct-Array Field Folding

> Core scaled indexing (`IRInstruction.scale` / `,S=N`, `VM` and backends `virtual`/`qemu68k`/`amiga500`/`new6502`, removal of `loadIndexReg` pre-scaling) was implemented 2026-09-02. This document now only tracks the deferred optimization.

## Remaining: `arr[i].field` single-LOADX

`arr:Struct[Sz]` currently lowers as 5 IR ops:

`EXT+MUL #Sz` (`ExpressionGen.kt:183`) → `ADDR rBase,rIdx` → `ADD #fieldOff` → `LOADI 0`

Goal: fold to one indexed op reusing the already-implemented `,S=N` + `+off` encoding:

```
loadx.b r5,r3,arr+4,S=12      // arr[i].x  struct 12, field 4  (disp=fieldOff, scale=structSize)
storx.b r1,r2,arr+4,S=12
```

* `labelSymbol` = `arr`, `symbolOffset` = `fieldOffset` (`+4`), `scale` = `structSize` (`S=12`)
* Simple `ptr->field` stays `LOADI/STOREI disp=fieldOff` (already optimal `4(a0)` on m68k)
* Generic `ptr[i]` where `ptr:^^T` stays explicit `MUL+ADDR` — only fold when base is a known array symbol (`PtIdentifier` of array-typed variable / struct-array symbol); check `deref.startpointer` type in `ExpressionGen.kt` (9.3)

## Where to change

* `ExpressionGen.kt:183/1863`, `AssignmentGen.kt:759` — detect `Struct[]` symbol + field chain, emit `LOADX/STOREX base=arr+fieldOff,S=structSize` instead of `MUL+ADDR+LOADI`
* Pointer paths `ExpressionGen.kt:681` keep explicit `MUL`
* Backends already handle `,S=N`+`+off` (`m68k` → `move.b 4(a0,d0.w*4),d0`, `new6502` → `asl` helper, `VM` → `base+index*scale+offset`) — no further backend work needed; this just enables the single-op form

## Implementation notes (from original §9)

**9.3 Array symbol vs generic pointer:** only fold known array symbols; keep `MUL+ADDR+LOADI` for `PtPointerVariable` / `^^T` results.

**Original file map for this item:** `codeGenIntermediate/ExpressionGen.kt:646/183` `AssignmentGen.kt:759/802` `IRInstructions.kt` `Utils.kt` `IRProgram.kt:318` `VirtualMachine.kt:590` `VmProgramLoader.kt:177` `codeGenM68k/InstrLoadStore.kt` `codeGenNew6502/InstrLoadStore.kt:675` `AstChecker.kt:1107`.
