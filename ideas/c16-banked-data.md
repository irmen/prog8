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

## 1. Banked data access library (cx16)

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
