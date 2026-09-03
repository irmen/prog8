# Scoping for qualified symbols - brevity

**Status: TODO / not implemented.** Listed as an active item in `docs/source/todo.rst`.

## Problem we are solving

Prog8 supports nested subroutines, and symbols in an outer scope must currently be reached by their fully qualified global name (`main.outer.inner.counter`). That is verbose, especially when the same prefix repeats many times inside a deep sub. The existing `alias` statement solves brevity, but it requires a separate declaration for every symbol and quickly becomes noisy when several outer symbols are used.

We want a lightweight, opt-in syntax that says "start this qualified lookup from my enclosing scopes" while keeping today's global-by-default semantics for unadorned `a.b.c`. The same syntax also gives us a backwards-compatible migration path if we ever decide to flip the default so that plain `a.b` becomes local-first.

## Goal

Shorter references inside nested `sub`s/blocks without changing the global-qualified default. `alias` already helps but requires a declaration; a lightweight local-qualified syntax removes repetitive `main.outer.inner.` prefixes.

## Current model

* Unscoped `x` = look in current `sub` -> enclosing `sub`s -> `block` -> error (`docs/source/programming.rst:176`).
* Scoped `a.b.c` = **always** from root (`programming.rst:116` - dotted names are global). `main.foo` inside `main.foo` still means `main.foo` (global), not local.

No local-first exists.

## Proposal: opt-in `.`-prefixed local-qualified

**Keep default:** `a.b.c` stays global (backwards compat). Add **one** new form:

```
.localName          ; local-first single name with leading dot
.local.sub.value    ; local-first dotted path with leading dot
```

Semantics: `.x` or `.a.b` starts lookup in current `sub`/`block` and walks enclosing `sub`/`block` chain (same walk as unscoped `x`), then continues down the dotted suffix. Applies only to value-context `scoped_identifier` dotted names - see Grammar below.

* `.x` with no dot suffix is allowed.
* Strict local-first: if `.a` is not found in enclosing scopes, error (no silent fallback to global or imported modules). Makes `.` explicit.

There is **no** change to `a.b.c` without dot - it remains global. For future flipping (Q2), reserve an explicit global prefix `::a.b` but do not implement yet - see Migration below. Do **not** use `global.a.b`; `global` could be a real identifier and `::` is unambiguous.

### Examples

With shadowing (where `.` adds real value):
```prog8
main {
    sub outer() {
        ubyte counter
        sub inner() {
            ubyte counter            ; shadows outer.counter
            counter = 1              ; inner.counter (unscoped finds innermost)
            .counter = 1             ; outer.counter (main.outer.counter)
            main.outer.counter = 1   ; same as .counter, but verbose
        }
    }
}
```

Without shadowing, `.counter` and `counter` refer to the same symbol, but `.counter` still documents that you intend the enclosing-chain interpretation and will catch typos that would otherwise fall back to an imported module.

Block brevity:
```prog8
main {
    ubyte shared
    sub work() {
        ubyte shared  ; shadows block-level shared
        .shared = 2   ; inner sub's shared (local), vs main.shared for global
    }
}
```

### When does `.x` differ from bare `x`?

Unscoped `x` already walks the enclosing sub/block chain, so `.x` and `x` usually resolve to the same symbol. They differ in two situations:

1. **Shadowing:** if a closer local declaration hides an outer one, `x` refers to the innermost symbol while `.x` refers to the outer one found by walking the enclosing chain.
2. **No local match but a module-level match exists:** unscoped `x` may fall back to a symbol in another module (e.g. an imported block), whereas `.x` errors because it is strictly local-first.
3. **Dotted names:** `a.b` is always global, while `.a.b` starts from enclosing scopes. This is the main use case for non-trivial paths.

### Alternatives considered

* Make bare `a.b` local-first - breaking (`todo.rst:13` alternative "require new syntax to explicitly look up from global scope" e.g. `::a.b`). Rejected for now to keep backwards compat (Q2).
* Global prefix `::` / `global.` - not needed while `a.b` stays global; reserve `::` for future flip. Use only `::`, never `global.a.b`, because `global` could be a real block name.
* Multi-dot scope-count prefix (`.x` = one scope up, `..x` = two scopes up, etc.). Rejected because counting scopes is fragile when refactoring, it complicates the lexer (new `..` / `...` tokens), and it interacts awkwardly with dotted paths like `..outer.counter`. The original `.a.b` already reaches a specific outer scope by name, so explicit counting is unnecessary.
* Fat `alias` sugar - `alias c = outer.counter` already solves brevity but needs one alias per name; `.` is lighter for one-off.

### Backwards compat and future flip (Q2)

Chosen now: `.a.b` = local-first explicit opt-in, `a.b` = global (compat). This is easy to evolve:

