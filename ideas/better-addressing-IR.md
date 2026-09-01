# Better Addressing IR: Scaled Indexing, Struct Fields and Pointer Access

This document generalizes the scaled-indexing proposal to also cover struct field and pointer access, so the `m68k` backend can use its full addressing modes (`(An)`, `(An,d16)`, `(An,Xn*scale,d16)`) while `new6502`/`VM` stay optimal.

---

## 1. Current State

**Array index pre-scaling:** `loadIndexReg` (`codeGenIntermediate/IRCodeGen.kt:2228`) multiplies the index by `itemsize` (`:2243`/`2263` `multiplyByConst`). Consumers assume byte offset:

- `VM` `InsLOADX/STOREX` `VirtualMachine.kt:590/666` adds raw index
- `new6502` `indexedLoad` `InstrLoadStore.kt:675`
- `m68k` `(a0,d0.w)` `codeGenM68k/InstrLoadStore.kt` ("index pre-scaled")

Blocks `68020` `(An,Xn*2/*4)`.

**Struct fields:** always constant byte offset, not scaled:

- `ptr->field` `LOADI/STOREI immediate=fieldOffset` `ExpressionGen.kt:1915` `AssignmentGen.kt:354` (`reg1,(reg2+off)`)
- `var.field` `LOADM/STOREM symbolOffset`
- `arr[i].field` where `arr:Struct[Sz]` is pointer arithmetic: `EXT+MUL #Sz` `ExpressionGen.kt:183` `ADDR rBase,rIdx` `ADD #fieldOff` `LOADI 0` - 5 `IR` ops for one field, cannot fold to `LOADX arr+fieldOff,S=Sz`.

**Pointer arithmetic:** `ptr[i]` `ExpressionGen.kt:681` and `arr[i].field` above keep explicit `MUL` for real address calc, not `scale`.

---

## 2. Design Decision

**`IR` never pre-scales; effective address is always `base + index*scale + disp` where `disp = labelSymbolOffset + fieldOffset` (both constant).**

- `LOADX/STOREX/STOREZX` carry explicit `scale:Int=1` (`B/W/L`), `index` holds **element index** (or `struct` index), `disp` holds `symbolOffset` (+ field offset when applicable). `setlsb/setmsb` `BuiltinFuncGen.kt:934` uses `scale=eltSize`, `disp=byteOff` to express `b @ arr[i]` in `W/L` array (scale from elt size, value `b`).
- **Simple struct field** `ptr->field` stays `LOADI/STOREI disp=fieldOff` (`disp` already optimal `4(a0)` on `m68k`). No new opcode, but document as addressing mode.
- **Struct-array field** `arr[i].field` becomes **one** `LOADX/STOREX` with `base=arr`, `scale=structSize`, `disp=fieldOffset` (`loadx.b r5,r3,arr+4,S=12` for `MyStruct[12].x@4`), not `MUL+ADDR+LOADI`. This reuses `,S=N` + existing `+off` parsing (`Utils.kt:248`) already used for `arr+fieldOff`.
- **Pointer arithmetic** `ptr[i]` where `ptr` is `^^T` stays explicit `MUL`+`ADDR` (true address calc, not array `base`), but when `ptr` is known array symbol `arr` the above `LOADX` form is preferred. No change for generic `ptr[i]`.

No `F=field` metadata - `disp` alone is optimal for `m68k` `(a0,off)`; struct name only needed for future value `Enemy e` `DataTypes.kt:22` `STRUCT_INSTANCE`, kept as future `HLIR` note.

---

## 3. IR Representation

Add `scale:Int=1` to `IRInstruction` (`intermediate/IRInstructions.kt:918`), `scale!=1` only on `LOADX/STOREX/STOREZX`, `scale>=1`.

Serialization `,S=N` always for those three, composes with `+disp`:

```
loadx.w r5,r2,arr,S=2
loadx.b r5,r3,arr+4,S=12      // arr[i].x  struct 12, field 4
storx.b r1,r2,floatarray+1,S=5 // setlsb
loadi.b r5,r2,4               // ptr->field disp 4
```

`Utils.parseIRCodeLine` strips optional `,S=N`, defaults `1`; `IRFileWriter` round-trips via `toString()`.

---

## 4. Scale Values by Target

| type | cx16/c64/pet32 | m68k/qemu68k | VM |
|------|----------------|--------------|----|
| byte/bool |1|1|1|
| split word|1|1|1|
| word|2|2|2|
| pointer|2 (split 1)|4|4|
| long|4|4|4|
| float|5(Mflpt5)|4|8|
| struct|structSize|structSize|structSize|

`6502` `ARRAY_SIZE_LIMIT 256` byte budget `AstChecker.kt:1107` ensures `scaled` fits `byte`; `m68k` `scale 2/4` fits `d0.w*scale`; `68020` `*1/*2/*4` (`*8` via `.l`).

---

## 5. Changes by Module

### 5.1 `intermediate`
Add `scale`, `,S=` parsing/validation, update `IRInstructions.kt:724` `IRProgram.kt:318`.

### 5.2 `codeGenIntermediate` - stop pre-scaling, fold struct-array field
`loadIndexReg` `IRCodeGen.kt:2228` delete `multiplyByConst` at `:2243/:2263`, keep `EXT` for `m68k` word index, drop `itemsize` param.

Array sites emit `scale`: `ExpressionGen.kt:646`, `AssignmentGen.kt:802/822/843`, `for` loop `IRCodeGen.kt:774` (`+1` inc, compare `len` not `len*eltSize`), `BuiltinFuncGen.kt:934` (`scale=eltSize`+`disp`).

