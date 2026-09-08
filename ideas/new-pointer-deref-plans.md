# Pointer Dereference Grammar Improvements

**Status: implemented.** Both implicit `^^` (`listarray[2].value = 123`) and explicit
`^^` before an index (`l1^^.s[0] = 4242`) on assignment targets now work: the grammar
accepts an optional index on the `pointerdereference` trailer, and `CodeDesugarer`
lowers it to poke-style writes (reads to peek calls). The comprehensive Option B
redesign below was not needed and remains documented for the long-term
"cursed hybrid" cleanup only.

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

Both `xtest`s in `compiler/test/TestPointers.kt` are now enabled as regular tests:

- `array indexed assignment with explicit dereference before index writes correct memory on VM`
- `a.b.c[i].value = X where pointer is struct compiles` (rewritten from the old
  error-message expectation, which no longer matches)

## Workaround

Use explicit `^^` for now:

```prog8
np[2]^^.field = 9999
```
