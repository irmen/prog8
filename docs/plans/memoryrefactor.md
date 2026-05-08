# Memory Slab AST Refactoring Plan

Replace the `memory()` builtin function call with dedicated AST node types: `MemorySlabReservation` (declaration) and `MemorySlabRef` (reference).

## Motivation

- Replace 22+ scattered `nameInSource == listOf("memory")` string checks with proper type dispatch
- Enable correct const propagation for memory slab pointers
- Clean separation between slab declaration and slab reference
- Memory slabs are always constant (assembly label references)

## Design

### CompilerAst (2 new nodes)

1. **`MemorySlabReservation`** — `Statement`, placed in enclosing block scope by CodeDesugarer
   - Fields: `slabName: String`, `size: UInt`, `align: UInt`, `position`

2. **`MemorySlabRef`** — `Expression`, replaces `memory()` call
   - Fields: `slabName: String` only (name-only ref, size/align live in reservation)
   - `constValue()` returns null (address unknown at compile time)
   - `inferType()` returns UWORD

### SimpleAst (1 new node + existing mechanisms)

3. **`PtMemorySlabReservation`** — Statement, codegen no-op (slab emitted from symbol table)
   - Fields: `name: String`, `size: UInt`, `align: UInt`, `position`

No `PtMemorySlabRef` needed. `PtConstant(memorySlab=StMemorySlab(...))` replaces
`PtFunctionCall("memory")` in ALL contexts, including array/struct-alloc elements.

Note: `PtConstant` extends `PtNamedNode`, NOT `PtExpression`. For array/struct-alloc
elements, the SimplifiedAstMaker creates `PtConstant` via a special-case in the array
literal transform (not through `transformExpression()`). The `PtArray`'s `children`
list accepts `PtNode`, so adding `PtConstant` is type-safe.

Existing mechanisms:
- **Const variable**: `PtConstant(memorySlab=StMemorySlab(...))` — already exists
- **Array literal element**: `PtConstant(memorySlab=StMemorySlab(...))` via special-case in SimplifiedAstMaker array transform, → `StArrayElement.MemorySlab(name)` via SymbolTableMaker
- **Struct alloc argument**: `PtConstant(memorySlab=StMemorySlab(...))` via special-case in SimplifiedAstMaker array transform, → `StArrayElement.MemorySlab(name)` via SymbolTableMaker
- **Non-const var with memory() initializer**: auto-promote to `PtConstant` (value is always constant)

SymbolTableMaker changes:
- `makeInitialArray()` adds an `is PtConstant ->` branch that extracts `memorySlab.name`
- Standalone memory() call handling changes from `is PtFunctionCall && isMemoryCall` to `is PtMemorySlabReservation`
- `createMemorySlabFromCall` is removed (replaced by PtMemorySlabReservation handler)
- The `structalloc` arg validation checks `is PtConstant` instead of `is PtFunctionCall && isMemoryCall`

## Implementation Phases

### Phase 1: SymbolTableMaker + Codegen + AstPrinter

1. Add `PtMemorySlabReservation` to `simpleAst/.../AstStatements.kt`  **DONE**
2. Add `PtMemorySlabReservation` to `AstPrinter.kt`  **DONE**
3. Update `SymbolTableMaker`:
   - Handle `PtMemorySlabReservation` → creates `StMemorySlab`, added to global scope
   - Add `is PtConstant ->` branch in `makeInitialArray()` → `StArrayElement.MemorySlab(name)` (no new slab creation, already from reservation)
   - Add `is PtConstant ->` branch in `handleStructAllocation()` inner check alongside existing `is PtFunctionCall`
4. Update ast printers/checkers that iterate `PtArray` children: add `PtConstant` to accepted element types
5. 6502 codegen `AsmGen.kt` `translate()`: add `is PtMemorySlabReservation -> {}` (no-op)
6. IR codegen `IRCodeGen.kt` `translateNode()`: add `is PtMemorySlabReservation -> emptyList()` (no-op)

### Phase 2: CompilerAst nodes + CodeDesugarer

7. Add `MemorySlabReservation` and `MemorySlabRef` to CompilerAst
8. Add `after(FunctionCallExpression)` in `CodeDesugarer.kt`:
   - Check `isMemoryCall`
   - Extract name, size, alignment from args
   - Return `AstReplaceNode` (call → MemorySlabRef) + `AstInsert.before` (MemorySlabReservation before containing statement)
9. Update `AstIdentifiersChecker`: slab name sanitization on MemorySlabReservation

### Phase 3: SimplifiedAstMaker updates

10. Side-channel map `slabDefs: Map<String, StMemorySlab>` in SimplifiedAstMaker (populated by MemorySlabReservation processing)
11. `MemorySlabReservation` → create `StMemorySlab`, store in map, emit `PtMemorySlabReservation`
12. Const VarDecl with `MemorySlabRef` initializer → look up slab from map, create `PtConstant(memorySlab=...)`
13. Non-const VarDecl with `MemorySlabRef` initializer → auto-promote to `PtConstant`
14. `MemorySlabRef` in array literal → special case in `transform(srcArr: ArrayLiteral)`:
    - Before calling `transformExpression`, check if `elt` is `FunctionCallExpression(isMemoryCall)` or `MemorySlabRef`
    - Create `PtConstant` directly (since not `PtExpression`, bypasses `transformExpression`)
    - Update element validation `require(...)` to include `PtConstant`
