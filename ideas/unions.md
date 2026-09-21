# Prog8 Unions - Implementation Plan

## Goal

Add `union` as a new type declaration that behaves like a `struct` where every field starts at byte offset 0. The storage size is the largest field size, not the sum.

## Initialization decision

**Option C: no static initialization allowed.**

- `^^U : []` is allowed and means "zero-initialized" (placed in BSS).
- Any initializer that provides values is rejected, even a single value.
- Named-field initializers are not supported for unions.
- Values must be written at runtime through the pointer.

This removes all ambiguity about overlapping initial values and is the simplest path to a correct implementation.

## Syntax

```prog8
union Identifier {
    datatype field1
    datatype field2
    ...
}
```

Same visibility rules as `struct` (`public`/`private`, default public). Same field type restrictions as `struct` (no nested struct instances, no arrays of struct instances, no 2D arrays, no `^^type[]` arrays). Inline 1D arrays are allowed.

## Semantics

- `offsetof(Union.field)` is always `0`.
- `sizeof(Union)` is `max(sizeof(field))`.
- A union type can be used wherever a struct type is used today: typed pointers, pointer arrays, arrays of union instances, subroutine parameters, and return values (all via pointer).
- Whole-union copy semantics are the same as whole-struct copy (memory copy of `sizeof(Union)` bytes).

## Frontend changes

### 1. Grammar

File: `parser/src/main/antlr/Prog8ANTLR.g4`

- Add `UNION: 'union';` token near `STRUCT` (line 27).
- Add `uniondeclaration` rule mirroring `structdeclaration` (line 172).
- Add it to `block_statement` and `statement`.
- Add an error-recovery case `UNION '{'` mirroring `STRUCT '{'` (line 150).

### 2. Parser visitor

File: `compilerAst/src/prog8/ast/antlr/Antlr2KotlinVisitor.kt`

- Add `visitUniondeclaration` that produces a `StructDecl` with an `isUnion = true` flag.
- Reuse `StructDecl` rather than introducing a new AST node. Most passes (LSP, validators, desugarer) dispatch on `StructDecl`.

### 3. Compiler AST `StructDecl`

File: `compilerAst/src/prog8/ast/statements/AstStatements.kt` (line 546)

- Add constructor parameter `val isUnion: Boolean = false`.
- Update `memsize()`:
  - If union: return `max` of individual field sizes.
  - Otherwise keep the current `sum`.
- Update `offsetof()`:
  - If union: return `0` for every declared field.
  - Otherwise keep sequential offset computation.
- Update `sameas()` to include the union flag.
- Update `copy()` to preserve the flag.

### 4. Validation

File: `compiler/src/prog8/compiler/astprocessing/AstIdentifiersChecker.kt` (line 191)

- In `visit(StaticStructInitializer)`:
  - If the target struct is a union, reject any initializer that has values.
  - Reject named-field initializers for unions.
  - Only allow `[]` (zero init).

File: `compiler/src/prog8/compiler/astprocessing/AstChecker.kt`

- Existing struct checks already apply (no nested instances, no value-type parameters, etc.).
- Add a small check that a union has at least one field.

File: `compiler/src/prog8/compiler/astprocessing/NamedStructInitializerFlattener.kt`

- Skip/bail out for unions; the checker above will have already rejected named usage.

### 5. Builtins

File: `compiler/src/prog8/compiler/BuiltinFunctions.kt` (lines 118, 130)

- `offsetof` and `sizeof` are fixed automatically once `StructDecl.offsetof()` and `memsize()` are updated.
- No code changes required.

## Simple AST / symbol table

File: `simpleAst/src/prog8/code/SymbolTableMaker.kt` (line 89)

- Compute `StStruct.size` as `max` for unions.

File: `simpleAst/src/prog8/code/SymbolTable.kt` (line 266)

- Add `isUnion: Boolean` to `StStruct`.
- Update `getField()` to return offset `0` for every field when `isUnion` is true.
- Update `sameas()` to include the flag.

File: `simpleAst/src/prog8/code/ast/AstStatements.kt`

- Add `isUnion` to `PtStructDecl` (if it does not already carry a generic flag bag).

## Intermediate representation

File: `intermediate/src/prog8/intermediate/IRSymbolTable.kt` (line 163)

- Add `isUnion: Boolean` to `IRStStructDef`.

File: `intermediate/src/prog8/intermediate/IRProgram.kt` (line 763)

- `IRStructSubtype.memsize()` already uses `def.size`; no change.
- `IRStructSubtype.getFieldType()` is unaffected.

File: `intermediate/src/prog8/intermediate/IRFileWriter.kt` (line 494)

- Serialize `union=true` in the `STRUCTDEFS` line.
- IR file format version bump is required if the reader is strict.

