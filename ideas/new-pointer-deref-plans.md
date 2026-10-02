# Pointer Dereference Grammar Improvements

**Status: implemented (for the common cases).** Both forms now work:

```prog8
listarray[2].value = 123    ; implicit dereference on an assignment target
l1^^.s[0] = 4242            ; explicit ^^ with an index on the trailing field
```

These are handled by two separate mechanisms:

1. **Implicit form (`ptr[i].field = x`)** - parsed via the pre-existing
   `ArrayindexedDerefTarget` grammar alternative (`arrayindexed ('.' identifier)+`),
   which builds an `AssignTarget` with a `dotExpression` (a chain of `.` binary
   expressions). `CodeDesugarer.before(assignment)` decomposes the chain, evaluates
   the base expression once into a temporary address variable, resolves the struct
   type and field offsets, and lowers the write to a poke-style call (reads become
   peek calls).
2. **Explicit form (`p^^.field[i] = x`)** - the `pointerdereference` grammar rule
   gained an optional index on its trailer (`('.' field = identifier
   fieldindex = arrayindex?)?`). The visitor normalizes it into an
   `ArrayIndexedPtrDereference` chain, and `CodeDesugarer.after(deref)` rewrites it
   to poke-style writes / peek calls.

The comprehensive Option B redesign below was not needed and remains documented for
the long-term "cursed hybrid" cleanup only.

## Problem

`^^` used to be required in assignment contexts even when the dereference is implied
by array indexing or field access:

```prog8
listarray[2].value = 123    ; FAILED - parse error
listarray[2]^^.value = 123  ; worked but awkward
```

In expression contexts `ptr[5].field` already worked; only the assignment-target
grammar was missing the implicit form.

## Why a minimal grammar fix does not work

Making `POINTER` (`^^`) optional in the existing `singlederef` rule creates
unresolvable ambiguity with `scoped_identifier` in ANTLR 4:

- `a.b` matches both scope resolution and struct field access.
- `scoped_identifier` greedily matches `ptr.field`, leaving array indices stranded.
- `singlederef` places `POINTER` at the end, so `ptr^^.field` has nowhere to put
  the trailing `.field`.

A simple Option A style tweak has been investigated and rejected.

## Remaining approach: comprehensive redesign (Option B)

Restructure `.`, `^^`, and `[]` as postfix operators with equal precedence:

```antlr
dottedChain : primary (('.' identifier) | ('[' expression ']') | POINTER)* ;
primary    : scoped_identifier | directmemory | '(' expression ')' ;
```

This lets the AST disambiguate scope resolution versus field access. It requires:

- Grammar redesign in `Prog8ANTLR.g4`.
- Unify `PtrDereference` and `ArrayIndexedPtrDereference` into a single chain
  representation (see the TODO in `AstExpressions.kt`).
- Visitor and `SymbolTable` lookup updates.

The codegen backends (IR, 6502, m68k, new6502) receive `PtPointerDeref` unchanged
after desugaring, so no backend changes are required.

## Skipped tests

Both `xtest`s in `compiler/test/TestPointers.kt` are now enabled as regular tests:

- `array indexed assignment with explicit dereference before index writes correct memory on VM`
- `a.b.c[i].value = X where pointer is struct compiles` (rewritten from the old
  error-message expectation, which no longer matches)

## Remaining limitations

The implementation covers the happy paths only; several patterns are still
unsupported and surface as errors or compiler TODOs:

- Multiple array-indexed dereferences in one chain (e.g. `z[i]^^[j]`) hit a
  `TODO("support multiple array indexed dereferencings")` in `CodeDesugarer`.
- Non-numeric (e.g. struct-valued) `ArrayIndexedPtrDereference` assignment targets
  are not rewritten; some paths fall into `TODO("cannot translate ...")`.
- Patterns the desugarer does not rewrite are caught by a fallback error in
  `AstChecker` ("no support for getting the target value of pointer array indexing
  like this yet. Split the expression by using an intermediate variable.").
- The `pointerdereference` grammar rule itself is still awkward (noted by an inline
  TODO in `Prog8ANTLR.g4`); it is one of the motivations for the Option B cleanup.
