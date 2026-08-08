# Scaled Indexing for IR LOADX / STOREX / STOREZX

This document describes the design to move array index scaling out of the IR
generator and into the code generation backends, by giving the IR instructions
`LOADX`, `STOREX`, and `STOREZX` an explicit `scale` operand.

The goal is to let the m68k backend use the 68020 hardware scaled-index
addressing mode (`(An, Xn*scale)`), while keeping the IR identical for all
targets and fixing the same problem uniformly for every backend.

---

## 1. Current State (background)

The IR generator pre-scales the array index:

- `loadIndexReg` (`codeGenIntermediate/IRCodeGen.kt:2100-2128`) loads the
  element index and immediately multiplies it by the element size
  (`itemsize`), both on the 16-bit path (`:2115`, `multiplyByConst(UWORD, ...)`)
  and on the 8-bit path (`:2126`, `multiplyByConst(UBYTE, ...)`).
- Therefore every consumer of `LOADX` / `STOREX` / `STOREZX` assumes the index
  register already holds a **byte offset** into the array:
  - The VM adds the raw index to the base address (`InsLOADX`/`InsSTOREX`/
    `InsSTOREZX` in `VirtualMachine.kt:576/646/693`).
  - new6502 uses the raw index as a byte offset into the array
    (`indexedLoad`/`storeExchange`/`zeroMemoryIndexed` in
    `InstrLoadStore.kt:675/753/845`).
  - m68k uses the raw index as `d0` in `(a0,d0.w)` / `(0,a0,d0.l)` forms
    (`codeGenM68k/InstrLoadStore.kt`), with comments noting "index pre-scaled".

Because the index is pre-scaled, the 68020's hardware scaled addressing mode
`(An, Xn*2)` / `(An, Xn*4)` cannot be used: the value in `Xn` would already be
scaled, so the hardware scaling would double-apply.

---

## 2. Design Decision

**The IR never pre-scales the index, regardless of compilation target.**

- The index register in `LOADX` / `STOREX` / `STOREZX` always holds the
  **element index**.
- Each instruction carries an explicit **`scale`** operand: the element size in
  bytes.
- The effective address is:

      base + index*scale + labelSymbolOffset

- `labelSymbolOffset` already exists on IR instructions and is already resolved
  everywhere, including in the VM program loader
  (`VmProgramLoader.kt:177-201`), so the intra-element byte offsets needed by
  `setlsb` / `setmsb` ride along for free.

### Why an explicit operand instead of deriving it from the type

`setlsb` / `setmsb` store a single byte (`.b`) into word/long/float arrays where
the element size (2/4/5/8) differs from the size of the value being stored
(1). Only an explicit operand can express "store a byte at element index i of an
array whose elements are N bytes wide". Deriving the scale from the value type
would give 1 for these stores and break them.

### Struct fields are unaffected

Struct field access never goes through the scaled index register. Field offsets
are always constant byte offsets:

- Pointer field access: `LOADI` / `STOREI` with `immediate = fieldOffset`
  (`ExpressionGen.kt:1886-1897`, `AssignmentGen.kt:295-315`).
- Struct-variable field access: `LOADM` / `STOREM` with `symbolOffset`.
- Array of structs: index is multiplied by the struct size and added to the
  pointer via `ADDR` (genuine pointer arithmetic, `ExpressionGen.kt:1866-1868`).

So the "absolute fixed byte index" of a struct field never appears in a scaled
`LOADX`/`STOREX`/`STOREZX` index register. The `scale` operand applies only to
the element-index convention of these three opcodes.

### Backward compatibility

No backward compatibility is needed for existing `.p8ir` files; the format
changes freely. The serialized `scale` is always written for the three opcodes
(see Section 3), so files are self-describing.

---

## 3. IR Representation

### 3.1 The `scale` field

- Add `val scale: Int = 1` to `IRInstruction`
  (`intermediate/IRInstructions.kt`, constructor around `:902`).
- Validation:
  - `scale >= 1`.
  - `scale != 1` is only allowed on `LOADX`, `STOREX`, `STOREZX`.

### 3.2 Serialization

Format: trailing `,S=N`, always written for the three opcodes.

```
loadx.w r5,r2,arr,S=2
storx.b r1,r2,floatarray,S=5
storezx.b r3,arr,S=1
```

Parsing: `parseIRCodeLine` (`intermediate/Utils.kt:87`) strips an optional
trailing `,S=N`; defaults to 1 when absent. The existing label + offset parsing
(`arr+1`, `Utils.kt:242-253`) is unchanged and composes with it:

```
storx.b r1,r2,arr+1,S=4
```

`IRFileReader` / `IRFileWriter` need no changes; they round-trip through
`toString()` / `parseIRCodeLine`.

### 3.3 Documentation updates

- `instructionFormats` header comment (`IRInstructions.kt`, around `:710`).
- The 32-bit word-index note in `IRProgram.kt:315`.

---

## 4. Scale Values by Target

