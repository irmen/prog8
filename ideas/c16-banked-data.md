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

## 1. Bank-transparent data access (cx16)

> **Reviewed 2026-09-26. Most use cases are already solvable with existing
> features, and the kernal primitives `fetch`/`stash` have important safety
> constraints. Recommendation: documentation first, then small `syslib.p8`
> wrappers, and only then a module. See "Recommendation" at the end.**

### What already exists

Banked *code* is well covered, in more ways than just `callfar`:

- `callfar` / `callfar2` builtin functions for raw far calls
  (`docs/source/libraries.rst`).
- `extsub ... @bank <n>` with automatic bank switching around the call, where
  `<n>` may be a constant, a `ubyte` variable, or a *bank selector subroutine*.
  Selector subroutines get a unique per-`extsub` call-site ID (0-255) passed in
  `.A`, and the compiler lists the assignments in a `.bankedcalls` file. This
  is aimed at overlay managers and dynamically loaded libraries; see
  `docs/source/technical.rst` and `examples/cx16/banking` and
  `examples/cx16/dynamicbanking`.
- Manual bank control: `cx16.rambank()`, `cx16.rombank()`, and the
  push/pop pair `cx16.push_rambank()` / `cx16.pop_rambank()`
  (`syslib.p8:863`, `:902`, `:912`).