**Struct-array field** `ExpressionGen.kt:183/1863` `AssignmentGen.kt:759`: if `arr` is `Struct[]` symbol + field chain, emit single `LOADX/STOREX base=arr+fieldOff,S=structSize` instead of `MUL+ADDR+LOADI`. Simple `ptr->field` unchanged `LOADI disp`.

Pointer paths `ExpressionGen.kt:681` keep explicit `MUL` (real `^^^^`).

### 5.3 `virtualmachine`
`InsLOADX:590/STOREX:666/STOREZX:723` `base+index*scale+offset` (`offset` via `VmProgramLoader.kt:177`), `reg2`/`reg1` per `IRInstructions.kt:728`.

### 5.4 `codeGenM68k`
`InstrLoadStore.kt` after `d0` load: `qemu68k` `(a0,d0.w*N)` / `(0,a0,d0.w*N)` + `disp` via `resolveAddress` `(B,a0,d0.w*N)`; `amiga500` `lsl.w #1`/`lsl.l #2`; struct-array field now one `m68k` `move.b 4(a0,d0.w*4),d0` instead of 3 insns.

### 5.5 `codeGenNew6502`
`InstrLoadStore.kt:675/753/845` helper `if(scale==2) asl else if(4) asl;asl` - struct-array field `arr+fieldOff` `base` via `resolveAddress`, `scale` as for word arrays; `LOADI disp` stays `(ptr),y`.

---

## 6. Out of Scope
`codeGenCpu6502` old backend unaffected. `6502` float `LOADX` `TODO`. Pointer `ptr[i]` generic keeps `MUL`. `for` loop `BYTE INC` on `m68k` check `INC` width for >255 `len`.

---

## 7. Tests
`intermediate` `,S=` round-trip; `qemu68k` `arr[i].x` asserts `(a0,d0.w*4+4)`; `amiga500` `lsl`; `new6502` `asl` shift; `VM` word/long/struct-array field with `scale+disp`.

---

## 8. File Map
| Concern | File |
|---------|------|
| IR def, `,S=` | `intermediate/IRInstructions.kt` `Utils.kt` `IRProgram.kt:318` |
| `loadIndexReg`, `for` | `codeGenIntermediate/IRCodeGen.kt` |
| array/struct-array field | `codeGenIntermediate/ExpressionGen.kt:646/183` `AssignmentGen.kt:759/802` |
| `setlsb` | `BuiltinFuncGen.kt:934` |
| VM | `VirtualMachine.kt:590` `VmProgramLoader.kt:177` |
| m68k | `codeGenM68k/InstrLoadStore.kt` `AsmGen.kt:97/233` |
| new6502 | `codeGenNew6502/InstrLoadStore.kt:675` |
| limits | `compiler/AstChecker.kt:1107` |

---

## 9. Implementation Notes for Subagents

### 9.1 `,S=` and `+offset` ordering

`+offset` is parsed into `IRInstruction.symbolOffset` (`labelSymbolOffset`). `,S=N` is a separate `scale` field.

For `arr[i].field`:
- `labelSymbol` = `arr`
- `symbolOffset` = `fieldOffset` (the `+4`)
- `scale` = `structSize` (the `,S=12`)

`parseIRCodeLine` should strip the `,S=N` suffix **after** extracting the operand list, then assign the remaining `arr+4` to `labelSymbol` + `symbolOffset` exactly as today.

### 9.2 `STOREZX` operand order

`STOREZX` (`store zero-extending`) uses the same register convention as `STOREX`: `reg1` is the value register, `reg2` is the index register, and the memory location is `address/labelSymbol + symbolOffset + reg2*scale`.

For float arrays, follow the existing `LOADX` convention: `reg1` is the index, `fpReg1` is the value. Do not introduce new operand mappings.

### 9.3 Array symbol `arr[i]` vs generic pointer `ptr[i]`

Only emit the new `LOADX/STOREX,S=` form when the base is a **known array variable symbol** (`PtIdentifier` of an array-typed variable) or a struct-array symbol.

If the base is a `PtPointerVariable` or the result of pointer arithmetic (`^^T`, address taken, returned pointer, etc.), keep the existing `MUL+ADDR+LOADI` sequence. The decision point is in `ExpressionGen.kt` when translating the array indexer / pointer deref: check `deref.startpointer` type - array symbol -> `LOADX,S=`; pointer expression -> explicit multiply.

### 9.4 Non-power-of-2 scales on `new6502`

For `scale` values that are not 1, 2, or 4, fall back to an explicit multiply of the index before indexing. Example for `scale=5` (Mflpt5 float array on 6502):

```
ldx index
lda #5
jsr multiply_x_by_a   ; or inline multiply
sta scaledIndex
; then index with scaledIndex as byte offset
```

Do not silently truncate or use repeated `asl` for scale 3/5/etc.

### 9.5 VM `wordArrayIndex` flag and scaling

In `VirtualMachine.kt`, `wordArrayIndex` selects whether the index register is read as byte or word. Compute the effective address as:

```kotlin
val index = if (wordArrayIndex) registers.getUW(i.reg2!!).toUInt() else registers.getUB(i.reg2!!).toUInt()
val base = i.address!!.value + (i.labelSymbolOffset?.toUInt() ?: 0u)
val effective = base + index * i.scale.toUInt()
```

Apply this same expression in `InsLOADX`, `InsSTOREX`, and `InsSTOREZX`. For float `LOADX`, use `i.reg1` as the index register and `fpReg1` as the destination.
