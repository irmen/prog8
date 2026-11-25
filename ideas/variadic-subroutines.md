# Compile-time Variadic Subroutines [won't do]

**Status: won't do unless a concrete need appears.** This is a design exploration
only; no compiler or library files are changed by this document. The M68K
calling-convention motivation disappears once calls are lowered to ordinary
fixed-arity calls. The useful part is printf-style output, which is better
served by a standalone typed `print(...)` intrinsic; homogeneous lists remain
expressible as array plus length.

## Overview

Prog8 has no variadic subroutines, no default parameters, and no user-defined
overloading. The only printf-like mechanism is the Copperline debug queue in
`compiler/res/prog8lib/shared_amiga_emudbg.p8`: callers queue values with
`debug_arg` / `debug_arg_str`, then call `debug_printf(str format)`. The queue is
bounded to 32 values, the values are typeless, and the format string carries
all type information.

That is a reasonable emulator debug facility, but it is not a good language
feature. C-style varargs are also a poor fit: the caller must supply manual type
tags, the callee walks an untyped runtime argument area, and neither side is
type-checked against the other.

This proposal adds variadic subroutines that are fully resolved at compile
time. Each call shape becomes an ordinary fixed-arity subroutine before IR
generation. There is no runtime argument list, no `va_list`, no tagged union,
and no format string.

The design was explored with these constraints:

- The original motivation came from M68K frame-based calling conventions, but
  the chosen lowering is backend-independent.
- The mechanism would work on 6502 and virtual targets without a new ABI.
- Variadic subroutines may not be recursive, directly or indirectly.
- All argument type information is known at compile time.

## Assessment

The design is workable but is not worth implementing as a general Prog8
language feature right now:

- The main ergonomic win, printf-style output, can be delivered by a typed
  `print(...)` intrinsic without user-defined variadics, specialization of user
  routines, or a recursion ban.
- Homogeneous rest is mostly array plus length with extra compiler machinery.
- Per-call-shape specialization hides arity and code-size cost, which conflicts
  with Prog8's explicit size/cost style.
- The compiler and tooling surface is large relative to the benefit.
- `debug_arg` / `debug_arg_str` / `debug_printf` already covers bounded debug
  output.

If a concrete program appears where these alternatives are inadequate, this
design is the fallback.

## Goals

- Let a caller write `sum(a, b, c)` or `log("x=", x, "\n")` naturally.
- Keep full compile-time type checking of every argument.
- Let a callee iterate a homogeneous argument list when that is useful.
- Let a user-defined wrapper process a heterogeneous argument list with
  compile-time unrolling, without runtime tags or type tests.
- Generate ordinary subroutines and ordinary calls for every backend.
- Avoid static scratch buffers that nested calls could corrupt.

## Non-goals

- No runtime-determined argument count.
- No C `...` ABI, `va_start` / `va_end`, or stack walking.
- No caller-written type letters beyond a future optional checked format
  string; the initial printf-like sink has no format string at all.
- No runtime `variant` or tagged descriptor array.
- No dynamic indexing or storage of a heterogeneous tuple; compile-time
  iteration is allowed.
- No variadic `asmsub`, `extsub`, builtin function, or bank subroutine.
- No recursion in or through a variadic subroutine.

## Proposed syntax

### Homogeneous, iterable rest

```prog8
sub sum(...values: uword) -> uword {
    total = 0
    for value in values {
        total += value
    }
    return total
}

x = sum(a, b, c)
y = sum()
```

A rest parameter may follow ordinary parameters:

```prog8
sub sum_with_seed(uword seed, ...values: uword) -> uword
```

The rest element type is explicit. All trailing arguments are coerced to that
type by the normal cast-insertion pass. `len(values)` folds to the call-site
arity, and `for value in values` can be unrolled.

### Heterogeneous, forward-only rest

```prog8
sub log(...parts) {
    if enabled {
        print("[LOG] ")
        for part in parts {
            print(part)
            print(" ")
        }
        print("\n")
    }
}

log("x=", x, " y=", y)
```

`...parts` has no element type. It is a compile-time tuple of the caller's
argument expressions. Inside the callee it can be:

- expanded in a call argument list, written `parts...`, including in the middle
  of the list: `print("[LOG] ", parts..., "\n")`;
- iterated with `for part in parts`, which is unrolled per element and
  type-checked with that element's concrete type.

It cannot be assigned, dynamically indexed, compared as a whole, or stored. A
body that is not valid for every element type is a compile-time error in the
specialization.

