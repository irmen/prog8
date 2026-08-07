# Arrays of Structs — Analysis & Change Overview

Prog8 does not currently allow declaring an array whose elements are
structures. This document analyzes the current state of the code and outlines
the changes needed to support `structtype name[size]`.

## Current state

The feature is **explicitly gated** in the parser. Declaring an array of
structures throws a hard error:

- `compilerAst/src/prog8/ast/antlr/Antlr2KotlinVisitor.kt:235-236`

  ```kotlin
  } else if(baseDt.isStructInstance)
      throw SyntaxError("array of structures not allowed (use array of pointers)", ctx.toPosition())
  ```

The recommended workaround today is `structtype[size]*` — an array of pointers
to separately-allocated structs — because the pointer element size is fixed
(2 bytes on 6502, 4 on m68k) and needs no struct-size stride.

## What already exists (the hard parts are partly done)

A surprising amount of the supporting machinery is already present:

- **Array indexer accepts struct elements.** `PtArrayIndexer`
  (`simpleAst/src/prog8/code/ast/AstExpressions.kt:239-240`) already permits
  `elementType.isStructInstance`, so the AST node is ready.
- **Index × struct-size math exists.** `ExpressionGen.kt:161-185` computes
  `offset = constIndex * struct.size` and `multiplyByConst(indexReg,
  struct.size)` when resolving a struct field; `:1850-1922` handles
  "index * structsize + field" for struct field access. The element-base
  arithmetic for arrays of structs is essentially the same code.
- **Array iteration uses element size.** `IRCodeGen.kt:752` computes
  `elementSize = program.memsizer.memorySize(elementDt)` and `:760` does
  `lengthBytes = iterableLength * elementSize`. If `elementDt` is a struct,
  this already yields `struct.size`.

So this is not a from-scratch feature: the gates are the parser throw plus
making "struct as array element type" legal everywhere, with consistent
stride handling.

## Change overview

### 1. Parser / declaration
- Remove (or relax) the `SyntaxError` at `Antlr2KotlinVisitor.kt:235-236` so a
  struct `DataType` can flow into the array-type builder.
- Allow `structtype name[size]` (and `const` / initialized forms).

### 2. DataType model (`codeCore/.../DataTypes.kt`)
- Represent "array of structs" as a `DataType` (extend `arrayFor` to accept a
  struct `DataType`, or add `arrayOfStructs`). Today `arrayFor` takes a
  `BaseDataType`; a struct instance is a full `DataType`.
- Ensure `elementType()` returns the struct `DataType`.
- Ensure `memsize` of the array = `length * struct.size`.

### 3. Memory layout & sizing
- Variable declaration must size the array as `length * struct.size` and align
  the base to the struct's alignment. The element-size plumbing already exists
  (`IRCodeGen.kt:752,760`); once the element type is legal it computes the
  right total.
- The `IRStStaticVariable` / static-var representation must carry the struct
  element type so sizing and alignment are correct.

### 4. Indexing / element stride (the core change)
- Every array-index operation multiplies the index by the **element stride**.
  That stride must become `struct.size` (possibly > 4 bytes), not the fixed
  1/2/4 of a numeric base type.
- `LOADX` / `STOREX` and pointer arithmetic must use this stride. For structs
  you typically compute the element base `base + index*struct.size` and then
  access fields within it, rather than a single wide load. The field-access
  machinery in `ExpressionGen.kt:161-185` and `:1850-1922` is the template to
  reuse.

### 5. Member access `arr[i].field`
- Parser: allow `.field` after an array indexer whose element is a struct.
- Codegen: combine `i * struct.size + fieldOffset` into one address using the
  existing struct-field offset logic.

### 6. Whole-struct operations
- Assignment `arr[i] = otherStruct` (and array element as LHS) → **memcopy of
  `struct.size` bytes**, not a scalar move. Struct-to-struct copy already
  exists elsewhere; it must handle the array-element LHS address
  (`base + i*struct.size`).
- `PtArrayIndexer` already permits struct element types, so the AST node is
  ready.

### 7. Initializers
- Array-of-struct literal: each element is a struct initializer; emit
  `struct.size` bytes per element into `.data` / `.bss`, with correct
  alignment.

### 8. VM & backends (6502 / m68k / new6502)
- VM array load/store and `memcopy` are byte-addressable, so `struct.size`-
  stride copies work; verify `LOADX` / `STOREX` use the element byte size
  (not assume a 1-byte stride).
- Backends: element address math uses stride = `struct.size`; on 6502 this
  needs 16-bit offset handling (structs can exceed 256 bytes), on m68k 32-bit
  is trivial. Pointer / `loadIndexToD0` paths must use the struct stride.

### 9. Tests
- Type tests (`elementType` for array-of-struct).
- Codegen tests: declare, index, `arr[i].field`, assign, iterate, initialize.
- VM execution tests for correct values after the above.

## Bottom line

The single hard blocker is one parser throw. The datatype/sizing path already
multiplies by element size in the right places. The real work is: (a) make
struct a legal array element type end-to-end, (b) ensure the *stride* is
`struct.size` consistently in all index math (not just field access),
(c) whole-struct copy for element LHS/RHS, and (d) initializers. The existing
`index*struct.size` code in `ExpressionGen` is the foundation to build on.
