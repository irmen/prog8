# Implementation Plan: Remove `ARRAY_POINTER` from Type System

**Status: TODO / not implemented.** Listed as an active item in `docs/source/todo.rst` (line 6).

**Note (plan refresh, Sep 2026):** the original version of this plan had stale
file/line references and missed several touch points. All locations below were
re-verified with `git grep -n "ARRAY_POINTER\|isPointerArray\|arrayOfPointers"`.
Regenerate that command before implementing, line numbers drift quickly.
Two files the original plan listed do **not** need changes and were removed:
`simpleAst/.../SymbolPrefixer.kt` (`typePrefixChar` handles `StNodeType`, not
datatypes) and `intermediate/.../IRTextCodec.kt` (`typeSuffix` handles
`IRDataType`, not `DataType`). There is no `vm32.md` in the repo, so that doc
item was dropped. `scripts/amigalibs2prog8.py` only deals with C pointers, not
`ARRAY_POINTER`.

## Overview

**Goal**: Remove the target-dependent `ARRAY_POINTER` type and replace it with `ARRAY` using a pointer element type, making the type system target-independent.

**Current Problem**: `ARRAY_POINTER` has different memory layouts on different targets (split word on 6502, regular long on m68k/virtual), requiring target checks throughout the codebase. This is described as "horrible" in `todo.rst`. The target decision is baked into the base type: `elementToArray()` (DataTypes.kt:253) branches on `target.cpu.is6502` to pick `ARRAY_POINTER`. Concretely, `PtVariable.isSplitWordArray` (`simpleAst/src/prog8/code/ast/AstStatements.kt:382`) and `PtArrayIndexer.splitWords` (`simpleAst/src/prog8/code/ast/AstExpressions.kt:235`) carry explicit booleans only because the enum value alone cannot tell whether the array is split.

**Solution**: Use `ARRAY(sub=POINTER, ...)` with a new field to store the pointee base type, making the layout decision explicit at allocation/codegen time via the existing `isSplitWordArray(memsizer)` predicate.

**Decisions**:
1. Remove `ARRAY_POINTER` entirely (no deprecated alias)
2. No backward compatibility for old `.p8ir` files (bump IR version, fail loudly on `^^type[N]` input rather than misparsing it)
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