This is what makes a generic printf-style wrapper possible: `log` accepts any
mix of printable values, adds its own prefix and separators, and may choose a
different sink intrinsic, while all type information stays known at compile
time.

### Typed print

`print(...)` is proposed as a compiler intrinsic with no format string. Each
expression is printed using its own static type:

```prog8
print("x=", x, " y=", y, "\n")
```

It lowers roughly to:

```prog8
txt.print("x=")
txt.print_uw(x)
txt.print(" y=")
txt.print_uw(y)
txt.print("\n")
```

Initial mapping:

| Static type | Routine |
| --- | --- |
| `str` | `txt.print` |
| `byte` / `ubyte` | `txt.print_b` / `txt.print_ub` |
| `word` / `uword` | `txt.print_w` / `txt.print_uw` |
| `long` | `txt.print_l` |
| `bool` | `txt.print_bool` |
| float | `floats.print` |

A type without a printable lowering is a compile-time error at the print call.
The intrinsic is not a normal subroutine and cannot be redefined. A later,
separate proposal could also accept multi-argument `txt.print(...)` as sugar
for this intrinsic; the first implementation should use the unambiguous
builtin name.

## Lowering

Both rest modes are lowered to ordinary parameters in a generated
specialization. The specialization name is an implementation detail, for
example `sum$3` or `log$2_str_uword_str`.

### Typed rest

For `sum(a, b, c)` with `...values: uword`, emit an ordinary subroutine:

```prog8
sub sum$3(uword v0, uword v1, uword v2) -> uword { ... }
```

References to `values[i]` in the cloned body become `v0`, `v1`, `v2`.
`len(values)` becomes the constant `3`. The call site becomes a call to
`sum$3(a, b, c)` with three ordinary arguments.

### Forward-only rest

For `log("x=", x, "\n")` where `x` is a `uword`, emit:

```prog8
sub log$3(str p0, uword p1, str p2) {
    if enabled
        print(p0, p1, p2)
}
```

Then the print intrinsic lowers to ordinary typed print calls. The
specialization is cached by the sequence of concrete argument types, so calls
with the same type shape share it while different expressions are passed as
ordinary arguments.

A `for part in parts` loop is unrolled into one statement per generated
parameter. In `log$4(str p0, uword p1, str p2, str p3)`, `part` is bound to
`p0`, then `p1`, and so on, and the loop body is type-checked separately for
each concrete type. No runtime type test or descriptor is emitted.

### Nested variadic calls

A variadic routine may call another variadic routine by forwarding its rest
tuple. Discovery of needed specializations therefore walks cloned bodies too.
Because variadic call graphs are acyclic, this terminates. The implementation
can use a worklist and stop when no new specialization is created.

## Recursion ban

A variadic subroutine may not call itself, directly or indirectly. The compiler
checks the call graph and rejects any cycle containing a variadic subroutine
with a clear diagnostic, for example:

```text
variadic subroutine 'main.log' may not be recursive
```

This keeps the number of specializations finite and avoids infinite template
expansion. Ordinary non-variadic recursion is unaffected.

## Arity and diagnostics

- Zero trailing arguments are valid.
- The minimum arity is the number of fixed parameters.
- The maximum arity is capped, proposed default 16, with a clear diagnostic
  beyond it.
- Calls to `asmsub`, `extsub`, builtins, and bank subroutines keep exact arity
  checks.
- The generated specialization names are not visible or callable from Prog8
  source. Symbol dumps and language-server hover show the original rest
  signature.

## Compiler design

The feature is mostly frontend and optimizer work. It happens before
`SimplifiedAstMaker`, so IR, the VM, and both code generators see only ordinary
fixed-arity subroutines. No IR format bump is needed.

Likely implementation areas:

- `parser/src/main/antlr/Prog8ANTLR.g4`: `sub_param`, `sub_params`, and the call
  syntax. Calls already allow arbitrary argument lists.
- `compilerAst/src/prog8/ast/antlr/Antlr2KotlinVisitor.kt`: `visitSub_param`
  records the rest mode.
- `compilerAst/src/prog8/ast/statements/AstStatements.kt`: `SubroutineParameter`
  gains a rest mode and propagates it through `copy` and `equals`.
- `compiler/src/prog8/compiler/astprocessing/AstIdentifiersChecker.kt`: replace
  exact arity with minimum arity for ordinary variadic subroutines.
- `compiler/src/prog8/compiler/astprocessing/AstExtensions.kt`: diagnostics for
  "at least N" arguments.
- `compiler/src/prog8/compiler/astprocessing/TypecastsAdder.kt`: coerce typed
  rest arguments to the declared element type; leave forwarded arguments with
  their own types.
