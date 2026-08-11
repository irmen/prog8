# Long Consts: optional const type, defaulting to long

## Requirements

### Overview & Goals

Make the const type declaration optional. When the type is omitted, the const
defaults to `long` (32-bit signed integer), except float literals which become
`float`, bool literals which become `bool`, `memory()` calls which become
`pointer`, and identifier references which inherit the referenced identifier's
type. This revisits the old `long-consts` branch (which is 217+ commits behind
master and predates the module split), without its controversial `-typedconsts`
force-long mode. The goal is purely additive: `const x = 42` becomes valid
syntax instead of a compile error, with behavior identical to
`const long x = 42`.

**Not value-based type inference.** This is NOT about inferring the best-fit
type from the value (like making `const x = 5` a ubyte because 5 fits). It's
expression-type inference: what type does the initializer expression produce?
Float literals produce float, bool literals produce bool, `memory()` produces
pointer, identifier references inherit the referenced identifier's type,
everything else defaults to long.

### Use Case

Before: `const long SCREEN_WIDTH = 320`, `const float PI = 3.14`, `const bool DEBUG = true`

After:  `const SCREEN_WIDTH = 320`, `const PI = 3.14`, `const DEBUG = true`

The type is inferred from the initializer expression. Explicit types still work
and are unchanged.

### Scope

#### In Scope

- **Parser visitor change**: default a missing const datatype using literal
  type detection (float/bool/long).
- Verify/adjust const type handling in the optimizer passes so long consts
  behave correctly through the whole pipeline.
- IR/virtual target verification as the absolute gate before any other backend.
- Unit tests and documentation.

#### Out of Scope

- The `-typedconsts` / `%option force_long_consts` mode from the old branch
  (retypes existing explicitly-typed consts; the only part that could break
  existing wraparound-dependent code).
- Grammar changes (the grammar already accepts `datatype?`).
- 6502 / m68k codegen backend changes (deferred until the IR gate passes).

## Technical Design

### Current Implementation

1. Grammar: `constdecl: PRIVATE? 'const' datatype? identifierlist '=' expression`
   (Prog8ANTLR.g4:211). The datatype is optional in the grammar so the visitor
   can emit a friendly error instead of a cryptic parse error.
2. Visitor: `Antlr2KotlinVisitor.kt:260-262` throws
   `SyntaxError("datatype missing")` when datatype is null. This is the only
   reason untyped consts fail today.