Known limitation: `callfar`/`callfar2` and variable-bank `extsub` use
self-modifying code to patch the `JSRFAR` operands, so they are rejected under
`%option romable` ("variable bank extsub has no romable code-generation for the
required jsrfar call", see `AstChecker.kt:2229`). See `todo.rst` for the
planned RAM trampoline. This is orthogonal to data access.

Banked *data* placement also already exists:

- `-varshigh <rambank>` (or the `%varsaddress <address>` directive) places
  uninitialized non-ZP variables **and** `memory()` slabs in the
  `$A000-$BFFF` window of the chosen HIRAM bank. The compiler emits no bank
  switching, and `init_system` / `init_system_phase2` do select that bank
  (`sta $00` with `PROG8_VARSHIGH_RAMBANK`) before the BSS-clearing startup
  code runs, so the variables *are* zero-initialized in the right bank. See
  `docs/source/compiling.rst` and `docs/source/programming.rst`.
  Verified in the emulator: a `memory()` slab placed with `-varshigh 3` really
  lives in bank 3, and reading it after `cx16.rambank(3)` returns what was
  written there.
- `%output library` + `diskio.load(..., $a000)` for loading binary blobs into a
  bank at runtime (`docs/source/binlibrary.rst`).
- Fixed addresses in a specific bank are already used by the existing
  libraries, e.g. `gfx_hires.p8:825-826` reads the ROM charset from bank 1 at
  a hardcoded `$f000`.

So the use cases originally listed for this feature - graphics assets, large
lookup tables, sample data in high RAM - are already covered by placement plus
`cx16.rambank()`.

### The actual gap

The one thing that is genuinely missing is **bank-*transparent*** access: code
that wants a byte out of bank N *without* disturbing the currently selected
bank register, e.g. inside an IRQ handler, or while the `$A000-$BFFF` window is
already mapped to a different bank for other purposes. For that case the CX16
kernal provides two primitives, exposed in `syslib.p8:479-480`:

```prog8
extsub $ff74 = fetch(ubyte zp_startaddr @A, ubyte bank @X, ubyte index @Y)  clobbers(X) -> ubyte @A
extsub $ff77 = stash(ubyte data @A, ubyte bank @X, ubyte index @Y)  clobbers(X)
```

Note they are in the `cx16` block, so the Prog8 names are `cx16.fetch()` and
`cx16.stash()`. Both round-trip correctly today (verified by writing and
reading a byte through HIRAM bank 3 in the emulator).

Their real interface, from the kernal source
(`kernal/drivers/x16/memory.s` in `X16Community/x16-rom`):

- The base address is **not** a parameter. `fetch` takes the *zeropage address
  of a pointer* in `.A`; `stash` reads the zeropage address of the pointer
  from the fixed `stavec` variable at `$03B2` (`syslib.p8:181`) and has no way
  to receive it as an argument. This asymmetry is inherited from the C128
  kernal, where the same two entry points are declared as `INDFET` / `INDSTA`
  (`c128/syslib.p8:360-361`, using `$02b9` instead of `$03B2`).
- Only an 8-bit index in `.Y` is available, so a single call reaches at most
  256 bytes from the base; crossing that boundary means updating the base
  pointer. (`.Y` wrapping within a bank is harmless, since a bank's address
  space is contiguous - it is the 256-byte *reach* that is limited.)
- The zeropage address is effectively 8-bit: `stash` builds its indirect
  pointer with `stz imparm+1`, so the pointer must live in `$00-$FF`. So a
  wrapper needs a dedicated ZP word of its own, at a hardcoded address.
- `.X` is not a plain RAM bank number. It is a combined *memory configuration*
  byte that the routine writes to **both** `$00` (`ram_bank`) and `$01`
  (`rom_bank`) - see `inc/io.inc` in the ROM source. For the normal
  `$A000-$BFFF` RAM case the ROM bank half is irrelevant (it is restored right
  after the access), but addresses at or above `$C000` select ROM/expansion
  space instead, and `stash` can write there.
- There are no multi-byte variants. Word access means two byte calls.

### Corrections to the earlier version of this item

Three claims in the previous draft were wrong, and the first one was dangerous.

**1. Bank register behavior.** Both routines write `$00` (ram_bank) and `$01`
(rom_bank). `fetch` protects the access with `php` / `sei` / `plp`, but the
RAM bank is restored *after* interrupts are re-enabled:

```asm
fetch:  lda ram_bank / pha / lda rom_bank / pha
        txa / sta ram_bank          ; $00 written
        plx / php / sei
        jsr fetch2                  ; fetch2: sta rom_bank / lda (fetvec),y / stx rom_bank
        plp                         ; <-- IRQs enabled again
        plx / stx ram_bank          ; <-- $00 restored only now
```

`stash` changes `ram_bank`/`rom_bank` and does not disable interrupts. Key
constraints:

- `fetch` has an IRQ window between `plp` and `stx ram_bank`.
- `stash` is **not IRQ-safe** when called with interrupts enabled.
- Both are **not reentrant**: `fetvec`, `stash0`, and the globals `stavec`/`imparm`
  are shared state. If an IRQ calls `fetch`/`stash` while another is in progress,
  it will corrupt the outer call.
- The base pointer for `stash` is read from the fixed `$03B2` (`stavec`) - there
  is no parameter to specify it.
- The bank argument in `.X` is a combined RAM/ROM configuration byte (not a plain
  RAM bank number). Addresses at `$C000+` may select ROM/expansion space.
- Only an 8-bit index in `.Y` is available; crossing a 256-byte boundary requires
  updating the base pointer.
- The zeropage pointer must live in `$00-$FF` due to `stz imparm+1` in `stash`.

Any wrapper must document these constraints explicitly. To be safer around bank
switching, it must own its ZP pointer and `stavec`, and may need to bracket with
`sei`/`cli` as appropriate.

**2. Data placement.** `-varshigh <rambank>` (or `%varsaddress`) places uninitialized
non-ZP variables and `memory()` slabs in the `$A000-$BFFF` window of the chosen
HIRAM bank. They are zero-initialized during startup with that bank selected.
Direct window access via `cx16.rambank()` is efficient and avoids the overhead
and safety issues of `fetch`/`stash`.

**3. Banked code.** `extsub @bank <n>` (with constant/variable/selector forms) and
`callfar`/`callfar2` already cover banked calls. Variable-bank `extsub` is not
romable due to self-modifying code generation for `JSRFAR` (see `todo.rst`).

### Cost

The reason this is not simply "always use `fetch`" is speed. Hand-counting the
kernal routine gives roughly 20 instructions / ~70-80 cycles for one
`cx16.fetch` (two `pha`, the `sei`/`plp` pair, four bank-register writes, and
a call into RAM-resident code), versus about 5 instructions / ~15 cycles for
`cx16.rambank(n)` (which inlines to `lda #n` / `sta $00`) plus a direct
`lda (zp),y` through the window - and the bank switch can be hoisted out of a
loop entirely, in which case it is free. So `fetch`/`stash` cost roughly 5x
more per byte and are only worth it when the current bank must be preserved.

### Explicitly out of scope

Bank-aware block copy. The kernal's own `memory_copy` at `$fee7`
(`syslib.p8:523`, `cx16.memory_copy`) already special-cases the `$A0xx` I/O
page, but only for the *currently selected* bank - it does not switch banks.
True bank-to-bank bulk transfer is a different mechanism (chunked copying
through the window with the bank register switched per chunk, or staging
through main RAM) with its own design questions, not a wrapper around
fetch/stash.

A real far-pointer *type* (bank + address as a first-class value) would be the
language-level version of this, and is a much larger change than a library.

### Recommendation

1. **Document the existing recipe** in `docs/source/`: `-varshigh <bank>` (or
   `%varsaddress`) to place variables and slabs in a HIRAM bank, combined with
   `cx16.rambank()` / `push_rambank()` / `pop_rambank()` to access them. This
   covers most of the motivation at zero code cost, and the `-varshigh` docs
   deserve a cross-reference from the banking section of `technical.rst`.
2. **If code is wanted, add small `asmsub` convenience wrappers to
   `syslib.p8` first**, rather than a new module - the same idiom already used
   for `set_screen_mode()` wrapping `screen_mode()` (`syslib.p8:660`). A
   `stash_ptr(ptr_zpaddr, bank, index, value)` that stores to `stavec` and
   tail-jumps to `stash` removes the ugliest part of the interface; word and
   word-pair variants follow. Roughly 30 lines, no new module, no new naming to
   bikeshed.
3. **Only then consider a `banked` module**, and when doing so it must document
   the non-reentrancy, `stash` IRQ exposure, the `plp`-before-`stx ram_bank`
   window in `fetch`, the ZP-residency requirement for the base pointer, the
   need to manage `stavec` for `stash`, and the combined RAM/ROM meaning of the
   bank argument. It should also state the 8-bit index limit (256-byte reach per
   base pointer update) and that multi-byte access requires multiple calls.
4. Leave the far-pointer type out of scope.