15. Same for struct alloc inner memory() → `PtConstant` with synthetic name
16. Remove all `isMemoryCall` special cases from SimplifiedAstMaker (the array/const handling now uses MemorySlabRef)

### Phase 4: Downstream CompilerAst pass updates

17. `AstChecker`: replace `isMemoryCall` with `is MemorySlabRef`/`is MemorySlabReservation`
    - Add explicit check: after desugaring, any remaining `FunctionCallExpression` with `isMemoryCall` is an error
18. `VerifyFunctionArgTypes`: visit `MemorySlabReservation` instead of memory() calls
19. `ConstantIdentifierReplacer`: keep the memory() workaround (runs before desugaring); add parallel handling for `MemorySlabRef` for optimizer phase
20. `LiteralsToAutoVarsAndRecombineIdentifiers`: keep as-is (runs before desugaring)

### Phase 5: Cleanup

21. Remove `createMemorySlabFromCall` helper from SymbolTableMaker (no longer needed)
22. Remove standalone `is PtFunctionCall if node.isMemoryCall ->` handler from SymbolTableMaker
23. Remove `funcMemory()` from both codegens (6502 and IR) — no longer reachable since PtConstant replaces PtFunctionCall("memory") in all contexts
24. Remove `"memory"` from BuiltinFunctions dispatch tables
25. AstChecker: add verification that no `PtFunctionCall` with `isMemoryCall` remains in SimpleAst
26. Remove dead `isMemoryCall` imports where no longer used
27. Full test suite: `gradle build`

### `isMemoryCall` helper — what remains after refactoring

The extension property stays, but only used in pre-desugaring phases (where `memory()` still exists as a `FunctionCallExpression`):

| Phase | File | Reason |
|-------|------|--------|
| processAst step 2 | `AstIdentifiersChecker.kt` | Sanitize slab name in the string arg |
| processAst step 2 | `LiteralsToAutoVarsAndRecombineIdentifiers.kt` | Keep string literal, don't intern |
| processAst step 4 | `ConstantIdentifierReplacer.kt` | Const reference replacement workaround |
| processAst step 6 | `CodeDesugarer.kt` | Detect memory() calls to transform |
| processAst step 10 | `AstChecker.kt` | Flag any surviving memory() call as error |

Post-desugaring phases (SimplifiedAstMaker, SymbolTableMaker, VerifyFunctionArgTypes, codegens) no longer need it — they see `MemorySlabRef`/`MemorySlabReservation` instead. Total reduction from ~22 uses to ~5.

## Key Pipeline Flow (after implementation)

```
Source: const uword buf = memory("mybuf", 100, 1)
  │
  ├─ Parser: FunctionCallExpression(target="memory", args=[StringLiteral, 100, 1])
  │
  ├─ processAst step 2 (AstIdentifiersChecker):
  │   └─ Sanitizes "mybuf" name in the FunctionCallExpression
  │
  ├─ processAst step 2 (LiteralsToAutoVars):
  │   └─ Keeps string literal as-is (not interned) because parent is memory() call
  │
  ├─ processAst step 4 (ConstantIdentifierReplacer):
  │   └─ CONST memory() variable: replaces identifier refs with FunctionCallExpression copies
  │
  ├─ processAst step 6 (CodeDesugarer):
  │   └─ Transforms FunctionCallExpression("memory") into:
  │         MemorySlabReservation("mybuf", 100, 1)    ← inserted before the VarDecl
  │         const uword buf = MemorySlabRef("mybuf")  ← replaces the call
  │
  ├─ optimizeAst:
  │   └─ MemorySlabRef constValue() = null → no constant folding
  │
  ├─ postprocessAst (VerifyFunctionArgTypes):
  │   └─ Visits MemorySlabReservation nodes for duplicate detection
  │
  ├─ SimplifiedAstMaker:
  │   ├─ MemorySlabReservation → PtMemorySlabReservation (and cached in side-channel map)
  │   └─ Const VarDecl with MemorySlabRef → PtConstant(memorySlab=StMemorySlab(...))
  │
  ├─ SymbolTableMaker:
  │   ├─ PtMemorySlabReservation → StMemorySlab added to global scope
  │   └─ PtConstant(memorySlab=...) → StConstant referencing the slab
  │
  ├─ Codegen:
  │   ├─ PtMemorySlabReservation → no-op
  │   ├─ StConstant(memorySlab) → EQUATE: constname = prog8_slabs.memory_mybuf
  │   └─ StMemorySlab → .section BSS_SLABS: mybuf .fill 100
  └─
```

## Non-const variable handling

`uword buf = memory("x", 100, 1)` — the slab address is constant, even if the variable isn't declared `const`. The SimplifiedAstMaker auto-promotes this to `PtConstant(memorySlab=...)`, making it always const internally.