| Element type  | 6502 (cx16/c64/c128/pet32) | m68k (amiga500/qemu68k) | VM   |
|---------------|----------------------------|-------------------------|------|
| byte / bool   | 1                          | 1                       | 1    |
| split word    | 1 (accesses lsb/msb byte sub-arrays) | 1                | 1    |
| word          | 2                          | 2                       | 2    |
| pointer       | 2 (non-split), 1 (split)   | 4                       | 4    |
| long          | 4                          | 4                       | 4    |
| float         | 5 (Mflpt5)                 | 4                       | 8    |

Constraints that make everything fit:

- 6502: `ARRAY_SIZE_LIMIT = 256` is a **byte budget** (`AstChecker.kt:1080-1099`):
  word arrays <= 128 elements, long <= 64, float <= 51. A scaled byte offset
  therefore always fits in a byte (max 50*5 + 4 = 254), so 8-bit index
  arithmetic suffices.
- 32-bit targets: all IR registers are physically 32-bit; element index
  <= 16383 (word) / 8191 (long/float), scaled value <= 32767, which fits in a
  signed 16-bit `.w` index register.
- 68020 scaled addressing supports `*1`, `*2`, `*4` (and `*8` with `.l`); m68k
  only ever sees scales 1/2/4.

---

## 5. Changes by Module

### 5.1 `intermediate`

1. Add `scale: Int = 1` to `IRInstruction` with the validation from Section 3.1.
2. Serialize `,S=N` in `toString()` for `LOADX`/`STOREX`/`STOREZX`; parse it in
   `Utils.parseIRCodeLine`.
3. Update the comments in `IRInstructions.kt` and `IRProgram.kt:315`.

### 5.2 `codeGenIntermediate` - stop pre-scaling

**`loadIndexReg`** (`IRCodeGen.kt:2100`):

- Delete the two `multiplyByConst` calls (`:2115` and `:2126`).
- Drop the now-unused `itemsize` and `arrayIsSplitWords` parameters.
- Keep the byte-to-word `EXT` so 32-bit targets get a word-sized index.

**Array-indexing sites - emit `scale = eltSize` on the instruction:**

- `ExpressionGen.kt:646` (`indexByExpression`).
- `AssignmentGen.kt:746/766/787` (`translateRegularAssignArrayIndexed`).
- Regular-array `for` loop (`IRCodeGen.kt:752-769`): LOADX gets
  `scale = elementSize`; the loop increment changes from
  `addConstByteToReg(indexReg, elementSize)` to `+1`; the loop compare changes
  from `lengthBytes` (= length*elementSize) to `iterableLength`.
- `setlsb`/`setmsb`, variable-index case (`BuiltinFuncGen.kt:931/952`):
  `scale = eltSize`, `labelSymbolOffset = byteOffset(eltSize)`, and drop the
  `ADD indexReg, byteOffset` that is no longer needed. The const-index branches
  (`:923-940`, `:944-950`) keep baking the full byte offset into the `LOAD`
  immediate with default `scale = 1`.

**Pointer-arithmetic sites - keep the explicit multiply:**

These sites use `loadIndexReg` to compute a real address (element offset
combined with a pointer via `ADDR` or `LOADI`/`STOREI`). They must multiply by
the element size themselves after `loadIndexReg`:

- `ExpressionGen.kt:681` (`translatePointerIndexing`).
- `ExpressionGen.kt:1866` (array-of-structs pointer math, struct size).
- `AssignmentGen.kt:703/713/835` (pointer store paths).
- `BuiltinFuncGen.kt:867` (`setlsb`/`setmsb` on a pointer target).

These paths do **not** use the `scale` operand; they keep genuine pointer
arithmetic, unchanged in behavior.

### 5.3 `virtualmachine`

`InsLOADX` (`:576`), `InsSTOREX` (`:646`), `InsSTOREZX` (`:693`): multiply the
index register by `i.scale` before adding it to the base address. The index
register is:

- `reg2` for integer `LOADX` / `STOREX`,
- `reg1` for float `LOADX` / `STOREX` and for all `STOREZX`
  (matches `instructionFormats`, `IRInstructions.kt:714/721/725`).

`labelSymbolOffset` is already folded into the base address by
`VmProgramLoader.kt:177-201`, so no change is needed there.

### 5.4 `codeGenM68k`

In `InstrLoadStore.kt` (including the float variants at `:198/252/282`), after
the index has been loaded into `d0` (`loadIndexToD0`, `AsmGen.kt:162`):

- **M68020 (qemu68k):** use the hardware scaled-index mode:
  - `(a0,d0.w*N)` for the integer forms,
  - `(0,a0,d0.w*N)` for the float forms (the index is a word value, always
    <= 32767, so `.w*N` is correct).
  - `labelSymbolOffset` folds into the displacement via `resolveAddress`
    (`AsmGen.kt:212`), producing `(B,a0,d0.w*N)`.
