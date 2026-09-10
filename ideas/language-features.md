# New language feature suggestions

> **Status: proposed ideas, not implemented. Not a commitment to build any of these.**

Brainstorm list of language/library improvements that seem to fit Prog8's
philosophy (static allocation, explicit types, no hidden runtime costs).
Items already implemented or already tracked elsewhere in `todo.rst` /
`ideas/` are deliberately excluded:

- implicit `for` loop variable declarations: implemented (`da8c3eda9`).
- arrays of struct instances: implemented (`94822b866`).
- `when` choice ranges like `5 to 10 -> ...`: already work and are documented.
- compile-time string concatenation `"a" + "b"` and repetition `"a" * 10`:
  already folded at compile time.
- strongly typed enums, manual generics, immediate struct variables,
  m68k register allocator / stack memory model: explicitly deferred or
  won't-do, see `todo.rst` and the corresponding `ideas/*.md` plans.
- 2D arrays and typed-pointer arrays as struct fields: still open items
  in `todo.rst`.
- pointer dereference grammar cleanup: see `ideas/new-pointer-deref-plans.md`.

## 1. Named struct-field initializers

Allow struct initialization by field name instead of position only:

```prog8
struct Enemy {
    ubyte hp
    ubyte x
    ubyte y
    ubyte frame
    ubyte bank
}

^^Enemy e = ^^Enemy:[hp=100, x=10]   ; rest zero-initialized
```

Today only positional init exists: `^^Enemy e = ^^Enemy:[100, 10, 0, 0, 0]`.
That gets brittle as structs grow (regression risk when fields are added or
reordered, and unreadable for structs with many mostly-zero fields).

**Semantics for unspecified fields: zero-fill.** Matches the language's
existing "everything is zero-initialized" guarantee (globals at start,
locals on sub entry, BSS sections). Unspecified fields behave exactly like
any other variable the user didn't initialize. Requiring all fields to be
named is possible but user-hostile for wide structs; type-level defaults
(`ubyte hp = 100` in the declaration) are a much larger feature involving
the type system and IR symbol table.

**Implementation sketch:**

- Grammar: `staticstructinitializer: POINTER? scoped_identifier ':' arrayliteral`
  currently only accepts positional array literals. Needs a named-arg
  alternative (either inside the array literal or a new production).
- `AstChecker.checkValueTypeAndRangeStaticStructInitializer` already
  validates positional count vs. field count; named init would instead
  map names to fields, reject duplicates/unknown names, and fill the gaps
  with `defaultZero(field.type)`.
- Codegen path afterwards is unchanged: the flattened value list looks
  identical to positional init.

## 2. `%assert` compile-time check

```prog8
%assert sizeof(Enemy) <= 32, "enemy struct grew too large"
```

Evaluated during compilation (`processAst`), emits the message as an error
if the constant expression is false. Essentially free to implement: constant
folding already evaluates such expressions; it is a check on top of
`constValue()`, similar to how the compiler validates array bounds at
compile time.

Use cases: libraries that promise struct layouts/sizes to user code or to
assembly, guarding the 256-byte struct limit early, and sanity-checking
derived constants (`STACK_SIZE * 2 < 512`).

## 3. Arena/slab allocator stdlib module

This is not a new idea - it is deduplicating an existing pattern. Six
examples (`pointers/binarytree.p8`, `pointers/animalgame.p8`,
`pointers/fountain-cx16.p8`, `pointers/fountain-virtual.p8`,
`pointers/hashtable.p8`, `pointers/sortedlist.p8`) each carry a copy of the
same ~8-line block:

```prog8
arena {
    ; extremely trivial arena allocator (that never frees)
    pointer buffer = memory("arena", 4000, 0)
    pointer next = buffer

    sub alloc(ubyte size) -> pointer {
        defer next += size
        return next
    }
}
```

A `prog8lib/arena.p8` module would replace those copies and add the safety
bits every example skipped:

- `init(slab_addr, size)` instead of the hardcoded `buffer`, so the caller
  still picks slab name/size via `memory()` but shares the routines
- overflow check in `alloc()` (return 0 when the slab is exhausted); the
  examples currently walk off the end unchecked
- `reset()` (set `next` back to the slab start) for cheap "free everything"
  between game states (levels, screens), which is the realistic lifetime
  model on these machines
- optional alignment (bump size up to even) so word/struct consumers work
  on m68k targets too

Not dynamic memory management in the language (that stays out of scope by
design), just a sanctioned pattern with the sharp edges filed off. Once
the module exists, the six examples should be simplified to use it.

## 4. Banked data access library (cx16)

Banked *code* (`callfar`) is wired in, and the kernal primitives are already
exposed in `syslib.p8`:

```prog8
extsub $ff74 = fetch(ubyte zp_startaddr @A, ubyte bank @X, ubyte index @Y) -> ubyte @A
extsub $ff77 = stash(ubyte data @A, ubyte bank @X, ubyte index @Y)
```

But using them requires manual zeropage setup per access. The interfaces
are asymmetric (a quirk inherited from the C128 API at the same addresses):
`fetch` takes the zero page *address* of the base pointer in `.A`, while
`stash` takes it via the fixed `stavec` variable at $03B2 instead. Both
also take only an 8-bit offset in `.Y`, so crossing a 256-byte page means
updating the base pointer by hand. There are no multi-byte variants, and
`memory()` slabs are always in main memory so a buffer cannot be declared
to live in a bank.

A `banked` library module would wrap fetch/stash with:

- `far_peek(bank, addr)` / `far_poke(bank, addr, value)` byte helpers
- word/long variants built from the byte primitives
- `far_peek_w(bank, addr, index)` / `far_poke_w(...)` for bank-indexed arrays

Each helper handles the ZP pointer setup (and `stavec` for writes), the
page-crossing base adjustment, and saving/restoring `stavec` around the
call. Note an IRQ hazard: these accesses change no bank register, but any
future variant that touches the RAM bank register ($00) must `sei`/`cli`
around it or save/restore, since kernal IRQ code assumes the bank is
unchanged.

Explicitly out of scope: bank-aware block copy. Banked RAM is only visible
through the 8KB window at $A000-$BFFF, so bulk transfer is a different
mechanism (chunked `memory_copy` through the window with the bank register
switched per chunk, or staging through main RAM for bank-to-bank) with its
own design questions, not a simple wrapper around fetch/stash.

This covers the realistic use cases (graphics assets, large lookup tables,
sample data in high RAM) without touching pointer-size assumptions. A real
far-pointer *type* (bank+address as first-class values) would be the
language-level version of this, but is a much larger change.
