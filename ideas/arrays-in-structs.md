
# Requirements

### Overview & Goals
Support inline arrays by-value inside struct fields. Currently, struct fields can only hold scalar types (bool, byte, ubyte, word, uword, long, float) and pointers. Arrays embedded directly in a struct's memory layout are not supported. The goal is to allow:

```prog8
struct Entry {
    ubyte age
    ubyte[10] name          ; 10 bytes inline, not a pointer
    word[4]  scores
}
```

When a struct instance is created, the array data is stored contiguously within the struct's memory block, not as a separate pointer to external data. This differs from `str` fields, which only store a 2-byte pointer.

### Scope

#### In Scope
- 1D arrays of basic types (`ubyte[N]`, `word[N]`, `uword[N]`, `long[N]`, `float[N]`, `bool[N]`) as struct fields
- `@nosplit` tag for word arrays in structs
- `ubyte[][] name` (empty array sig, size inferred from initializer)
- Reading and writing individual elements: `entry.name[i] = val`, `val = entry.name[i]`
- Initialization of struct instances with array fields via `^^Entry : [...]`
- `sizeof(Entry)` and `offsetof(Entry.name)` for structs with array fields
- Struct total size still limited to 256 bytes

#### Out of Scope
- 2D arrays as struct fields initially (but grammar can accept them for future use)
- Entire-array operations on array fields (e.g., `copy entry.name, source` on the field) -- use element-by-element copying
- Arrays of structs inside structs
- `str` fields changed from pointer to inline (keep `str` as pointer)
- Variable-length array fields
- Array fields of struct pointer types (`^^Foo[N]`)

### User Stories
- As a Prog8 developer, I want to store small fixed-size arrays inline in structs so that I can model data structures like file system entries, game objects with stats, or C-style structs without needing separate memory allocations.
- As a Prog8 developer, I want `sizeof` and `offsetof` to correctly reflect the full inline storage of array fields so that I can use them in assembly and memory layout calculations.
- As a Prog8 developer, I want to read and write individual array elements in a struct field using natural `instance.field[i]` syntax.


# Technical Design

### Current Implementation
Struct fields are stored as `Array<Pair<DataType, String>>` in `StructDecl` (compiler AST) and `List<Pair<DataType, String>>` in `StStruct` (simple AST). The `DataType` does not store array dimensions; that information lives separately in `VarDecl.arraysize`. For structs, there is no such separate storage.

Key restrictions currently in place:
1. **Grammar** (`Prog8ANTLR.g4:174`): `structfielddecl: datatype identifierlist` -- no array index syntax
2. **AstChecker** (`AstChecker.kt:2301`): rejects non-basic, non-numeric, non-pointer field types
3. **Memory layout** (`StructDecl.memsize()`:527, `offsetof()`:533): passes `numElements=1` to `sizer.memorySize()` -- correct for scalar fields, but for array fields would need to pass the actual array dimension instead

Field access `ptr.field[idx]` on a struct pointer hits an error/TODO at `AstChecker.kt:1583-1605` because the `.` operator with an array-indexed right side is not handled for struct pointers.

### Key Decisions
- **`StructField` data class**: Introduce a proper data class for fields to carry array dimension alongside type and name, rather than continuing with `Pair<DataType, String>`. This provides a clean extension point and avoids parallel arrays.
- **Grammar mirrors `vardecl`**: The `(arrayindex arrayindex? | EMPTYARRAYSIG)?` suffix follows the exact pattern used for variable declarations, keeping the language consistent.
- **Array dimension in DataType**: Use `DataType.elementToArray()` to create array types (with `isArray=true`, `sub=elementType`) for array fields, just like `VarDecl` does. The concrete dimension lives in `StructField.arraySize`.
- **Element-level access only**: `ptr.field` on its own (without index) is not a valid rvalue for array fields; you must write `ptr.field[i]`. This keeps code generation simple and avoids the need for struct-field-as-array-reference semantics.
- **Initialization**: Use nested ArrayLiteral values for array fields in `^^Type : [...]` initializer. The invariant `args.size == fields.size` is preserved -- each field gets one argument. For `ubyte[N]` fields, string literals are also accepted as initializer shorthand, auto-expanded to individual bytes.

### Proposed Changes

#### 1. Grammar (`Prog8ANTLR.g4:174`)
```
- structfielddecl: datatype identifierlist ;
+ structfielddecl: datatype (arrayindex arrayindex? | EMPTYARRAYSIG)? identifierlist ;
```

#### 2. `StructField` data class (new, or inline in `StructDecl`)
```kotlin
data class StructField(
    val type: DataType,
    val name: String,
    val arraySize: ArrayIndex? = null   // null = scalar
) {
    val isArray: Boolean get() = arraySize != null
}
```

