# Optional For-Loop Type Declaration

## Goal

Allow an optional datatype before the implicit loop variable:

```prog8
for long w in "derp" {
    txt.print_l(w)
}
```

The specified datatype overrides the type inferred from the iterable. Existing
`for variable in iterable` syntax remains unchanged.

If the loop specifies a datatype and the loop variable is already explicitly
declared in the same scope, report a conflicting variable declaration,
regardless of whether the two types match. A loop without an explicit datatype
continues to reuse and type-check an existing declaration as it does today.

## Required Changes

### Grammar

Update `parser/src/main/antlr/Prog8ANTLR.g4:413`:

```antlr
forloop : 'for' datatype? scoped_identifier 'in' expression EOL? (statement | statement_block) ;
```

The optional datatype must not break existing forms such as `for i in values`.
Parser tests should cover both typed and untyped forms.

### AST

Update `compilerAst/src/prog8/ast/statements/AstStatements.kt`:

- Add an optional `DataType` property to `ForLoop`, such as `loopVarType: DataType?`.
- Keep the default value `null` for untyped loops.
- Include the type in `toString()` when present.
- No AST walker changes are needed because `DataType` is not an AST node.

### `loopVarDt()` Behavior

`ForLoop.loopVarDt(program)` at
`compilerAst/src/prog8/ast/statements/AstStatements.kt` currently infers the
loop variable's type by calling `loopVar.inferType(program)`, which looks up
the `VarDecl` and returns its `DataType`.

With the new `loopVarType` property, update `loopVarDt()` to return the
explicit type when present:

```kotlin
fun loopVarDt(program: Program): InferredType {
    if (loopVarType != null)
        return InferredTypes.knownFor(loopVarType!!)
    return loopVar.inferType(program)
}
```

**Why this matters:** `TypecastsAdder` at
`compiler/src/prog8/compiler/astprocessing/TypecastsAdder.kt` calls
`parent.loopVarDt(program)` to determine the target type when adjusting range
expression `from`/`to` values. For a typed loop like `for word w in 10 to 20`,
the range endpoints (ubyte constants) need to be widened to `word` to match
the loop variable. `loopVarDt()` must return `word`, not the inferred ubyte
from the range.

This also ensures `AstChecker`'s range boundary validation uses the correct
target type when checking `checkValueTypeAndRange` and
`isNotAssignableTo`/`istype` on range endpoints.

**Note:** For typed loops, `ImplicitForIteratorDecls` already creates the
`VarDecl` with the specified type (e.g., `word`), so
`loopVar.inferType(program)` would also return the correct type. The explicit
`loopVarType` check is a safety measure that avoids relying on declaration
ordering and makes the intent clear.

### Parser Visitor

Update `compilerAst/src/prog8/ast/antlr/Antlr2KotlinVisitor.kt`:

- In `visitForloop`, convert `ctx.datatype()` with the existing `dataTypeFor` helper.
- Pass the result to the `ForLoop` constructor.

### Implicit Iterator Declaration

Update `compiler/src/prog8/compiler/astprocessing/ImplicitForIteratorDecls.kt`:

- Use `forLoop.loopVarType ?: elementType` when creating the implicit `VarDecl`.
- If `loopVarType` is present and an existing `VarDecl` with the loop variable
  name is found, report an error such as:

  ```text
  conflicting variable declaration: 'w' is already declared
  ```

- Do not insert a second declaration after reporting the error.
- Preserve the current behavior for untyped loops with existing declarations.
- Ensure pending declarations retain the effective loop variable type when
  checking multiple loops in the same scope.

### Type Checking

Update `compiler/src/prog8/compiler/astprocessing/AstChecker.kt`:

- Preserve the current strict iterable checks for untyped loops.
- For typed loops, validate that the loop variable has a numeric or pointer
  type.
- Validate that an iterable element can be assigned to the requested loop
  variable type. This should permit safe widening, for example:

  ```prog8
  for word w in "derp" { }
  for long w in byteValues { }
  ```

- Keep the existing range-value checks and unsigned countdown safety checks.
- Preserve the existing pointer-specific validation where applicable.

### Source Conversion

Update `compilerAst/src/prog8/ast/AstToSourceTextConverter.kt` so typed loops
are emitted as:

```prog8
for <datatype> <variable> in <iterable> ...
```

This keeps AST-to-source output round-trippable.

## Code Generation Considerations

The loop variable remains a normal `VarDecl`, so the existing IR and 6502 code
generators should use its effective type automatically. Element-to-variable
conversion should use existing assignment and implicit-cast handling.

Verify this assumption with both virtual-target execution and a CX16 compile,
especially for widening from string or byte-array elements to `word` or
`long`.

## Tests

Add or extend tests in the existing for-loop test suites:

- `compiler/test/ast/TestProg8Parser.kt`
  - Parse `for long w in "derp"`.
  - Preserve parsing of untyped loops.
- `compiler/test/ast/TestVariousCompilerAst.kt`
  - Confirm the implicit iterator declaration uses the requested type.
  - Confirm widening element types are accepted.
- `compiler/test/ast/TestAstChecks.kt`
  - Confirm a typed loop conflicts with an existing explicit declaration,
    including when the types match.
  - Confirm untyped loops retain existing explicit-declaration behavior.

## Verification

Run:

```bash
gradle :compiler:compileKotlin --console=plain
prog8c -target virtual -emu examples/test.p8
prog8c -target cx16 -check examples/test.p8
gradle build --console=plain
```

The example should compile with `w` typed as `long` and print the string
characters through the long-valued loop variable.

## Documentation

Update the for-loop section of the language documentation to describe the
optional datatype and the conflicting-declaration rule.
