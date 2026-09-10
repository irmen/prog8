# Aliasing a whole enum

> **Status: proposed idea, not implemented.**

Make `alias myEnum = Priority` legal, so that `myEnum::Member` behaves
exactly like `Priority::Member`. Aliasing individual enum members
(`alias myNormal = Priority::NORMAL`) already works; aliasing the enum
as a whole is still rejected with the error "cannot alias an enum".

## Overview & goals

- The goal is to allow `alias myEnum = Priority` (scoped or unscoped enum target).
- `myEnum::Member` must then behave identically to `Priority::Member`.
- Bare `myEnum` (without `::Member`) stays unusable: an enum is not a type
  or a value, only a namespace for member constants, so such usage should
  keep producing an "undefined symbol" error.
- No changes to the type system, the enum desugaring, or any code generator backend.

## Background: why member aliasing works

Member aliasing was fixed in commit `ccc26a0eb` ("Fix alias targets to enum members"):

1. In the first `AstPreprocessor` fixpoint pass, `before(alias)` in
   `compiler/src/prog8/compiler/astprocessing/AstPreprocessor.kt:415`
   finds no target yet for `Priority::NORMAL` (only the `Enumeration`
   node exists), but `refersToNotYetDesugaredEnumMember()` (`:469`)
   detects the pending enum member and defers the error.
2. In the same pass, `after(enum)` (`:530`) desugars the enum into
   `VarDecl(CONST)` declarations named `Priority::NORMAL` etc. and
   removes the `Enumeration` node.
3. In the next pass the member alias resolves to the new const, and
   `LiteralsToAutoVarsAndRecombineIdentifiers.after(identifier)` (`:260`)
   rewrites uses to the scoped member name, which `constantFold` then
   folds to a numeric literal.

The full pipeline (`preprocessAst` -> `checkIdentifiers` -> `charLiteralsToUByteLiterals`
which removes all `Alias` nodes in `AstExtensions.kt:136`, -> `checkValid`)
is proven to handle such aliases end to end.

## Why the whole enum cannot be aliased today

- `alias myEnum = Priority` is explicitly rejected in `AstPreprocessor.kt:428-429`.
- `Enumeration` (`compilerAst/src/prog8/ast/statements/AstStatements.kt:632`)
  is transient sugar: it is desugared and deleted, and has no value or type
  semantics. `inferType` returns unknown, `constValue` returns null
  (`AstExpressions.kt:1581-1648`), and `StructTypeResolver.kt:19-43` returns
  null for it, so neither the alias machinery nor any code generator can
  consume an alias that points at it.
- `::` is intra-token (part of a single `UNICODEDNAME` token), so
  `myEnum::NORMAL` is a single `nameInSource` element. The existing
  dot-based alias forwarding (`AstToplevel.kt:223`,
  `LiteralsToAutoVarsAndRecombineIdentifiers.kt:252`) can rewrite
  `myStructAlias.field` but cannot split and rewrite the prefix inside `::`.

## Approach: expand the whole-enum alias into member aliases

Instead of teaching every consumer `::`-aware prefix rewriting, reuse the
already working member-alias pipeline: rewrite `alias myEnum = Priority`
during `AstPreprocessor.before(alias)` into N generated member aliases:

```kotlin
Alias("myEnum::LOW",    IdentifierReference(["Priority::LOW"]),    ...)
Alias("myEnum::NORMAL", IdentifierReference(["Priority::NORMAL"]), ...)
...
```

`myEnum::NORMAL` then resolves as an ordinary alias (`searchSymbol` matches
`stmt.alias==name`), is replaced with `Priority::NORMAL`, and is folded.
Visibility checks, private checks and final alias removal apply unchanged.

## Proposed changes

### 1. `AstPreprocessor.kt:415-467` (`before(alias)`)

- Replace the `else if(tgt is Enumeration)` error branch (`:428-429`) with
  a new helper, e.g. `splitEnumAliasIntoNamespace(alias, enum, enumTargetPath)`:
  for each `enum.members` entry create
  `Alias("${alias.alias}::${member}", IdentifierReference(enumTargetPath.dropLast(1) + listOf("${enum.name}::${member}"), alias.position), alias.visibility, alias.position)`,
  and return those as `AstInsert.before(alias, ...)` plus `AstRemove(alias, parent)`
  (same insert-before-plus-remove pattern as `after(enum)` at `:585-587`).
- In the `tgt is Alias` chain loop (`:430-462`), when the terminal target
  resolves to an `Enumeration`, call the same helper with
  `chainedTargetName.nameInSource` as the enum path instead of emitting the
  plain chain-shortened alias. This keeps chained cases
  (`alias a = b; alias b = Priority` and the reverse order
  `alias b = Priority; alias a = b`) working because the fixpoint processes
  both directions. Cross-module chains already resolve to the `Enumeration`
  via `lookupQualified`'s alias following (`AstToplevel.kt:214-225`), so the
  direct branch covers those too.

### 2. Tests (`compiler/test/ast/TestConst.kt`)

Replace/extend the "alias to whole enum gives error" test (`:692`) with
(`%zeropage basicsafe`, `cx16` target, `writeAssembly=false`, values checked
via `@shared` assignments like the existing test at `:667`):

1. `alias myEnum = Priority`, then `myEnum::NORMAL == 1` and
   `myEnum::EXTREME == 3` (auto-increment values).
2. Enum with a mix of explicit and auto-assigned member values.
3. Chained whole-enum alias: `alias a = b; alias b = Priority; a::HIGH`.
4. Whole-enum alias to an enum in another block
   (`alias x = other.Priority` -> `x::LOW`).
5. Bare `myEnum` in an expression -> "undefined symbol" (documents intent).
6. Keep the test for nonexistent member (`:707`, "undefined symbol") unchanged.

### 3. Docs

- `docs/source/programming.rst:127-134`: state that an enum can be aliased
  as a whole and used via `<alias>::<member>`.
- `docs/source/history.rst`: add a release-notes entry.

## Edge cases

- The expansion runs while the `Enumeration` node still exists (the same
  fixpoint pass that also desugars it), so the member consts are in place
  when the generated member aliases are resolved in the next pass.
- `AstIdentifiersChecker.visit(alias)` (`:149`) conflict check returns the
  alias itself for `myEnum::X`, so no spurious name conflict.
- All `Alias` nodes (including generated ones) are removed by
  `AstExtensions.kt:136` before `checkValid`, so the `::`-in-name
  assertions (`AstChecker.kt:87,441,1048`) are never triggered.
- Generated aliases inherit the original alias's `visibility` (consistent
  with a user-written member alias; the enum members' own privacy is still
  enforced on the desugared consts).
- Bare `myEnum` usage: the original alias node is removed on expansion, so
  lookups fail with "undefined symbol", which is the desired semantics.

## Verification

- `gradle :compiler:compileKotlin --console=plain`
- `gradle :compiler:test --tests "prog8tests.compiler.TestConst" --console=plain`
- `gradle build --console=plain`

## Rejected alternative

Making the whole-enum alias a "namespace alias" with `::`-prefix rewriting
in `lookup` / `lookupQualified` / `LiteralsToAutoVarsAndRecombineIdentifiers`.
It would handle chains marginally more uniformly, but touches shared lookup
code used everywhere plus `inferType` / `constValue` / deref paths, and is
much riskier than reusing the proven member-alias pipeline.