**`StructDecl` changes** (`AstStatements.kt`):
- `fields: Array<StructField>` replaces `Array<Pair<DataType, String>>`
- `memsize()`: element-count-aware: `if(field.isArray) sizer.memorySize(field.type, resolvedSize) else sizer.memorySize(field.type, 1)`
- `offsetof()`: same treatment for field offset accumulation
- `getFieldType()`: unchanged (already delegates to `DataType`)
- `copy()`, `sameas()`: updated for new field type
- `offsetof()` constraint: field offsets must remain ≤ 255 (Y-register limit)

#### 3. `Antlr2KotlinVisitor.getStructField()` (`Antlr2KotlinVisitor.kt:801`)
- Read `ctx.arrayindex()` from context
- If array indices present, convert base DataType via `elementToArray()` (same as line 616/926)
- Return `StructField(dt, name, arraySize)` instead of `Pair<DataType, String>`

#### 4. `StStruct` changes (`SymbolTable.kt`)
- `fields: List<StructField>` replaces `List<Pair<DataType, String>>`
- `getField()`: compute offsets with element-count-aware `sizer.memorySize()`
- Update `size` calculation in constructor and `SymbolTableMaker`

#### 5. AstChecker changes (`AstChecker.kt`)

**Field type validation (line 2301)**:
```kotlin
struct.fields.forEach { field ->
    val dt = field.type
    val allowed = dt.isBasic
                  || dt.base.isNumericOrBool
                  || dt.isPointer
                  || (dt.isArray && dt.sub!!.isNumericOrBool)
    if(!allowed)
        errors.err("only booleans, numeric and pointer fields allowed in a struct: '${field.name}'", struct.position)
}
```

**`.` operator with array field (lines 1583-1605)**:
Handle the case where `left` is a struct pointer and `right` is `ArrayIndexedExpression("field", idx)`. Validate the field exists and is an array type. Set the result type to the element type of the array.

#### 6. 6502 Code Generator (`PointerAssignmentsGen.kt`)

`deref()` and `operatorDereference()` for `ptr.field[idx]`:
- Compute address as `base_pointer + field_offset + (index * element_size)`
- If `field_offset + (max_index * element_size) > 255`, the Y-register can't hold the full offset; emit code to add the high byte to the pointer
- This is already handled for general array indexing on pointers; struct field offset just needs to be added to the base

#### 7. IR Code Generator (`ExpressionGen.kt`, `AssignmentGen.kt`)

`traverseRestOfDerefChainToCalculateFinalAddress()`:
- When a field in the deref chain is an array and has an index, add `index * element_size` to the computed address
- Scale the index by element size before adding to the field offset

#### 8. StaticStructInitializer changes (`AstChecker.kt:2681`)

Current validation at line 2698 enforces `args.size == struct.fields.size`. With array fields, the invariant is preserved: each array field receives a single initializer value which is an ArrayLiteral (or string literal for `ubyte[N]`).

- Accept ArrayLiteral or string literal as initializer value for array fields
- Validate element count in ArrayLiteral matches the array dimension
- For `ubyte[N]` fields, accept a string literal as shorthand; expand/crop to N bytes
- Allow `[]` (empty array sig) for uninitialized array fields
- Update `SymbolTableMaker.handleStructAllocation()` to process nested initializer values

Example:
```prog8
struct Entry {
    ubyte age
    ubyte[10] name
    word[3]  scores
}

^^Entry entry = ^^Entry : [25, "Hello", [100, 200, 300]]
```

#### 9. `AstToplevel.searchStructFieldRef()` (line 227)
- Uses `getFieldType()` which returns `DataType` already -- should work if `getFieldType` returns the array DataType
- The `StructFieldRef` node gets the field's type; subsequent array indexing should resolve the element type

#### 10. `AstToSourceTextConverter`
- Update `visit(StructDecl)` to emit array dimensions for struct fields

### Data Models

```kotlin
data class StructField(
    val type: DataType,
    val name: String,
    val arraySize: ArrayIndex? = null
)
```

Used in:
- `StructDecl.fields: Array<StructField>` (compiler AST)
- `StStruct.fields: List<StructField>` (simple AST)
- `Antlr2KotlinVisitor.getStructField()` return type

The `ArrayIndex` class already exists (`AstStatements.kt:600`) with `indexExpr: Expression` and supports const-evaluation.


# Migration / Compatibility

No breaking changes to existing code. All existing struct declarations continue to work unchanged. The change is purely additive:
- Existing scalar/pointer fields: `isArray=false` in `StructField`, behavior identical to current `Pair<DataType, String>`
- New array fields: only accessible when explicitly declared with `type[N] varname`

Existing `AstChecker` tests that check the "only booleans, numeric and pointer fields" error message need updating to also permit array types.

Existing tests accessing `struct.fields[i].first` / `.second` need updating to use `.type` / `.name` instead (or add backward-compat extension properties).


# Testing

### Validation Approach
Primary testing uses the **virtual target** and prog8c's IR debugging features. This allows fast iteration without needing 6502 emulators.