1. Keep `a.b` = global for at least one minor version, introduce `::a.b` as explicit global in parallel. Warn on `a.b` that also exists locally ("did you mean `.a.b`?").
2. Later, flip default: `a.b` becomes local-first, `::a.b` becomes the required global form. Provide `%option local_scoped_lookup` to opt into new behavior early, and a fixer to rewrite `a.b` -> `::a.b` where needed.

Because `.` and `::` are distinct tokens, the flip is a one-line parser/lookup change and a docs/migration flag. Keeping `.` now does not lock us out of a global prefix later; it is the migration path. Reserve the `::` token in the lexer now even though it is unused, so user code cannot claim it before the flip.

Q3 answer: yes, walk enclosing chain - otherwise `.` would not add value over unscoped `x`, which already does that. The value is dotted `.a.b` and shadowing disambiguation.

Q5: only value-context `scoped_identifier` dotted names; pointer deref `ptr.field` (`pointerdereference` rule) is not a scoped lookup and is unaffected, though `.ptr^^.field` is allowed because the variable `ptr` itself may be local-qualified.

## Grammar change

`Prog8ANTLR.g4:325` currently has a single rule used everywhere:

```
scoped_identifier : identifier ('.' identifier)* ;
```

**Decision: split the grammar.** Do not add `'.'?` to the global `scoped_identifier` rule, because that rule is also used in type/declaration contexts where a leading dot is meaningless (`datatype`, `pointertype`, `for` loop variable, `staticstructinitializer`, `alias` target if it names a type).

Instead, introduce a value-only variant and update only the value-context rules (`expression`, `assign_target`, `arrayindex`, `addressof`, `functioncall`, `functioncall_stmt`, `sizeof_argument`, `pointerdereference` prefix, and statement forms that accept an `assign_target`):

```
scoped_identifier        : identifier ('.' identifier)* ;
local_scoped_identifier  : '.' identifier ('.' identifier)* ;
value_scoped_identifier  : scoped_identifier | local_scoped_identifier ;
```

Then use `value_scoped_identifier` in expression/assignment/call/address-of/index rules, and keep `scoped_identifier` in type/declaration rules. In particular, keep `scoped_identifier` (no leading dot) for:

* `datatype` / `pointertype` / `staticstructinitializer` type names
* `for` loop variable name
* `alias` target
* `extsub` bank variable
* directive name lists

Visitor (`Antlr2KotlinVisitor.kt:477`) already builds `IdentifierReference(listOf(...))`; now set `isLocalQualified` when the parse tree came from `local_scoped_identifier`. `Position` should include the dot for error messages.

## AST representation

Add a flag to `IdentifierReference`:

```kotlin
data class IdentifierReference(
    val nameInSource: List<String>,
    val isLocalQualified: Boolean = false,
    override val position: Position
) : Expression()
```

Because it is a data class, adding the field affects copy/equals/hashCode. Audit all call sites that construct or copy `IdentifierReference` to ensure the flag is preserved through AST transforms. `-printast1` and `-printast2` should render the leading dot.

## Symbol resolution

`compilerAst/src/prog8/ast` `SymbolTable` lookup: if `isLocalQualified` then walk enclosing scopes first, then resolve remaining dotted suffix. Do **not** reuse `lookupUnqualified` verbatim because it also falls back to imported modules and builtins; local-qualified lookup must be stricter.

Add a new helper, e.g. `lookupEnclosingOnly(name)`:

1. Search current scope.
2. Walk parent scopes (sub -> enclosing sub -> block).
3. Stop at block/global; do **not** search other modules and do **not** return builtins.
4. Return the matched node, or null if not found.

For `.a.b.c`:
1. Find `a` via `lookupEnclosingOnly("a")`.
2. If not found -> error `undefined local symbol '.a'`.
3. Resolve `b.c` relative to the scope where `a` was found (same suffix logic as current `lookupQualified`, but anchored at that node).

No `flat` cache impact if resolved at lookup time; call `resetCachedFlat()` if any AST rewrite adds new local-qualified nodes (not needed).

## Scope walk (Q3: walk enclosing chain)

Reuse existing enclosing-scope walk but for `.a.b`:
1. find `a` in current `sub` -> enclosing `sub`s -> enclosing `block`s (same as unscoped).
2. then resolve `.b` suffix from that scope (normal scoped resolution relative to `a`).

If step 1 fails -> error `undefined local symbol '.a'`. Single `.x` is thus a shorthand for the same walk plus shadowing control vs bare `x`.

Rationale (Q3): looking only inside current `sub` would be redundant - unscoped `x` already does that. The dot is valuable precisely to write `.outer.counter` meaning "start from an enclosing `outer` scope, not from root `main`".

## Self-reference edge case

