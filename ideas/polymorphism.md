# Manual Generic Subroutines

## Overview

Prog8 could support a limited form of polymorphism for subroutines by
specializing a generic subroutine for each concrete type used by the program.
This is compile-time polymorphism, not dynamic dispatch: every generated
specialization is an ordinary Prog8 subroutine with a concrete ABI and data
layout.

The feature is intended to generalize behavior that is currently hardcoded for
builtin functions such as `abs`, `min`, and `divmod`.

## Proposed Syntax

Generic type variables are declared in a suffix clause, alongside other
subroutine metadata such as `clobbers()`:

```prog8
sub identity(T value) -> T generic(T) {
    return value
}
```

Multiple type variables are allowed:

```prog8
sub choose(T first, U second) -> T generic(T, U) {
    return first
}
```

The names in `generic(...)` are type variables scoped to that subroutine. They
are placeholders, not concrete datatypes. A type-looking name that is not
listed in the clause remains subject to the normal datatype lookup rules.

The generic clause is preferable to treating every uppercase name as generic:
it makes the declaration explicit and leaves unknown type names as ordinary
compiler errors.

## Specialization

Calls infer type variables from their arguments. For example:

```prog8
identity(byteValue)
identity(wordValue)
```

could create these internal specializations:

```text
identity__byte
identity__word
```

Each specialization is generated at most once and is cached by its generic
subroutine and concrete type mapping. The generated name is an implementation
detail and need not be visible in Prog8 source.

The initial design should use inference only. Explicit type arguments, such as
`identity<byte>(value)`, may be added later if return-only type variables or
ambiguous calls create a practical need.

## Type Rules

- Every type variable used in a parameter or return type must be declared in
  `generic(...)`.
- A type variable occurring more than once must resolve to the same concrete
  type.
- Conflicting bindings are errors rather than implicit conversions.
- Type variables should initially be inferable from at least one argument.
- The generic body is checked after substitution, using the normal type rules.
- Operations unsupported by the selected concrete type produce an ordinary
  type-checking error in that specialization.

For example:

```prog8
sub same(T left, T right) -> T generic(T)
```

must reject a call combining a `byte` and a `word`, unless the source contains
an explicit cast that makes both arguments the same type.

Generic subroutines may use type variables in scalar, pointer, and array type
positions where the existing datatype system supports those forms:

```prog8
sub first(T[] values) -> T generic(T)
sub assign(T* destination, T value) generic(T)
```

Generic data types, generic globals, type constraints, and overload sets are
out of scope for the first implementation.

## Compiler Design

The existing builtin specialization pipeline is the main implementation model:

1. Parse the `generic(...)` suffix and retain the generic declaration in the
   compiler AST.
2. Resolve a call to a generic subroutine and infer a mapping such as
   `T -> word`.
3. Look up a cached specialization using the generic declaration and mapping.
4. If absent, clone the generic subroutine and substitute concrete `DataType`
   values in parameters, return types, local declarations, and nested type
   expressions.
5. Register the specialized subroutine under a unique generated name.
6. Run the normal type checking, cast insertion, statement reordering,
   optimization, and code generation on the concrete clone.

Instantiation must happen before transformations that depend on concrete
parameter layout or calling convention. The resulting specialization should
then be indistinguishable from a normal user-defined subroutine to later
compiler phases and backends.

The likely implementation areas are:

- `parser/src/main/antlr/Prog8ANTLR.g4` for the suffix grammar.
- `compilerAst` for generic declarations, type-variable references, and AST
  cloning/substitution.
- `compiler/src/prog8/compiler/astprocessing` for inference and specialization
  registration.
- `codeCore` for reuse of existing `DataType` and compatibility logic.
- `SymbolTable` handling for generated names and cached specializations.

Existing builtin-related code is useful as a model, especially
`ConstantIdentifierReplacer`, `VerifyFunctionArgTypes`, and `TypecastsAdder`.
It should not be copied wholesale: builtins use hardcoded dispatch, while
generic subroutines need ordinary cloned AST nodes and symbol-table entries.

## Ordering and Recursion

Specialization should occur before ABI-sensitive transformations such as string
and array parameter normalization. A generic body should not be type-checked
only once with unresolved type variables, because operators, indexing, pointer
operations, and layout decisions depend on the concrete type.

Recursive generic calls need a specialization cache entry before processing the
body, so direct recursion can resolve to the specialization currently being
built. The compiler should detect unbounded specialization, such as recursive
calls that continually create new type mappings, and report an error rather
than recurse indefinitely.

## Compatibility and Code Generation

This feature does not require runtime support or backend-specific generic
logic. After specialization, code generation sees only concrete types. The
same generated code can therefore be used by the 6502, m68k, and virtual
backends, subject to their existing datatype support.

Specialized routines should participate in normal unused-code removal. A
generic declaration with no call sites should not emit any code, and only the
concrete specializations reachable from calls should be retained.

## Implementation Stages

### Stage 1: Syntax and Representation

- Add the optional `generic(T, U)` suffix to subroutine declarations.
- Store the declared type-variable names in the compiler AST.
- Reject undeclared type-variable names in generic declarations.

### Stage 2: Single-Variable Specialization

- Support one type variable used in parameters and the return type.
- Infer it from a call argument.
- Clone the subroutine and substitute concrete types.
- Cache and register the generated specialization.

### Stage 3: Multiple Variables and Nested Types

- Support multiple independent type variables.
- Support variables in arrays and pointers.
- Validate repeated variables and conflicting bindings.

### Stage 4: Diagnostics and Optional Explicit Arguments

- Improve errors for failed inference, conflicting bindings, and unsupported
  operations.
- Consider explicit type arguments only after inference-based usage is proven
  useful.

## Testing

Tests should cover:

- Parsing `generic(T)` and `generic(T, U)` declarations.
- Identity-style routines specialized for byte, word, and other supported
  concrete types.
- Correct return-type inference.
- Reuse of the same specialization at multiple call sites.
- Different specializations of one generic subroutine in the same program.
- Conflicting bindings for repeated type variables.
- Generic variables in pointer and array positions.
- Invalid operations rejected after specialization.
- Recursive calls and specialization-cycle diagnostics.
- Unused generic declarations and unused specializations removed normally.
- Virtual-target execution plus representative 6502 and m68k compilation.

The virtual target is the preferred first verification path because it provides
fast compilation and observable runtime behavior. Existing builtin
specialization tests provide a useful structure for compiler unit tests.

## Non-Goals

This proposal does not introduce:

- Runtime type parameters or reflection.
- Dynamic dispatch or interfaces.
- General user-defined overload resolution.
- Generic structs or generic modules.
- Type constraints such as `T: Numeric`.

The goal is a small, explicit, compile-time specialization mechanism that
extends the existing builtin pattern without requiring a separate `varsub`
keyword.
