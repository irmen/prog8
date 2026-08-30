# Implementation Plan: Remove `ARRAY_POINTER` from Type System

## Overview

**Goal**: Remove the target-dependent `ARRAY_POINTER` type and replace it with `ARRAY` using a pointer element type, making the type system target-independent.

**Current Problem**: `ARRAY_POINTER` has different memory layouts on different targets (split word on 6502, regular long on m68k/virtual), requiring target checks throughout the codebase. This is described as "horrible" in `todo.rst`.

**Solution**: Use `ARRAY(sub=POINTER, ...)` with a new field to store the pointee base type, making the layout decision explicit at allocation/codegen time.

**Decisions**:
1. Remove `ARRAY_POINTER` entirely (no deprecated alias)
2. No backward compatibility for old `.p8ir` files
3. Implement incrementally, phase by phase with verification

---

## New Type Representation

### Current
```kotlin
DataType(ARRAY_POINTER, sub=UBYTE)              // array of pointers to ubyte
DataType(ARRAY_POINTER, sub=STRUCT, subType=X)  // array of pointers to struct X
```

### New
```kotlin
DataType(ARRAY, sub=POINTER, pointeeSub=UBYTE)              // array of pointers to ubyte
DataType(ARRAY, sub=POINTER, pointeeSub=STRUCT, subType=X)  // array of pointers to struct X
```

**Key Change**: Add `pointeeSub: BaseDataType?` field to `DataType` to store what the pointers point to.

---

## Phase 1: Core Type System Changes

**File**: `codeCore/src/prog8/code/core/DataTypes.kt`

### Changes:
1. **Remove `ARRAY_POINTER` from `BaseDataType` enum** (line 23)
2. **Add `pointeeSub: BaseDataType?` field to `DataType`** (after line 196)
3. **Update `DataType` constructor and `copy()` method** to include new field
4. **Remove `arrayOfPointersTo()` factory methods** (lines 218-222) - replace with new versions that create `ARRAY` with `POINTER` subtype
5. **Update `elementType()`** (line 252-259):
   - For `ARRAY` with `sub=POINTER`: return `DataType(POINTER, sub=pointeeSub, subType=subType)`
6. **Update `isSplitWordArray()`** (line 476-482):
   - Check `sub==POINTER && memsizer.POINTER_MEM_SIZE<=2u` instead of `base==ARRAY_POINTER`
7. **Remove `isPointerArray` alias from `BaseDataType`** (line 93)
8. **Add `DataType.isPointerArray` extension property**: `isArray && sub==POINTER`
9. **Update `isAssignableTo()`** (lines 396-409):
   - Replace `ARRAY_POINTER` cases with `ARRAY` + `sub=POINTER` checks
10. **Update `toString()` and `sourceString()`** (lines 323-364):
    - Handle `ARRAY` with `sub=POINTER` to print as `^^subtype[]`

### Verification:
- Compile `codeCore` module: `gradle :codeCore:compileKotlin --console=plain`
- Run `codeCore` tests: `gradle :codeCore:test --console=plain`

---

## Phase 2: Type Creation

### File: `compilerAst/src/prog8/ast/antlr/Antlr2KotlinVisitor.kt`
**Line 232-233**: Update parser to create `ARRAY` with `POINTER` subtype instead of `ARRAY_POINTER`

### File: `compilerAst/src/prog8/ast/expressions/AstExpressions.kt`
**Lines 1284-1292**: Update array literal type inference to create `ARRAY` with `POINTER` subtype

### Verification:
- Compile `compilerAst` module: `gradle :compilerAst:compileKotlin --console=plain`

---

## Phase 3: Type Checking and Validation

### File: `compiler/src/prog8/compiler/astprocessing/AstChecker.kt`
**8 references** (lines 306, 992, 1050, 1146, 1258, 1525, 1688, 2540):
- Replace `isPointerArray` checks with `datatype.isPointerArray` (using new extension property)
- Or inline as `datatype.isArray && datatype.sub==POINTER`

### File: `compiler/src/prog8/compiler/astprocessing/ImplicitForIteratorDecls.kt`
**Lines 61-62**: Update for-loop iterator type checking

### Verification:
- Compile `compiler` module: `gradle :compiler:compileKotlin --console=plain`

---

## Phase 4: AST Transformations

### File: `compiler/src/prog8/compiler/astprocessing/CodeDesugarer.kt`
**Lines 132, 1015**: Update prefix operator and struct dereference checks

### File: `compiler/src/prog8/compiler/astprocessing/VariousCleanups.kt`
**Lines 34, 96, 145-152**: Update type compatibility and `@nosplit` handling

### File: `compiler/src/prog8/compiler/astprocessing/LiteralsToAutoVarsAndRecombineIdentifiers.kt`
**Line 88**: Update struct field resolution for pointer arrays

### Verification:
- Compile `compiler` module: `gradle :compiler:compileKotlin --console=plain`

---

## Phase 5: Memory Sizing

