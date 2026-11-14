# Memory regions: named bases for cheap 6502 memory access

Status: design proposal, no implementation. Companion to `ideas/betterpointers.md`
(which assumes no language changes). This file proposes a language addition so
the efficient access forms do not depend on luck.

## 0. Open questions (answer these before implementing)

1. `fill`/`copyfrom` as builtins, or range assignment (`region[0 to len-1] = v`)?
2. Should `for cell in region` yield an lvalue (proposed) or a read-only value plus a separate write form?
3. Lift the length cap for fixed-address regions only, or does the 256 limit serve a purpose beyond index width?
4. ZP contract for cursor variables: hard requirement (`@zp`, error if impossible) or warning with documented fallback?
5. 2D regions (`&ubyte[40][25] screen = $0400`, `screen[x, y]`) in the same change, or later?

## 1. Problem

A memory access in Prog8 is an anonymous address expression, `@(BASE + offset)`.
It has no representation the backend can keep, so the 6502 backend rebuilds a
16-bit address in a scratch pair on every store (26-29 cycles). The cheap forms
exist but are fragile:

- `uword base; base[i] = v` is 10 cycles if `base` lands in ZP, 22 otherwise.
  Placement is a usage-score heuristic, not a contract.
- `&ubyte[N] row = ADDR; row[j] = 1` emits `sta row,y` (5-7 cycles), but `N` is
  capped at 256 and the index must be a byte (`row[w]` is a compile error).

Verified: `&ubyte[1000] big = $0400` errors (byte array length 1-256);
`row[w] = 2` errors (array indexing limited to byte size 0..255). A plain
`uword` base in ZP uses `(base),y`; outside ZP it copies to scratch first.

Other languages give the base a name and a type (`screen[pos]`, `ptr[i]`), which
is what lets the compiler pick an addressing mode and reuse an address.

## 2. Proposal

### 2.1 Regions: fixed-address arrays of any length

Lift the length cap for fixed-address arrays to 65535 elements and allow any
integer index, with lowering defined by what the compiler can prove:

| access | compiler knows | lowering | per cell |
|---|---|---|---|
| `region[const]` | constant offset | absolute | 5-7 |
| `region[ubyte]` | index is 8-bit | `sta BASE,x` / `BASE,y` | 5-7 |
| `region[uword]`, length <= 256 | index provably < 256 | low byte as index | 5-7 |
| `region[uword]`, length > 256 | word index, unknown range | page + index via ZP pointer | ~20-26 |
| `for i in 0 to len-1 { region[i] = v }` | range + induction | page spans or cursor | 4-6 |
| `fill(region, v)` | range only | page spans | 3-6 |

The third line is the key: a declared length proves a word index cannot leave
the base page, making it as cheap as a byte index. That information does not
exist in the language today.

Example:

```prog8
&ubyte[1000] buf = $0400
buf[w] = 1        ; word index; cheap iff compiler proves w < len(buf)
buf[j] = 2        ; byte index; always absolute indexed
```

### 2.2 Bulk intent: `fill` / `copyfrom`

Builtins next to `len`/`peek`, no new grammar:

```prog8
fill(screen, 32)
copyfrom(screen, other)
```

A `fill` on a fixed-address region is the one case where base, length and
stride are all known at compile time, so the backend can emit page-span stores
(four `sta base,x` per iteration) and reach hand-written cost. This covers the
bulk-clear stage, which is ~40% of a full screen build and cannot be expressed
as intent today.

### 2.3 Iteration over a region

```prog8
for cell in screen { cell = 32 }
```

Semantically identical to `for i in 0 to len(screen)-1 { screen[i] = 32 }`,
but the compiler sees the whole range and can pick cursor or page-span
lowering. Element is an lvalue.

### 2.4 Word-element regions

`&uword[N] @nosplit` already works and scales the index. Require `@nosplit`
for memory-mapped word regions (already an error without it) and document the
element layout.

### 2.5 Hardware registers unchanged

A single memory-mapped variable is a region of length one; `@(...)` keeps
working. No special case.

## 3. Cursor contract

Regions fix indexed access. Computed positions (`plot(x, y, ...)`) need a
cursor, and the language already has it:

```prog8
uword @zp p = BASE + offset
@(p) = value     ; -> ldy #0 / lda #v / sta (p),y   (10 cycles)
p += 1           ; -> inc / bne / inc                 (9 cycles)
```

Proposal:

1. Document `uword` dereferenced via `@(p)` as the cursor type, requiring ZP
   for the 10-cycle form.
2. Strengthen the allocator: a variable used as the base of `@(var)` or
   `var[i]` becomes a ZP candidate ahead of other DONTCARE variables. Today it
   competes on usage score while pointer-typed declarations jump the queue
   (`AstPreprocessor.kt:308-317`), so identical code can cost 10 or 22 cycles
   depending on unrelated allocation pressure.
3. Warn when such a variable misses ZP, naming the fix (`@zp`, or a region).

This turns a luck-dependent outcome into a documented contract with a visible
warning. See P7/P9 in `ideas/betterpointers.md` for the compiler side.

## 4. What this removes

After this, no program needs `@(BASE + offset)` for a buffer:

- scanning/filling: region iteration, `fill`, or a cursor
- random access, byte offset: `region[byte]`, absolute indexed
- random access, word offset: `region[word]`, one pointer build, cheaper when
  the region proves the page

`@(...)` remains for hardware registers and computed jumps, where a rebuild
per access does not matter.

## 5. Migration

- Existing `@(...)` code compiles exactly as today. Opt-in by declaration.
- The P9 lint becomes a migration aid: "this access could be a region".
- Docs guidance: name buffers, then never mention their address again.
- Lifting the length cap is source-compatible; word indices on regions are
  additive (an error today).

## 6. Portability

| target | `region[ubyte]` | `region[uword]` | `fill` |
|---|---|---|---|
| 6502 | `sta BASE,x` | ZP pointer, or absolute when provable | page spans |
| m68k | `move.b d,(CONST,a2)` | same, one instruction | `move.b` loop or `memset` |
| virtual | `storei.b` with displacement | same opcode | `memset` |

Base + length + element size is the same input on all backends; no
target-specific source needed.

## 7. Costs and risks

- Bounds analysis is the new machinery. Where `index < length` cannot be
  proved, lowering falls back to the current pointer build, so it degrades
  safely. Loop ranges plus constant comparisons cover the cases that matter.
- `fill` and range iteration can duplicate a few instructions per page; apply a
  size cap as with the existing shift-add budgets.
- The 256 cap also limits `str` and general arrays; this lifts it for
  fixed-address regions only, leaving split-word and general layout alone.
- Making `@nosplit` mandatory for all memory-mapped regions is a small
  source-compatibility question (an error today only for words).
- Tests: length > 256, word indices with/without provable ranges, `fill`,
  iteration, plus `simulate()` tests asserting written bytes and no
  per-iteration rebuild.

## 8. Alternatives considered

- Codegen-only fix (`@(const + ubyte)` becomes absolute indexed). Cheapest,
  still worth doing (P4), but the base stays anonymous so word offsets and
  scanning get no help, and users must still know which spelling is fast.
- Addressing-mode suffix on `@`, e.g. `@[i]`. Renames the problem.
- First-class cursor type. `uword` + `@(p)` already lowers to the optimal
  three instructions, so a new type buys safety, not speed. Revisit if safe
  cursors become a goal.
