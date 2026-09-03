# Pointer Dereference Grammar Improvements

**Status: partially implemented.** Listed as an active item in `docs/source/todo.rst`.

Already implemented on master: assignment targets accept parenthesized expressions
and function calls followed by a field chain, e.g. `(expr as ^^Struct).field = value`
and `func().field = value`. These are handled by `assign_target` alternatives
`ParenDerefTarget` / `FunctioncallDerefTarget` and desugared by `CodeDesugarer`
into poke-style writes.

Still pending: implicit `^^` for indexed/field assignment targets such as
`ptr[idx].field = value` and `ptr.field = value`.

## Problem

`^^` is still required in assignment contexts even when the dereference is implied
by array indexing or field access:

```prog8
listarray[2].value = 123    ; FAILS - parse error
listarray[2]^^.value = 123  ; works but awkward
```

In expression contexts `ptr[5].field` already works; only the assignment-target
grammar is missing the implicit form.

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
  representation.
- Visitor and `SymbolTable` lookup updates.

The codegen backends (IR, 6502, m68k, new6502) receive `PtPointerDeref` unchanged
after desugaring, so no backend changes are required.

## Skipped tests

Two tests in `compiler/test/TestPointers.kt` remain skipped (`xtest`) until the
redesign is implemented:

- `array indexed assignment parses with and without explicit dereference after struct pointer`
- `a.b.c[i].value = X where pointer is struct gives good error message`

## Workaround

Use explicit `^^` for now:

```prog8
np[2]^^.field = 9999
```