**Key Change**: Add `pointeeSub: BaseDataType?` field to `DataType` to store what the pointers point to. (Rationale: `POINTER` already encodes its pointee as `(sub, subType)`, but an `ARRAY`'s `sub` slot will be occupied by `POINTER` itself and `subType` only holds structs, so a new field is needed. This creates two fields that must stay in sync - see risks.)

---

## Verified reference inventory

Full list of touch points (verified Sep 2026, excluding `compiler/test/comparisons` generators):

| File | Lines | What |
|------|-------|------|
| `codeCore/.../core/DataTypes.kt` | 23, 94, 97-98, 135, 227-230, 253-283, 285-295, 297-322, 324-333, 335-366, 368-422, 428-449, 460-466, 495-504, 514-520 | enum, factories, init, equals/hashCode, elementToArray/elementType, toString/sourceString, assignability, size, predicates, isSplitWordArray |
| `codeCore/.../core/BuiltinFunctions.kt` | 27 | `IterableDatatypes` |
| `codeCore/.../target/NormalMemSizer.kt` | 13 | `memorySize` |
| `codeCore/.../testFixtures/.../Dummies.kt` | 15 | test memsizer |
| `codeCore/test/.../TestDataType.kt` | 25, 96, 105-116 | tests |
| `compilerAst/.../antlr/Antlr2KotlinVisitor.kt` | 243 | parser type creation |
| `compilerAst/.../expressions/AstExpressions.kt` | 1294-1298, 2165, 2202 | literal inference, prefix-target checks |
| `compiler/.../astprocessing/AstChecker.kt` | 320, 1055, 1113, 1217, 1224, 1351, 1659, 1822, 2710 | **9 sites** (not 8) |
| `compiler/.../astprocessing/CodeDesugarer.kt` | 132, 1167, 1384 | |
| `compiler/.../astprocessing/ImplicitForIteratorDecls.kt` | 72 | |
| `compiler/.../astprocessing/LiteralsToAutoVarsAndRecombineIdentifiers.kt` | 90 | |
| `compiler/.../astprocessing/VariousCleanups.kt` | 34-36, 96, 145 | incl. `@nosplit` on pointer arrays |
| `compiler/test/helpers/Dummies.kt` | 38 | test memsizer |
| `compiler/test/ast/TestVariousCompilerAst.kt` | 1618 | test |
| `intermediate/.../Utils.kt` | 18-22 | `irTypeString` |
| `intermediate/.../IRFileReader.kt` | 690-697 | `parseDatatype` `^` branch (was misquoted as 601-611) |
| `intermediate/.../IRFileWriter.kt` | 322-328, 375-402 | split `_lsb`/`_msb` emission (verify-only, works via predicate) |
| `codeGenCpu6502/.../ProgramAndVarsGen.kt` | 802 | uninitialized array emission (a `TODO`, was misquoted as 831-832) |
| `codeGenIntermediate/.../ExpressionGen.kt` | 934 | cast to pointer array (a `TODO` - needs semantics decision, see risks) |
| `codeGenIntermediate/.../IRCodeGen.kt` | ~752 | for-loop `isSplitWordArray` (verify-only) |
| `codeGenIntermediate/.../SymbolPrefixer.kt` | 265 | computes split flag from predicate (verify-only) |
| `codeGenCpu6502/.../assignment/AugmentableAssignmentAsmGen.kt` | 1756 | `PtArrayIndexer` split flag from predicate (verify-only) |
| `codeGenCpu6502/.../assignment/BinaryOpAssignmentsGen.kt` | 231 | same (verify-only) |
| `virtualmachine/.../VmProgramLoader.kt` | 406, 615 | zeroing + init (**2 sites**, old plan listed only the first with wrong lines) |

---

## Phase 1: Core Type System Changes

**File**: `codeCore/src/prog8/code/core/DataTypes.kt`

### Changes:
1. **Remove `ARRAY_POINTER` from `BaseDataType` enum** (line 23)
2. **Remove it from `isArray` / `isIterable`** (lines 94, 98)
3. **Remove `isPointerArray` alias from `BaseDataType`** (line 97)
4. **Add `pointeeSub: BaseDataType?` field to `DataType`** (constructor, line 126-131)
5. **Update `init` validation** (lines 133-153): `ARRAY` with `sub==POINTER` requires `pointeeSub` (or struct `subType`/`subTypeFromAntlr`); `POINTER` must remain the only other base allowed a `sub`
6. **Update `equals` / `hashCode`** (lines 155-166) to include `pointeeSub`
7. **Replace `arrayOfPointersTo()` / `arrayOfPointersFromAntlrTo()` factories** (lines 227-230) with versions that create `ARRAY` with `POINTER` subtype + `pointeeSub`
8. **Update `elementToArray()`** (lines 253-283): stop branching on `target.cpu.is6502` for pointer elements, always build the new representation. Keep the `STR -> pointerBaseType` mapping in `arrayFor()` distinct: a `STR` array that becomes a `LONG` array on 32-bit targets is a plain `LONG` array, **not** a pointer array
9. **Update `elementType()`** (lines 285-295): for `ARRAY` with `sub=POINTER`, return `DataType(POINTER, sub=pointeeSub, subType=subType)`
10. **Update `typeForUntypedAddressOf()`** (lines 297-322, esp. line 310) and `dereference()` (lines 324-333) for the new representation
11. **Update `toString()` and `sourceString()`** (lines 335-422, esp. 359-361 and 393-400): handle `ARRAY` with `sub=POINTER`
12. **Update `isAssignableTo()`** (lines 428-449, esp. 433, 435, 446): replace `ARRAY_POINTER` cases with `ARRAY` + `sub=POINTER` checks, comparing `(pointeeSub, subType)` with struct `sameas`
13. **Update `size()`** (lines 460-466): with `sub==POINTER` it currently returns the pointer size, decide whether it must return `pointeeSub` size or pointer size and document it
14. **Add `DataType.isPointerArray` extension property**: `isArray && sub==POINTER`
15. **Update `isSplitWordArray()`** (lines 514-520): check `sub==POINTER && memsizer.POINTER_MEM_SIZE<=2u`
16. **Audit every `is*Array` predicate** (lines 495-504): each needs the `sub!=POINTER` guard. Note `isLongArray` (line 503) already lacks it today, an array of pointers-to-long must **not** count as a long array. Same for any future predicate
17. **Update `arrayFor()`** (lines 208-220): its `require(!elementDt.isPointer)` becomes obsolete, decide the new contract (reject bare `POINTER` element, require going through the pointer-array factory so `pointeeSub` is always set)

### Verification:
- Compile `codeCore` module: `gradle :codeCore:compileKotlin --console=plain`
- Run `codeCore` tests: `gradle :codeCore:test --console=plain`

---

## Phase 2: Type Creation

### File: `compilerAst/src/prog8/ast/antlr/Antlr2KotlinVisitor.kt`
**Line 243**: `pointerDatatypeFor` / array creation path, use the new factory.

### File: `compilerAst/src/prog8/ast/expressions/AstExpressions.kt`
**Lines 1294-1298**: array literal type inference. **Lines 2165, 2202** (missed by old plan): prefix-target checks `isPointer || isPointerArray`, these keep working via the new extension property but verify the surrounding logic expects element types, not array types.

### Verification:
- Compile `compilerAst` module: `gradle :compilerAst:compileKotlin --console=plain`

---

## Phase 3: Type Checking and Validation

### File: `compiler/src/prog8/compiler/astprocessing/AstChecker.kt`
**9 references** (lines 320, 1055, 1113, 1217, 1224, 1351, 1659, 1822, 2710):
- Replace with `datatype.isPointerArray` (new extension property)

### File: `compiler/src/prog8/compiler/astprocessing/ImplicitForIteratorDecls.kt`
**Line 72**: for-loop iterator type checking, same replacement.

### Verification:
- Compile `compiler` module: `gradle :compiler:compileKotlin --console=plain`

---

## Phase 4: AST Transformations

### File: `compiler/src/prog8/compiler/astprocessing/CodeDesugarer.kt`
**Lines 132, 1167, 1384** (old plan listed only 2): prefix operator, pointer-var validation, struct dereference checks.

### File: `compiler/src/prog8/compiler/astprocessing/VariousCleanups.kt`
**Lines 34-36, 96, 145-152**: type compatibility and `@nosplit` handling. Line 145 (`isPointerArray && splitwish==NOSPLIT`) needs a decision: `NOSPLIT` on a pointer array now means "linear even on 6502", confirm that semantic survives.

### File: `compiler/src/prog8/compiler/astprocessing/LiteralsToAutoVarsAndRecombineIdentifiers.kt`
**Line 88**: struct field resolution for pointer arrays (`elementType().subType` still works via new `elementType()`).

### Verification:
- Compile `compiler` module: `gradle :compiler:compileKotlin --console=plain`

---

## Phase 5: Memory Sizing

### File: `codeCore/src/prog8/code/target/NormalMemSizer.kt`
**Line 13**: `if(dt.isPointerArray) return pointerSize * numElements`. With the new extension property this line works unchanged, but the `dt.sub` `when` below (which now can see `sub==POINTER`) needs a branch: `POINTER -> numElements * pointerSize`. Add it explicitly rather than relying on the first branch.

### File: `codeCore/src/testFixtures/kotlin/prog8tests/helpers/Dummies.kt`
**Line 15**: same shape, plus its `when(dt.sub)` lacks `POINTER`/`STRUCT_INSTANCE` arms, add them.

### File: `compiler/test/helpers/Dummies.kt`
**Line 38**: same as above.

### Verification:
- Compile and test `codeCore`: `gradle :codeCore:test --console=plain`

---

## Phase 6: IR Handling

**Wire format decision (required before coding):** today a pointer array serializes as `^^type[N]` (`Utils.kt:18-22`) and parses back at `IRFileReader.kt:690-697`. Options: (a) keep `^^type[N]` syntax with new in-memory semantics (smallest diff, but old files silently change meaning - acceptable only with a version bump), (b) new syntax such as `pointer[N]:type`. Pick one, bump the IR version, and make the reader reject the other form loudly. No backward compatibility either way, per the decisions above.

### File: `intermediate/src/prog8/intermediate/Utils.kt`
**Lines 18-22** (`irTypeString`): emit the decided format for `ARRAY` with `sub=POINTER`.

### File: `intermediate/src/prog8/intermediate/IRFileReader.kt`
**Lines 690-697** (`parseDatatype` `^` branch): parse the decided format into `ARRAY` with `POINTER` subtype + `pointeeSub`. Also check the struct-fallback arm (line 697, `IRSubtypePlaceholder`).

### Verify-only (no change expected, confirm by test):
- `IRFileWriter.kt` lines 322-328, 375-402 (split `_lsb`/`_msb` emission keys off `isSplitWordArray`, which keeps working)
- `IRCodeGen.kt` ~752 (split-word for-loop iteration, accepts `POINTER` element type already)

### Verification:
- Compile `intermediate` module: `gradle :intermediate:compileKotlin --console=plain`
- IR write/read round-trip test for `^ubyte[N]`, `^^Struct[N]`, `^str[N]` on cx16 and virtual targets

---

## Phase 7: Code Generation

### File: `codeGenCpu6502/src/prog8/codegen/cpu6502/ProgramAndVarsGen.kt`
**Line 802**: `dt.isPointerArray -> TODO(...)` for uninitialized arrays. The 6502 backend must emit split `_lsb`/`_msb` reservation here (or explicitly reject with a proper error, not `TODO`).

### File: `codeGenIntermediate/src/prog8/codegen/intermediate/ExpressionGen.kt`
**Line 934**: `ARRAY_POINTER -> TODO("typecast to array of pointers ...")`. Define the semantics first: what does a value cast to `^ubyte` mean under the new rep (address reinterpretation, no code)? Then implement it instead of porting the `TODO`.

### Verify-only:
- `codeGenIntermediate/.../SymbolPrefixer.kt:265`, `AugmentableAssignmentAsmGen.kt:1756`, `BinaryOpAssignmentsGen.kt:231` derive split flags from the predicate, confirm by inspection.

### simpleAst split flags (follow-up cleanup, optional):
`PtVariable.isSplitWordArray` and `PtArrayIndexer.splitWords` become derivable from `(type, memsizer)` once the base type is target-independent. Removing them is out of scope for this plan but should be filed as a follow-up; the todo.rst wording ("need an explicit boolean") is resolved by making the boolean redundant.

### Verification:
- Compile codegen modules: `gradle :codeGenCpu6502:compileKotlin :codeGenIntermediate:compileKotlin --console=plain`

---

## Phase 8: VM Updates

### File: `virtualmachine/src/prog8/vm/VmProgramLoader.kt`
**Lines 406** (array zeroing) **and 615** (array initialization, missed by old plan; old plan's 245-247/447-456 lines were wrong): both switch to the new `isPointerArray`. Semantics stay "array of 32-bit addresses" on the 32-bit VM.

### Verification:
- Compile `virtualmachine` module: `gradle :virtualmachine:compileKotlin --console=plain`

---

## Phase 9: Builtin Functions

### File: `codeCore/src/prog8/code/core/BuiltinFunctions.kt`
**Line 27**: `IterableDatatypes`, drop `ARRAY_POINTER` (plain `ARRAY` already covers iteration; confirm `for x in ptrArray` still typechecks via the `ARRAY` entry).

---

## Phase 10: Tests and Documentation

### Test Files:
- `codeCore/test/prog8tests/codecore/TestDataType.kt` (lines 25, 96, 105-116)
- `compiler/test/ast/TestVariousCompilerAst.kt` (line 1618)

### Documentation:
- `docs/source/todo.rst` (line 6): remove TODO item

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

Compile and run for multiple targets (behavioral check on virtual + cx16;
codegen check on c64 and qemu68k):
- `prog8c -target virtual -emu test.p8`
- `prog8c -target cx16 test.p8`
- `prog8c -target c64 test.p8`

Extended cases (from review): `^^Struct[]`, `^str[]`, `@nosplit` pointer array
on 6502, `for x in ptrArray` on cx16 + virtual + m68k, IR write/read round-trip.

---

## Risks

1. **`sub==POINTER` becomes legal in `ARRAY`, and much code assumes it isn't.** Every `sub!!` dereference, `forDt(sub!!)`, and `is*Array` predicate must be audited (Phase 1 items 13, 16, 17).
2. **`pointeeSub`/`subType` sync.** Two fields describe the pointee; `copy()` (there is no explicit `copy()` method, all construction goes through the private constructor + factories, audit every call site), `equals`/`hashCode`, and IR round-trip must propagate both.
3. **IR wire format must be decided explicitly** (Phase 6), with version bump and loud rejection of old files.
4. **Split-ness moves but doesn't disappear.** `isSplitWordArray` still checks `POINTER_MEM_SIZE`; all three memsizers (normal + 2 test dummies) must agree.
5. **Cast semantics** (`ExpressionGen.kt:934`) and **`@nosplit` on pointer arrays** (`VariousCleanups.kt:145`) need decisions, not just ports.
6. **`STR` arrays vs pointer arrays** must stay distinct on 32-bit targets (Phase 1 item 8).

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

**Total**: ~20 Kotlin source files + 1 documentation file, ~70 reference points
(counted via `git grep -n "ARRAY_POINTER\|isPointerArray\|arrayOfPointers"`;
re-run before implementing).

### By Category:
| Category | Files | References |
|----------|-------|------------|
| Type system | DataTypes.kt | 20+ |
| Type creation | Antlr2KotlinVisitor.kt, AstExpressions.kt | 4 |
| AST validation | AstChecker.kt, ImplicitForIteratorDecls.kt | 10 |
| AST transforms | CodeDesugarer.kt, VariousCleanups.kt, LiteralsToAutoVars.kt | 7 |
| IR handling | Utils.kt, IRFileReader.kt (+ IRFileWriter verify-only) | 3 + verify |
| Code generation | ProgramAndVarsGen.kt, ExpressionGen.kt (+ IRCodeGen, SymbolPrefixer, 2 AsmGen verify-only) | 2 + verify |
| VM | VmProgramLoader.kt | 2 |
| Memory sizing | NormalMemSizer.kt, Dummies.kt (2 files) | 3 |
| Builtin functions | BuiltinFunctions.kt | 1 |
| Tests | TestDataType.kt, TestVariousCompilerAst.kt | 4 |
| Documentation | todo.rst | 1 |