- **M68000 (amiga500):** there is no scaled-index mode, so shift explicitly:
  - `lsl.w #1,d0` for scale 2,
  - `lsl.l #2,d0` for scale 4,
  then keep the existing `(a0,d0.w)` / `(0,a0,d0.l)` forms.
- Refresh the stale "index pre-scaled" comments.
- `scale = 1` paths are unchanged.

Gate on `AsmGen.cpu` (`AsmGen.kt:55`); only the 68020 path uses the scaled
addressing mode.

### 5.5 `codeGenNew6502`

The index register is always a byte on the 6502, and the array byte budget
(Section 4) guarantees the scaled offset fits in a byte.

A small helper multiplies the byte index by `insn.scale`:

- `scale` 1: no-op (byte and split arrays).
- `scale` 2: `asl`.
- `scale` 4: `asl` twice.
- any other scale (notably 5, the Mflpt5 float size): `TODO` - to be
  implemented together with float array support. This leaves
  `setlsb`/`setmsb` on float arrays unimplemented in this backend for now.

The helper is used by `indexedLoad` (`:675`, byte path), `storeExchange`
(`:753`), and `zeroMemoryIndexed` (`:845`). `labelSymbolOffset` folds into the
base address via `resolveAddress`, so setlsb/setmsb needs no extra handling.

Float `LOADX`/`STOREX`/`STOREZX` remain `TODO` in this backend
(`:711/861-862`), unrelated to this change.

---

## 6. Out of Scope / Notes

- The old SimpleAST 6502 backend (`codeGenCpu6502`) does not consume IR and is
  unaffected.
- On the 6502, byte-size-budgeted arrays (Section 4) mean scaled offsets always
  fit in a byte; no 16-bit carry propagation is required in the scale helper.
- The m68k regular-array `for` loop uses a BYTE-typed `INC`/`CMPI` on the loop
  index. Verify during implementation that on m68k these operate on the full
  32-bit register so comparing against `iterableLength` works for arrays with
  more than 255 elements; if not, widen the loop index register to WORD on
  32-bit targets only (the VM is unaffected). This addresses the known latent
  m68k loop-index issue; 6502 loops are fine because of the byte budget.
- Pointer indexing keeps an explicit multiply (it is real pointer arithmetic,
  not array indexing) and does not use the `scale` operand.

---

## 7. Test Coverage

- `intermediate`: `.p8ir` round-trip with `,S=N`; defaults to 1 when absent.
- qemu68k: compile word/long array loads and stores, assert the generated
  `.asm` contains `(a0,d0.w*2)` / `(a0,d0.w*4)`; setlsb/setmsb variant asserts
  `(B,a0,d0.w*N)`.
- amiga500: same programs, assert `lsl.w #1,d0` / `lsl.l #2,d0` are present and
  that no `(a0,d0.w*...)` scaled forms appear.
- VM (`TestCompilerVirtual`, extend the existing regression at `:930`): word /
  long / float element access with a variable index (scale 2/4/8), plus a
  `setlsb`/`setmsb` case (scale + `labelSymbolOffset`).
- cx16 (new6502): word/long indexed access, assert the shift sequences (scale
  2/4); optional ksim65 execution test (`prog8tests.codegeneration.TestExecution6502`
  pattern).
- Use the compile-and-grep-`.asm` test pattern from
  `TestAmigaChipramOption.kt:96-103`.

---

## 8. File Reference Map

| Concern | File |
|---------|------|
| IR instruction definition, formats, validation, serialization | `intermediate/src/prog8/intermediate/IRInstructions.kt` |
| IR text parsing (`,S=N`) | `intermediate/src/prog8/intermediate/Utils.kt` |
| IR comment (32-bit word index note) | `intermediate/src/prog8/intermediate/IRProgram.kt:315` |
| IR code generator, `loadIndexReg`, for loop | `codeGenIntermediate/src/prog8/codegen/intermediate/IRCodeGen.kt` |
| Scaled store sites | `codeGenIntermediate/src/prog8/codegen/intermediate/AssignmentGen.kt` |
| Indexed loads, pointer math | `codeGenIntermediate/src/prog8/codegen/intermediate/ExpressionGen.kt` |
| `setlsb`/`setmsb` | `codeGenIntermediate/src/prog8/codegen/intermediate/BuiltinFuncGen.kt` |
| VM execution of the three opcodes | `virtualmachine/src/prog8/vm/VirtualMachine.kt` |
| VM label + offset resolution | `virtualmachine/src/prog8/vm/VmProgramLoader.kt:177-201` |
| m68k load/store emission | `codeGenM68k/src/prog8/codegen/m68k/InstrLoadStore.kt` |
| m68k `cpu`, `loadIndexToD0`, `resolveAddress` | `codeGenM68k/src/prog8/codegen/m68k/AsmGen.kt` |
| new6502 load/store emission | `codeGenNew6502/src/prog8/codegen/new6502/InstrLoadStore.kt` |
| Array byte budget limits | `compiler/src/prog8/compiler/astprocessing/AstChecker.kt:1080-1099` |