**Debugging workflow for each change:**
1. **Syntax check**: `prog8c -check input.p8` -- quick semantic validation
2. **AST inspection**: `prog8c -target virtual -printast1 input.p8` -- inspect compiler AST after parsing
3. **IR inspection**: `prog8c -target virtual -out /tmp/out input.p8` then examine the generated `.p8ir` file
4. **IR comparison**: `prog8c -target virtual -compareir baseline.p8ir new.p8ir` -- compare IR instruction changes
5. **VM execution**: `prog8c -target virtual -emu input.p8` -- run and see stdout output
6. **VM trace**: `prog8c -vm input.p8ir -vmtrace` -- step through IR instructions with location tracking
7. **Disable optimizations**: `prog8c -target virtual -noopt -emu input.p8` -- isolate optimizer issues
8. **Full regression**: `gradle build --console=plain` -- run all unit tests

**Important**: Always test with `-noopt` first to isolate optimizer issues from code generation bugs. Use `-compareir` to see what instructions change when optimizations are applied.

Final verification against the existing test suite via `gradle build --console=plain`.

### Key Scenarios

| Scenario | Test |
|----------|------|
| Basic array field | `struct S { ubyte[4] data }` -- create instance, write/read elements |
| Word array | `struct S { word[3] vals }` -- test split word and `@nosplit` |
| Mixed fields | `struct S { ubyte id; ubyte[8] name; word count }` -- correct offsets |
| Nested struct with array | `struct Inner { ubyte[4] data }` then `struct Outer { ^^Inner ptr }` |
| sizeof/offsetof | `sizeof(S)` = 8 for `ubyte[8]`, `offsetof(S.data)` = 1 if preceded by `ubyte id` |
| Initialization | `^^S : [1, 'a','b','c','d', 1000]` -- array field from byte values |
| Error: too large | `struct S { ubyte[300] data }` -- rejects (> 256 bytes) |
| Error: invalid type | `struct S { str[3] names }` -- rejects arrays of strings (complex) |
| Error: non-const array size | `struct S { ubyte[n] data }` -- rejects non-constant dimensions |
| Field offset > 255 | struct with carefully laid out array fields that push offset past 255 (requires extra pointer math) |
| Existing structs unchanged | All existing struct tests continue to pass |

### Test Changes
- New VM test class or extend `TestCompilerVirtual.kt` for array-in-struct runtime tests
- New codegen test in `TestExecution6502` for 6502 code generation with array fields
- Update `TestPointers.kt` struct tests if field access patterns change
- Update `TestAstChecks.kt` for relaxed field type validation
- Update `TestConst.kt` for `sizeof`/`offsetof` with array fields


# Delivery Steps

### Step 1: Grammar, Parser, and AST Changes
The parser and AST now support array index declarations in struct fields.

- Update `structfielddecl` grammar rule in `Prog8ANTLR.g4`.
- Create `StructField` data class to manage array dimensions.
- Update `StructDecl` to store `StructField`s.
- Update `Antlr2KotlinVisitor.getStructField()` to process array indices.
- Update `AstToSourceTextConverter` to emit array dimensions.
- **Verify**: parser successfully parses `struct S { ubyte[10] name }`.

### Step 2: Semantic Analysis and Validation
The compiler correctly validates array field types and struct initializers.

- Relax field type check in `AstChecker.struct` visitor to permit array types.
- Handle `.` operator with `field[idx]` for struct pointers.
- Handle `StaticStructInitializer` validation for array field initializers.
- Update `AstToplevel.searchStructFieldRef()` for proper symbol resolution.
- **Verify**: valid structs compile, and invalid declarations produce appropriate errors.

### Step 3: Memory Layout and Symbol Table
Struct field offsets and total sizes correctly account for array fields.

- Update `StructDecl.memsize()` and `offsetof()` to compute array field sizes.
- Update `StStruct` and `SymbolTableMaker` to handle array field storage.
- **Verify**: `sizeof` and `offsetof` return correct values based on element counts.

### Step 4: 6502 Code Generation
The 6502 backend correctly handles array field offsets in pointer assignments.

- Update `PointerAssignmentsGen` to handle array field offsets with indexing.
- Handle offset > 255 case by emitting extra pointer math.
- Verify generated 64tass `.struct` directives and `.dstruct` data layouts.
- **Verify**: compiled programs run correctly in the 6502 simulator/emulator.

### Step 5: IR + VM Code Generation
The IR generator correctly calculates addresses for array field indexed access.

- Update IR expression and assignment generators to handle array field access.
- **Verify using IR debugging workflow**:
  - `prog8c -check` for syntax errors.
  - `prog8c -target virtual -printast1` to inspect AST nodes.
  - `prog8c -target virtual -out` then inspect `.p8ir` for correct indexing opcodes.
  - `prog8c -target virtual -emu` to run and verify output.
  - `prog8c -vm file.p8ir -vmtrace` to trace execution and verify field access addresses.

### Step 6: Testing
All new functionality is covered by automated unit and integration tests.

- Add VM runtime tests for array-in-struct field access.
- Add 6502 simulator-based tests.
- Add error-case coverage for invalid field declarations.
- Run full suite: `gradle build --console=plain`.