### File: `codeCore/src/prog8/code/target/NormalMemSizer.kt`
**Lines 13-14**: Update memory size calculation for `ARRAY` with `POINTER` subtype

### File: `codeCore/testFixtures/kotlin/prog8tests/helpers/Dummies.kt`
**Lines 15-16**: Update test memory sizer

### File: `compiler/test/helpers/Dummies.kt`
**Lines 38-39**: Update compiler test memory sizer

### Verification:
- Compile and test `codeCore`: `gradle :codeCore:test --console=plain`

---

## Phase 6: IR Handling

### File: `intermediate/src/prog8/intermediate/Utils.kt`
**Lines 18-22**: Update IR type string serialization
- Change `^^` prefix format to regular array format with pointer subtype

### File: `intermediate/src/prog8/intermediate/IRFileReader.kt`
**Lines 601-611**: Update IR file deserialization
- Parse new format and create `ARRAY` with `POINTER` subtype

### Verification:
- Compile `intermediate` module: `gradle :intermediate:compileKotlin --console=plain`
- Run `intermediate` tests if any

---

## Phase 7: Code Generation

### File: `codeGenCpu6502/src/prog8/codegen/cpu6502/ProgramAndVarsGen.kt`
**Lines 831-832**: Update uninitialized pointer array emission for 6502

### File: `codeGenIntermediate/src/prog8/codegen/intermediate/ExpressionGen.kt`
**Lines 908-910**: Update typecast to pointer array (currently TODO)

### File: `codeGenIntermediate/src/prog8/codegen/intermediate/IRCodeGen.kt`
**Line 752**: Update for-loop iteration to use new `isSplitWordArray` logic

### Verification:
- Compile codegen modules: `gradle :codeGenCpu6502:compileKotlin :codeGenIntermediate:compileKotlin --console=plain`

---

## Phase 8: VM Updates

### File: `virtualmachine/src/prog8/vm/VmProgramLoader.kt`
**Lines 245-247, 447-456**: Update variable zeroing and array initialization

### Verification:
- Compile `virtualmachine` module: `gradle :virtualmachine:compileKotlin --console=plain`

---

## Phase 9: Builtin Functions

### File: `codeCore/src/prog8/code/core/BuiltinFunctions.kt`
**Line 27**: Update `IterableDatatypes` array (remove `ARRAY_POINTER`)

---

## Phase 10: Tests and Documentation

### Test Files:
- `codeCore/test/prog8tests/codecore/TestDataType.kt` (lines 96, 110-112)
- `compiler/test/ast/TestVariousCompilerAst.kt` (line 1557)

### Documentation:
- `docs/source/todo.rst` (line 10): Remove TODO item
- `vm32.md` (lines 133-142): Update dev notes

### Verification:
- Run all tests: `gradle build --console=plain`

---

## Verification Strategy

After each phase:
1. Compile the affected module(s)
2. Run module-specific tests
3. After all phases: run full build with `gradle build --console=plain`

### Manual Testing:
Create test programs with pointer arrays:
```prog8
%zeropage basicsafe
%option no_sysinit

main {
    ^ubyte[3] ptrs
    ^structDef[2] structPtrs
    
    sub start() {
        ; test pointer array operations
    }
}
```

Compile for multiple targets:
- `prog8c -target cx16 test.p8`
- `prog8c -target virtual test.p8`
- `prog8c -target c64 test.p8`

---

## Risk Mitigation

1. **Incremental implementation**: Complete and verify each phase before moving to next
2. **Type safety**: Ensure `pointeeSub` field is properly propagated through all type operations
3. **Memory layout**: Verify split-word logic works correctly on all targets

---

## Estimated Effort

- **Phase 1-2**: 2-3 hours (core type system)
- **Phase 3-4**: 2-3 hours (validation and transforms)
- **Phase 5-6**: 1-2 hours (memory and IR)
- **Phase 7-8**: 2-3 hours (codegen and VM)
- **Phase 9-10**: 1-2 hours (tests and docs)
- **Total**: 8-13 hours

---

## Files Affected Summary

**Total**: ~18 Kotlin source files + 2 documentation files, ~60 reference points

### By Category:
| Category | Files | References |
|----------|-------|------------|
| Type system | DataTypes.kt | 15+ |
| AST validation | AstChecker.kt | 8 |
| AST transforms | CodeDesugarer.kt, VariousCleanups.kt, LiteralsToAutoVars.kt | 7 |
| IR handling | Utils.kt, IRFileReader.kt | 10 |
| Code generation | ProgramAndVarsGen.kt, ExpressionGen.kt, IRCodeGen.kt | 3 |
| VM | VmProgramLoader.kt | 2 |
| Memory sizing | NormalMemSizer.kt, Dummies.kt (2 files) | 3 |
| Type creation | Antlr2KotlinVisitor.kt, AstExpressions.kt | 2 |
| Builtin functions | BuiltinFunctions.kt | 1 |
| Tests | TestDataType.kt, TestVariousCompilerAst.kt | 3 |
| Documentation | todo.rst, vm32.md | 2 |