Inside `sub inner()`, `.inner()` should **not** resolve to the current sub itself. Local-qualified lookup for the first component should skip the current scope when the matched symbol would be the sub that contains the reference, otherwise `.inner()` would be a confusing synonym for `inner()`. Recursive calls should continue to use the unqualified name.

## Aliases, constants, address-of and type casts

* **Aliases:** if `.a` resolves to an `alias`, follow it, just like unscoped lookup does. This means a local alias can effectively point at a global symbol; that is fine because the alias itself is local.
* **Constants:** `.CONST` works and participates normally in constant folding. `ConstantIdentifierReplacer` resolves it the same way as any other `IdentifierReference`.
* **Address-of:** `&.var` and `&&.var` work because `addressof` uses a value-context scoped identifier.
* **Type casts:** `x as .sometype` is **disallowed**. The grammar split keeps leading dots out of type/declaration contexts, so this cannot parse. Document that type names are always global-qualified to keep type lookup simple and unambiguous.

## Private symbols

`.foo` inside the same scope sees private symbols normally. It does **not** bypass privacy from outside the scope. No special behavior is needed beyond today's privacy rules.

## Imported modules

Imported module names (e.g. `txt` after `%import textio`) are not part of the lexical enclosing chain. `.txt.print(...)` must error; use the global-qualified `txt.print(...)` for imports.

## Testing

* `prog8c -check` rejects `.missing` (not found locally).
* `prog8c -check` rejects `.txt.print(...)` when `txt` is an imported module.
* `prog8c -check` rejects `x as .sometype`.
* `prog8c -target virtual -emu` verifies `.counter` inside nested sub writes parent var correctly, while `counter` (unscoped) still works as before.
* Ensure `main.outer.counter` still forces global even when local `counter` exists (shadowing test).
* `.var` reaches parent sub, grandparent sub, and block-level var.
* `.outer.counter` inside a triply-nested sub.
* `.inner()` does not resolve to the current sub itself.
* `&.localvar` and `&&.localvar` produce correct addresses.
* `.arr[0] = ...` and `foo(.arg)` work.
* `.CONST` works in constant expressions.
* Test both `-noopt` and optimized paths, and both virtual and 6502 targets.

## Language server / tooling

Update `languageServer` for the new syntax: go-to-definition, hover, autocomplete, and symbol lookup must understand `isLocalQualified` and resolve it against enclosing scopes rather than the global namespace.

## Documentation updates

Update `docs/source/programming.rst`:
* Lines 116-118: note that `.`-prefixed scoped names are an exception to "scoped names always start in the global scope."
* Lines 176-184: add an example of `.` local-qualified lookup for nested subroutines.
* Add a short subsection under "Blocks, Scopes, and accessing Symbols" explaining the `.` syntax.

## Existing example programs

No bulk updates are needed. A review of programs with nested subroutines (`numbergame.p8`, `virtual/bouncegfx.p8`, `maze.p8`, and others) found that they already access enclosing-scope variables with bare unqualified names, which is already concise. The `.` syntax would only add explicitness there, not brevity. Leave existing examples unchanged; add one small new example demonstrating `.outer.counter` if desired.

## Comparison to other languages

The problem (nested scopes and shadowing) is universal, but Prog8's current rule - scoped names are always global - is unusual. Most languages do the opposite: unqualified/local lookup is the default, and reaching the global/root namespace requires an explicit prefix.

* **Lexical scoping** (inner scopes can read outer variables by bare name) is the norm in C, C++, Python, JavaScript, Pascal, Rust, etc. Prog8 already does this for unscoped names, so the proposal aligns with that common model.
* **Explicit global/root prefix** is also common: C++ `::name`, Rust `crate::name`, Java/C# fully-qualified class names. The proposed future `::a.b` global form moves Prog8 toward this convention.
* **Named qualifiers for shadowed outer names** are the usual way to disambiguate: `this.field` / `OuterClass.this.field` in Java/C#, `super::` in Rust, `nonlocal` / `global` in Python. A leading dot (`.field`) for "local-qualified" is uncommon; it is a Prog8-specific shorthand chosen because it is short and visually distinct.
* **Dot-count scope climbing** (`.x`, `..x`, `...x`) is basically unheard of in mainstream languages; the few systems that use dots for locality (some assemblers' local labels) use a single dot to mean "local to the enclosing label," not "N scopes up."

Bottom line: the proposed future flipped model (`a.b` local-first, `::a.b` global) would make Prog8 scoping look conventional. The current opt-in `.` syntax is the pragmatic, backwards-compatible stepping stone.

## Vibe check

Brevity without breaking global explicitness matches retro simplicity: `main.` stays obvious, `.` is visible opt-in like `&`/`&&` for addresses. Less magic than making all `a.b` local-first.

Open questions:
* None remaining - all design decisions above are resolved.

(End of file)