File: `intermediate/src/prog8/intermediate/IRFileReader.kt` (line 333)

- Parse the optional `union=true` flag.

## Backend changes

### 6502 old backend (`codeGenCpu6502`)

File: `codeGenCpu6502/src/prog8/codegen/cpu6502/ProgramAndVarsGen.kt` (line 360)

- In `structInstances2asm()`:
  - Emit `.union`/`.endunion` type definitions for unions (same reason struct definitions are emitted: so inline assembly can reference `#p8t_MyUnion.p8v_field` and `size(p8t_MyUnion)`).
  - All union instances are emitted without init values into BSS (`.fill ${size}`).
  - `.dunion` is not needed because static initialization is disallowed.
- Remove or skip union instances from the initialized `STRUCTINSTANCES` section.

64tass supports `.union`/`.endunion`, so no assembler workaround is needed.

### 6502 new backend (`codeGenNew6502`)

File: `codeGenNew6502/src/prog8/codegen/new6502/AsmGen.kt` (lines 934, 1045)

- `emitStructDefs()`: emit `.union`/`.endunion` for union types, so inline assembly can reference union field labels and size.
- All union instances are zero-initialized in BSS (`.fill ${size}`). No initialized union instance section is emitted.

### M68K backend (`codeGenM68k`)

File: `codeGenM68k/src/prog8/codegen/m68k/AsmGen.kt` (lines 1039, 1393)

- All union instances are zero-initialized in BSS (`ds.b ${size}`).
- The m68k backend does not emit struct/union type definitions today. This is an existing omission; it can be fixed separately by emitting VASM `equ` symbols for size and field offsets (e.g. `p8b_main_p8t_MyUnion_size equ 2`, `p8b_main_p8t_MyUnion_p8v_field equ 0`). Until then, union behavior remains consistent with the existing struct behavior on m68k.

### Virtual machine (`virtualmachine`)

- The VM does not recompute struct field offsets at runtime; offsets are constants in IR `LOADM`/`STOREM` instructions.
- Only requirement: `IRStStructInstance.size` must equal the union's max field size. This is already handled by the frontend/SimpleAST changes.

## Tests

### Unit tests

- `compiler/test/TestPointers.kt`:
  - `offsetof(Union.field)` is 0 for every field.
  - `sizeof(Union)` equals the largest field size.
- `compiler/test/codegeneration/TestStructsWithArrays.kt`:
  - Arrays of union instances behave correctly.
- `compiler/test/ast/TestConst.kt`:
  - `sizeof`/`offsetof` on unions are compile-time constants.

### Execution tests

- `compiler/test/codegeneration/TestExecution6502.kt`:
  - Declare a zero-initialized union, write field A at runtime, read field B; verify overlap.
  - Copy a union instance to another via pointer dereference.
  - Test union in an array of instances.
- `compiler/test/vm/TestCompilerVirtual.kt`:
  - Same behavioral tests on the virtual target.
- `codeGenM68k` tests if m68k-specific emission is touched.

### IR round-trip

- `intermediate/test/TestIRFileInOut.kt`:
  - Write and read back a program containing a union; verify `isUnion` flag and size.

### Error tests

- Any union initializer with values is rejected, even a single value.
- Named-field initialization on a union is rejected.
- Accessing an unknown field on a union still produces the normal "no such field" error.

## Risks and open questions

1. **Nested unions / unions inside structs.**
   - A struct field that is a pointer-to-union is fine.
   - An inline union inside a struct is out of scope for this first version because Prog8 does not support inline struct instances as fields today.

2. **Pointer arithmetic on `^^Union`.**
   - Already handled: pointer increment uses `sizeof(Union)`, which is now the max field size.

3. **Alignment on M68K.**
   - Union instances are aligned the same way struct instances are (`ALIGN 2`). This is the existing behavior for packed structs; no new issue.

4. **Assembly references to union fields.**
   - `#p8t_MyUnion.p8v_field` resolves to the field offset (0) inside the union definition. Using `.union` in 64tass makes this correct.

5. **Initialization policy.**
   - This plan disables all static initialization of unions. If desired later, C-style first-member or named-member initialization can be added as a separate feature.

## Roll-out checklist

1. Grammar + parser visitor.
2. `StructDecl` union flag + `memsize`/`offsetof` changes.
3. SimpleAST `StStruct` / `PtStructDecl` changes.
4. Validation: reject all union initializers that provide values; only `[]` is allowed.
5. IR `IRStStructDef` flag + file format bump.
6. 6502 old backend `.union`/`.endunion` type-definition emission.
7. new6502 / M68K union instance emission.
8. Tests: unit, execution, IR round-trip, error cases.
9. Update `docs/source/structpointers.rst` and `docs/source/libraries.rst`.
10. Update `docs/source/history.rst` and `docs/source/todo.rst` if needed.