3. `VariousCleanups.kt:72-73` already preserves `long` as the declared type for
   `const long` declarations (does not shrink the type to the value's size).
4. `ConstantFoldingOptimizer.kt:34-49` reduces LONG literals to their
   optimal smaller type at **all** expression sites, including const declaration
   sites. When the parent is a const VarDecl, `parent !is Assignment` is true,
   so the LONG literal IS shrunk (e.g. LONG 5 becomes UBYTE 5). This does NOT
   break correctness because `IdentifierReference.constValue()` (see point 5)
   re-casts the value back to the declared `long` type on substitution. The old
   branch added a guard to prevent this unnecessary shrink-and-recast cycle,
   but the guard is optional, not required for correctness.
5. `IdentifierReference.constValue()` (AstExpressions.kt:1577-1607) casts a
   const's value back to the const's declared datatype when substituting it.
   Combined with (4), this means: a const declared `long` with value 5 is
   substituted as LONG 5, then reduced to UBYTE 5 at each use site.

The key insight: **the current pipeline already handles `const long X = 5`
end-to-end correctly** (declaration kept long, use sites shrunk). The feature
is therefore mostly a matter of letting the visitor accept the untyped form
and default it to `long`.

### Key Decisions

1. **Default to `long`, with exceptions.** Integer consts without an explicit
   type become `long`, as if written `const long x = 5`. Float literals become
   `float`, bool literals become `bool`, `memory()` calls become `pointer`,
   identifier references inherit the referenced identifier's type. This matches
   the todo.rst:24 direction ("Make all constants long by default? remove type
   name altogether"), avoids wraparound surprises in const arithmetic, and
   reuses the existing pipeline behavior for long consts. This is
   expression-type inference, not value-based type inference.
2. **No new optimizer pass.** The old branch added a `ConstantIntegerTypeChanger`
   pass to shrink long literals at use sites. The current `ConstantFoldingOptimizer`
   already does exactly that, so the pass is unnecessary.
3. **Purely additive, no compatibility risk.** Untyped consts are currently a
   compile error, so no existing program can be affected. Explicitly typed
   consts (including `const long`) behave exactly as before.
4. **Symbol dumps already include the type.** The skeleton files in
   `docs/source/_static/symboldumps/` show const types via
   `SymbolDumper.kt:127-131` (format: `const <type>  <name>`). Untyped consts
   defaulting to long will correctly appear as `const long  NAME` in the dumps.
   No changes needed to the dump generation.

### Proposed Changes

#### 1. Parser Visitor (core change)

**File: `compilerAst/src/prog8/ast/antlr/Antlr2KotlinVisitor.kt`**

In `visitConstdecl` (lines 260-278), replace the `SyntaxError("datatype missing")`
throw with type inference from the initializer expression:

```kotlin
val datatype = if(ctx.datatype()!=null) {
    dataTypeFor(ctx.datatype()) ?: DataType.LONG
} else {
    when(val init = initialvalue) {
        is NumericLiteral if init.type==BaseDataType.FLOAT -> DataType.FLOAT
        is NumericLiteral if init.type==BaseDataType.BOOL -> DataType.BOOL
        is IdentifierReference -> {
            // re-infer from referenced identifier's type
            val refDecl = init.identifier.lookupStatement(program).treeWalkFilter { it is VarDecl }.singleOrNull() as? VarDecl
            refDecl?.datatype ?: DataType.LONG
        }
        is FunctionCall -> {
            // memory() returns pointer
            if(init.functionNameInProgram(program) == "memory") DataType.POINTER else DataType.LONG
        }
        else -> DataType.LONG
    }
}
```

Keep the existing literal-promotion logic (lines 267-270) unchanged. This is
NOT value-based type inference (like making `const x = 5` a ubyte because 5
fits). It's expression-type inference: what type does the initializer expression
produce? Float literals produce float, bool literals produce bool, `memory()`
produces pointer, identifier references inherit the referenced identifier's
type, everything else defaults to long.

#### 2. Enum type defaulting to long

**File: `compiler/src/prog8/compiler/astprocessing/AstPreprocessor.kt`**

In the enum desugaring code (lines 548-565), `DataType.forDt(enum.type)` is
used to determine the type for the enum's const members. If the user specifies
a type (e.g., `enum ubyte Color { ... }`), it's that type. If no type is
specified, the default is currently whatever the parser gives it.

Add a check: if `enum.type` is the default type (probably `ubyte`), change it
to `long`:

```kotlin
val dt = if(enum.type == <default-type>) DataType.LONG else DataType.forDt(enum.type)
```

This makes `enum Color { RED, GREEN, BLUE }` produce long-typed consts, while
`enum ubyte Color { RED, GREEN, BLUE }` still produces ubyte-typed consts.

**Gate**: the full test suite must pass after this change, and enum examples
must produce identical output on the virtual target.

#### 2. Verify / adjust ConstantFoldingOptimizer declaration-site guard

**File: `codeOptimizers/src/prog8/optimizer/ConstantFoldingOptimizer.kt`**

The branch added a guard so LONG literals that are the direct value of a
`const long` declaration are not shrunk at the declaration site:

```kotlin
val parentVarDecl = parent as? VarDecl
if(parentVarDecl!=null && parentVarDecl.type==VarDeclType.CONST && parentVarDecl.datatype.isLong) {
    return noModifications
}
```

Whether this guard is strictly needed in the current codebase is not yet
certain: `VariousCleanups` preserves the declared `long` type, and
`IdentifierReference.constValue()` re-casts the value back to `long` on
substitution. Test first whether `const x = 5` (long) survives to the simple
AST as a long-typed constant with the correct value; add the guard only if a
mismatch appears. If needed, add it at the top of `after(numLiteral, parent)`
(lines 34-49) and re-run the gate.

#### 3. Initializer expression edge cases (non-literal initializers)

The visitor handles these cases via expression-type inference:

- **integer literal** (`const A = 42`) -> `long` (the default).
- **float literal** (`const PI = 3.14`) -> `float` (expression-type inference).
- **bool literal** (`const FLAG = true`) -> `bool` (expression-type inference).
- **identifier reference** (`const X = PI` where PI is a float const) -> `float`
  (inherits referenced identifier's type). For integer chains (`const B = A + 1`)
  the referenced identifier is long, so the result is long.
- **`memory()` call** (`const M = memory("name", 100, 0)`) -> `pointer`
  (expression-type inference). The `memory()` builtin returns POINTER
  (BuiltinFunctions.kt:177). This matches the target-dependent address type
  used by `SimplifiedAstMaker` (line 31).
- **other function calls** -> `long` (default; other builtins like `sizeof()` return
  integers).

Cases that fall through to the long default and need validation:

- **char literal** (`const CH = 'a'`) -> `long` by default. `CharLiteral` is not
  a `NumericLiteral`, so it falls through. `charLiteralsToUByteLiterals`
  (Compiler.kt:566) later turns the value into a ubyte literal; the long datatype
  is preserved by `VariousCleanups` (line 72-73) and the value (<= 255) always
  fits. Verify with a test program that a char const compiles and yields the
  correct value.
- **binary/unary expressions** (`const X = A + B`) -> `long` by default. Verify
  with a test program.
- **address-of** (`const P = &label`) -> `long` by default (address is integer).
  Verify with a test program.

#### 4. Optional type-frugality: ConstExprEvaluator bitwise results

**File: `codeOptimizers/src/prog8/optimizer/ConstExprEvaluator.kt`**

The current bitwise handlers return LONG for LONG operands (lines 77-78, 92-93,
107-108). With consts defaulting to long, expressions like
`const PAGE = (A & $F0) | (B & $0E)` evaluate with LONG intermediates. This is
correct (the final value gets shrunk at use sites) but wider than necessary.
The branch's `adjustedNumericForBitwise` reduced such results immediately.

Deferred: this is an optimization, not a correctness issue. Only implement it
if the IR comparison (`-compareir`) shows excessive widening that matters.

#### 5. Backwards compatibility verification

Because untyped consts are currently an error, the change is purely additive.
Still, the gate must verify that explicitly typed consts produce byte-identical
results before and after the change, by compiling the stdlib and examples and
comparing output.

## Backwards Compatibility

Yes. Untyped const declarations fail to compile today, so no existing program
uses them. Explicitly typed consts take the identical code path as before
(the visitor change only affects `datatype==null`). `const long X = 5`
already works today and continues to work; the existing optimizer machinery
(VariousCleanups preservation + ConstantFoldingOptimizer use-site reduction)
already handles long consts. The dropped `-typedconsts` mode was the only part
of the old branch that changed behavior of existing declarations.

## Testing

### Validation Approach

Primary testing uses the **virtual target** and prog8c's IR/VM debugging
features, per `AGENTS.md`. This is the absolute gate: no other codegen backend
may be modified or relied upon until the virtual target passes.

**Debugging workflow for each change:**
1. **Syntax check**: `prog8c -check input.p8`
2. **AST inspection**: `prog8c -target virtual -printast1 input.p8` - verify
   untyped consts get datatype LONG (or FLOAT/BOOL).
3. **Simple AST inspection**: `prog8c -target virtual -printast2 input.p8` -
   verify const values/types survive to the simple AST.
4. **IR inspection**: `prog8c -target virtual -out /tmp/out input.p8`, inspect
   the `.p8ir` file.
5. **IR comparison**: `prog8c -target virtual -compareir base.p8ir new.p8ir`
   (see `AGENTS.md`) to see what the optimizer changes.
6. **VM execution**: `prog8c -target virtual -emu input.p8` - run and verify
   stdout output.
7. **VM trace**: `prog8c -vm input.p8ir -vmtrace` - step through IR execution.
8. **Disable optimizations**: `prog8c -target virtual -noopt -emu input.p8`
   to isolate optimizer issues.
9. **Full regression**: `gradle build --console=plain --quiet` - all unit tests.

### Test Programs for Every Code Alteration

**Rule: every major code alteration must ship with a Prog8 test program that
exercises exactly that alteration, before any further alteration is made.**
This is a hard gate per alteration, not just at the end of a stage. A "major
code alteration" is any change to: the parser visitor, constant folding,
type/value checks, cleanup passes, type inference, or codegen behavior that
touches how consts are represented or validated.

Workflow for each alteration:

1. Write a minimal `.p8` program (or an inline snippet in a Kotlin test, per
   the `compiler/test` conventions - see `compiler/test/arithmetic/*.p8` for
   standalone files and `compiler/test/vm/TestCompilerVirtual.kt` for
   virtual-target runtime tests) that:
   - uses the feature being altered (e.g. an untyped `const` in every one of
     the initializer forms from section "3. Initializer expression edge cases"),
   - prints results to stdout via `txt.print_*` and a `@shared` variable
     assertion style, so the virtual target can verify values,
   - contains a comment naming the alteration it validates.
2. Run it through the full debugging workflow above (`-check`, `-printast1`,
   `-printast2`, `-noopt`, `-emu`, `-vmtrace` as needed) and confirm the
   expected output.
3. Keep the test program in the repo (add it as a unit test in
   `TestConst.kt`, or as a `.p8` file under `compiler/test/` wired into an
   existing test), so the gate is repeatable via `gradle build`.

Required test programs (one per alteration):

| Alteration | Test program validates |
|------------|------------------------|
| Visitor type inference | `const A = 42` -> long; `const PI = 3.14` -> float; `const FLAG = true` -> bool; `const N = -5` -> long; `const L = 1000000000` -> long |
| Float ref const | `const X = PI` (PI is float const) -> float, value correct |
| Pointer const | `const M = memory(...)` -> pointer, address correct on virtual target |
| Long const at use sites | `ubyte @shared v = A` (and word/long targets) yields the correct runtime value on virtual target |
| Chained consts | `const B = A + 1` compiles, both long, value correct |
| Char const | `const CH = 'a'` compiles and yields 97 at runtime |
| Address-of const | `const P = &label` compiles and P equals the label address |
| Bitwise widening | `const PAGE = (A & $F0) \| (B & $0E)` evaluates correctly |
| Explicit typed consts | `const ubyte C = 5` behaves byte-identically to before the change (backwards compat) |
| Untyped enum | `enum Color { RED, GREEN, BLUE }` -> long consts, values 0, 1, 2 |
| Typed enum | `enum ubyte Color { RED, GREEN, BLUE }` -> ubyte consts, values 0, 1, 2 (unchanged behavior) |
| @shared consts | `@shared const X = 42` compiles and is accessible from assembly code |
| Array size from const | `ubyte arr[SIZE]` where `const SIZE = 10` compiles and array has correct size |
| Var-to-const promotion | Var with constant value gets promoted to const by `ConstantIdentifierReplacer`, retains original narrow type |
| Backwards compat | Full stdlib + examples still compile/run with identical output on virtual target |

### Unit Tests (compiler/test/ast/TestConst.kt)

- `const FORTY_TWO = 42` - datatype must be `long`.
- `const PI = 3.14` - datatype must be `float`.
- `const FLAG = true` - datatype must be `bool`.
- `const LARGE = 1000000000` - stays `long`.
- `const NEG = -5` - `long` (not byte).
- Explicitly typed consts (`const ubyte C = 5`, `const float PI = 3.14`,
  `const bool FLAG = true`) - unchanged behavior.
- Chained consts: `const A = 5` + `const B = A + 1` - both long, correct values.
- Long const used in a byte context: `ubyte @shared v = FORTY_TWO` - compiles,
  value correct at runtime via virtual target.
- `const SIZEOF = sizeof(...)` / `const P = &label` (address-of) - compile and
  produce correct values on virtual target.
- `const M = memory("name", size, align)` - compiles and slab address correct on
  virtual target.
- Untyped enum `enum Color { RED, GREEN, BLUE }` - member consts are long type.
- Typed enum `enum ubyte Color { RED, GREEN, BLUE }` - member consts are ubyte type
  (unchanged behavior).
- `@shared const X = 42` compiles and is accessible from assembly code.
- Const used in array size: `ubyte arr[SIZE]` where `const SIZE = 10` compiles correctly.
- Var-to-const promotion: var with constant initial value retains original narrow
  type when promoted to const by `ConstantIdentifierReplacer`.
- Stdlib smoke test: existing library routines using consts still compile and
  behave identically.

Use `@shared` variables and check runtime values, per `AGENTS.md`.

## Risk & Mitigations

1. **Long const value/type mismatch somewhere in the pipeline.** The declared
   type is long but a pass may shrink the value, confusing later checks.
   - *Mitigation*: the `-noopt` / `-printast1` / `-printast2` workflow isolates
     this; add the ConstantFoldingOptimizer guard if the mismatch shows up.
2. **Wider intermediate arithmetic on 6502-family targets.** With long consts
   everywhere, expressions involving consts may produce wider IR until use-site
   reduction kicks in.
   - *Mitigation*: `-compareir` shows the impact; only affects non-virtual
     targets, which are gated off until the virtual target passes.
3. **Enum desugaring.** Enums become `VarDecl` consts with explicit
   `NumericLiteral` values and optimal types (AstPreprocessor.kt:560-562).
   Untyped consts do not affect enums since enum members always carry a type.
   Verify with existing enum tests.
4. **Var-to-const promotion.** `ConstantIdentifierReplacer.kt:56-145` promotes
   eligible vars to consts but copies the var's original datatype
   (`.copyFrom(decl)`), so promoted consts keep their narrow type and are not
   affected by the long default.

## Delivery Plan

### Stage 1: Core Compiler Change

- Modify `visitConstdecl` in `Antlr2KotlinVisitor.kt` to use literal type
  detection (float -> float, bool -> bool, else -> long) instead of throwing
  `SyntaxError("datatype missing")`.
- Add the ConstantFoldingOptimizer declaration-site guard only if the
  verification shows it is needed.
- **Per-alteration gate**: each change ships with its test program from the
  "Test Programs for Every Code Alteration" table and passes the virtual-target
  debugging workflow before the next alteration starts.
- **Gate**: `gradle build --console=plain --quiet` passes; existing tests green.

### Stage 2: IR/Virtual Target Verification (absolute gate)

- Compile and run the stdlib and examples with `prog8c -target virtual -emu`
  and verify stdout output is unchanged (backwards compatibility check).
- Add the new const unit tests to TestConst.kt.
- Add the `memory()` / address-of / float-ref test programs and resolve the
  decision points they expose (see section 3 of Proposed Changes).
- Verify `-noopt`, `-compareir`, `-vmtrace` behavior.
- **Gate**: no other codegen backend may be modified until this passes.

### Stage 3: Example Programs

Rewrite example programs to use the new streamlined const syntax, one at a time,
verifying each on the virtual target before moving to the next. The standard
library files are left untouched - the change is purely additive and existing
typed consts continue to work.

Process per example:
1. Modify a single example `.p8` file to use untyped consts where appropriate.
2. `prog8c -target virtual -emu example.p8` - verify output is byte-identical
   (or shows expected differences if demonstrating the new feature).
3. Only virtual target at this stage.

### Stage 4: Documentation

- Update `docs/source/variables.rst` "Constants" section (lines 200-211) to
  document optional types and the long default.
- Update const examples in `docs/source/programming.rst`.
- Update the const-long item in `docs/source/todo.rst` (line 24) to match the
  implemented plan (already done).
- **Gate**: `gradle build --console=plain --quiet` still green.

### Stage 5: Other Backends

**Only after virtual target is verified.**

1. **Verify no codegen changes needed**: Run the full test suite with 6502
   (c64, c128, cx16, pet32) and m68k (qemu68k, amiga500) targets. Expected to
   pass without any changes since consts are folded into immediates before
   codegen and long consts already work end-to-end.

2. **Convert examples per target**: Once confirmed no codegen changes are
   needed, convert the example programs for each target one by one, checking
   results on each before moving to the next. Order: virtual (already done in
   Stage 3), then c64, c128, cx16, pet32, qemu68k, amiga500.

   Process per target/example:
   1. Compile the example for the target: `prog8c -target <target> example.p8`
   2. Run in the appropriate emulator/simulator and verify output
   3. Move to the next example/target only after confirming correctness