- `compiler/src/prog8/compiler/astprocessing/AstChecker.kt`: keep strict checks
  for asmsub, extsub, builtins, and bank subroutines.
- `compilerAst/src/prog8/compiler/CallGraph.kt`: reject variadic recursion and
  drive specialization discovery.
- A new specialization pass before `SimplifiedAstMaker.kt`: clone the routine
  per call shape, add ordinary parameters, rewrite `values[i]`, `len(values)`,
  and `parts...`, and unroll `for part in parts`.
- `compiler/src/prog8/compiler/astprocessing/SimplifiedAstMaker.kt` and
  `simpleAst` symbol tables: register generated routines with generated names.
- `codeOptimizers/src/prog8/optimizer/Inliner.kt` and other optimizers: treat
  generated specializations as ordinary code; skip the original variadic
  template.
- `codeCore/src/prog8/code/core/BuiltinFunctions.kt` and IR codegen for the
  `print(...)` intrinsic lowering.
- `languageServer` and `AstToSourceTextConverter`: display rest signatures.

After adding or cloning AST nodes, call `symbolTable.resetCachedFlat()` so
symbol lookups remain correct.

## Target scope

M68K is the first target for implementation and validation because its
frame-based calling convention was the original motivation for looking at
variadics. However, this design does not depend on the M68K stack ABI. Since
every variadic call is lowered to ordinary fixed-arity calls before IR, the
same source works on 6502 and virtual targets with no extra codegen support.

A future runtime-count extension would be different: an M68K-only frame-slot
temporary array passed as pointer plus count. That would need changes to
`StackFrameLayout.rewriteCallArgumentLocations`, `CallLocation`, the IR text
codec, and the IR format version. It is explicitly out of scope here.

## Rejected alternatives

### C-style varargs

Rejected: manual type letters, no argument type checking, and runtime stack
walking. This is the ergonomics problem the proposal exists to avoid.

### Typed rest as a runtime slice

The earlier idea of materializing the trailing arguments into a temporary
array and passing pointer plus count was rejected. On 6502 and virtual targets
there are no call frames, so a static scratch array is corrupted by nested
calls such as `sum(1, sum(2, 3), 4)`. It also requires a new slice type, a
runtime count, IR serialization changes, and VM support.

### Runtime tagged descriptors

A `variant`-like descriptor array would allow arbitrary heterogeneous runtime
values, but it introduces type erasure, tag dispatch, payload-size problems
across targets, and a new ABI. The forward-only tuple plus compile-time
unrolled iteration covers prefixes, separators, conditional logging, sink
choice, and uniform per-element processing without that machinery. Truly
type-specific behavior for one element still needs an explicit wrapper at the
call site.

### Fixed maximum arity expansion

Generating `log1` through `log16` is mechanically simple and type-safe, but
the callee cannot iterate a homogeneous rest list, and code size grows for all
arities whether used or not. Per-call-shape specialization grows only for
shapes that are actually used.

### Keeping only the debug queue

Keeping `debug_arg` / `debug_arg_str` / `debug_printf` avoids language changes,
but it remains typeless, capped at 32 values, and tied to Copperline debug
ports. It is not a general-purpose language feature.

## Testing

- Compiler AST and diagnostic tests for minimum arity, arity cap, recursion ban,
  and fixed-arity checks on asmsub, extsub, builtins, and bank subs.
- Specialization tests that inspect generated routine names and parameter
  types, without dumping raw assembly.
- Homogeneous tests using the virtual target where possible, for example
  `sum(1, 2, 3)` and `sum()`.
- Print-lowering tests that inspect the call sequence by parsing generated
  lines, not by string-matching full assembly listings.
- Nested forwarding tests such as an outer wrapper calling an inner wrapper.
- M68K tests should use `writeAssembly = true, assemble = false` so they do not
  depend on `vasm`, which is not available in CI.
- No IR round-trip changes are expected because no IR format change is needed.

## Open questions

- Exact spelling of the rest marker: `...values: T` versus an alternative such
  as `values...: T`.
- Exact name and namespace of the print intrinsic: proposed `print(...)`, with
  optional multi-argument `txt.print(...)` sugar later.
- Which operations are allowed in a `for part in parts` body. Initially,
  anything that type-checks for each concrete element type.
- Default arity cap: 16 is proposed; 32 would match the existing debug queue
  but increases code-size risk.
- Whether a checked format-string variant should be added later for programs
  that want printf-compatible output rather than expression-list printing.
