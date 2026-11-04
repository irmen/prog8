# Amiga bare-metal text helper - design

Design only. No implementation yet. Target: `amiga500` (68000) and
`amiga1200` (68020). Must run on both without CPU-specific code paths.

## Goals

- Render text onto a bare-metal bitmap without OS calls, after
  `custom.grab_system()`.
- Support the one embedded 8x8 font only for now; custom fonts, variable-size
  glyphs, and font switching are out of scope.
- Transparent glyph background: glyph 0 bits never touch the destination.
  At glyph 1 bits, write the exact foreground palette index, including
  color 0, by setting selected planes and clearing unselected planes.
- Arbitrary X/Y placement within caller-provided bounds; no clipping.
- CPU-only (no blitter), so no `blitter.wait()` needed for the text itself.
- Stateless: no cursor; position is passed to each call.
- Composable with the existing blitter/copper/playfield helpers.

## Module

- Name: `text` (bare-metal counterpart of OS `textio`; no import clash in
  bare-metal programs since `textio` is not used there).
- File: `compiler/res/prog8lib/shared_amiga_text.p8` (shared, like
  `shared_amiga_blitter.p8`), plus per-target wrappers
  `amiga500/text.p8` and `amiga1200/text.p8` that `%import shared_amiga_text`.
- Options: `%option merge, no_symbol_prefixing, ignore_unused` (matches the
  blitter helper).
- The renderer may `%import math` to use `math.mul32` for 32-bit row offsets.
- **No `%import graphics`.** The module is self-contained like the blitter
  library: it takes raw geometry and plane pointers (`init`) and never
  references `graphics.BitMap` or any other OS structure. The module is fully
  self-contained: geometry and plane pointers are passed as raw values.
- Module documentation and source comments should state that rendering is
  limited to its embedded 8x8 font for now.

## Font

- Embed `font8x8_basic` (public domain, 128 glyphs x 8 bytes = 1024 bytes)
  as a byte array in **fast RAM** (CPU-only renderer; no blitter access to
  the font is needed, so fast RAM gives quicker reads on 68000 and avoids
  chip-RAM contention):
  ```
  txtdata {
      ubyte[1024] font8x8
  }
  ```
  (No `%option amiga_chipram`.) If a future blitter-based text path is added,
  a chip-RAM copy can be provided separately.
- Bytes 0-127 draw the corresponding glyph normally. Bytes 128-255 draw the
  corresponding glyph in reverse video: use the low 7 bits as the glyph index
  and invert each glyph row. This needs no second font and costs only a small
  per-row XOR or a once-per-glyph branch, so it is included in the design.

## State (private `@shared`, all zero-initialized)

| Name | Type | Purpose |
|---|---|---|
| `pitch` | uword | bytes per row (`BytesPerRow`) |
| `depth` | ubyte | number of bitplanes |
| `planes` | `pointer[8]` | cached bitplane base pointers (max 8 for AGA) |

Font pointer is a fixed label (`txtdata.font8x8`), not state.

## API

```
text.init(uword pitch, ubyte depth, ^^pointer planes) ; raw plane table, no BitMap
text.char(uword x, uword y, ubyte chr, ubyte color)   ; one glyph, caller ensures bounds
text.print(uword x, uword y, str text, ubyte color)   ; string, caller ensures bounds
```

- `init` caches `pitch`, `depth` and copies `depth` plane base pointers from
  `planes` into the cached `planes[8]` array. Depth is clamped to 8. No
  `graphics.BitMap` or other OS structure is used anywhere in the module.
- `char` derives the plane mask from `color` once for that glyph. `print` derives
  it once for the whole string and passes it to a private glyph-render routine.
  `print` also computes the destination row offset once per text row.
  A set mask bit means set that plane under glyph pixels; an unset bit means
  clear that plane there. Thus color 0 clears glyph-shaped pixels to palette
  color 0 while leaving glyph-background pixels unchanged.

## Rendering algorithm (per glyph)

The private renderer receives `x`, a precomputed `rowOffset = y * pitch`,
`chr`, and a precomputed plane mask (caller guarantees in-bounds):
1. `shift = x & 7`, `byteCol = x >> 3`, `rowBase = rowOffset + byteCol`.
2. `glyphBase = font8x8 + (chr & $7F) * 8`; if bit 7 of `chr` is set,
   invert each 8-bit glyph row before shifting it. This makes the former
   glyph background part of the reverse-video cell.
3. For `row` in 0..7:
   - `g = font8x8[glyphBase + row]`.
   - If `g == 0`: advance row pointer, continue (transparent row).
   - If `shift == 0`:
       - For each plane `p`:
           - if the plane mask has bit `p`: `planes[p][rowBase + row*pitch] |= g`
           - otherwise: `planes[p][rowBase + row*pitch] &= ~g`
   - If `shift != 0`:
       - `left  = g >> shift`
       - `right = (g << (8 - shift)) & $FF`
       - For each plane `p`, apply the same set/clear operation to both
          coverage bytes: OR `left`/`right` when bit `p` is set in the plane mask,
         otherwise AND with their complements. The writes are:
           `planes[p][rowBase + row*pitch]` and
           `planes[p][rowBase + row*pitch + 1]`; the caller guarantees the
           glyph fits within the row.

Glyphs are 8 pixels wide, so `x` advances by 8 per character in `print`.

## `print` implementation

`print` is a Prog8 `sub` that loops over the string:
LF (byte 10) starts a new text row; other control bytes are treated as font
indices, with bit 7 selecting reverse video as described above.

```
sub print(uword x, uword y, str text, ubyte color) {
    uword plane_mask = color & ((1 << depth) - 1)
    long row_offset = math.mul32(y, pitch)
    uword cx = x
    ubyte i = 0
    while i < len(text) {
        ubyte chr = text[i]
        if chr == 10 {
            cx = x
            y = y + 8
            row_offset = math.mul32(y, pitch)
            i = i + 1
            continue
        }
        draw_glyph(cx, row_offset, chr, plane_mask)
        cx = cx + 8
        i = i + 1
    }
}
```

Most of the module is Prog8: `init`, `char`, and `print` handle configuration,
plane-mask calculation, string iteration, and row-offset reuse. The private
glyph-rendering inner loop is an optimized `asmsub` written in 68000-compatible
assembly. `print` computes the mask and row offset once, then reuses them for
each character on the same text row. Keep string handling in Prog8 unless
profiling later shows that it needs optimization.

## Assembly notes (68000/68020 compatible)

- Keep the implementation within the 68000 instruction set; avoid scaled
  indexing and other 68020-only addressing features. If indexed addressing is
  used, use the 68000 brief form with a word-sized index.
- The renderer needs byte `OR` for set planes and byte `AND` with the inverse
  glyph mask for cleared planes. Include both operations, plus shifts and
  pointer arithmetic, in the implementation plan.
- Use `math.mul32(y, pitch)` for the 32-bit row offset instead of a widened
  Prog8 multiplication, which currently emits the general 32-bit multiply
  helper on `amiga500`. `math.mul32` uses `MULU.W`, whose two 16-bit unsigned
  operands produce a 32-bit result. A `char` call computes this once; `print`
  computes it once per text row and reuses it for each glyph, recomputing only
  after newline.
- The eight-row loop can use `dbra`. Unrolling may reduce loop overhead but
  increases code size; measure on both CPUs rather than assuming it helps only
  the 68020.
- Visit every plane: planes selected by the color are OR-ed with glyph bits;
  other planes are AND-ed with the inverse mask to clear those pixels. This is
  required for exact colors, including color 0.
- Declare every modified register in the `asmsub` clobbers. The clobbers list
  does not save registers. Use scratch registers where possible; preserve any
  registers required by the calling convention, and never clobber A5 or Amiga
  A6.

## Clipping and bounds

No clipping is performed. The caller is responsible for ensuring that
every glyph fits in the bitmap; out-of-bounds writes are not guarded. This
keeps the inner loop minimal (no per-glyph bounds checks). Newline (optional)
resets `cx` and advances `y` by 8, and the caller must ensure those rows fit.

## Composability and statelessness

- No cursor or text-color state; `print`/`char` take explicit coordinates
  and palette indices.
- No OS calls; works after `custom.grab_system()`.
- CPU-only: does not touch blitter registers, so it composes with
  `blitter.wait()` boundaries (call `blitter.wait()` before text if a blit
  is still in flight, since blitter priority can stall CPU chip-RAM access
  on 68000).
- Does not modify palette or copper; the caller owns those.

## Fit with existing bare-metal helpers

No bare-metal helper requires a `^^graphics.BitMap`:

- `blitter.line_color` now takes `(pen, depth, ^^pointer planes)`.
- `text` takes pitch, depth and a raw plane table via `init`.

Existing blitter primitives use raw pointers; rectangle helpers take pitches,
while line drawing uses the pitch and fill pattern cached by `line_init`.
`line_color` takes a raw plane table. `copper` takes raw lists and plane
tables. `soft_blit` is only proposed here, not an existing primitive. A
bare-metal program that allocates its own bitplanes needs no `BitMap`.

- `custom`: `detect_chipset()` available but not required (text is
  CPU-only and chipset-independent). `grab_system()`/`restore_system()`
  unchanged. `cpuVBR` not needed.
- `playfield`: its `create()` helper uses OS allocation; bare-metal text does
  not depend on it and accepts raw plane pointers instead.
- `blitter`: not used by text (CPU renderer). `blitter.wait()` is the only
  interaction point, documented above.
- `copper`: not used.
- Memory: font lives in fast RAM; state is a handful of
  `@shared` words/pointers. No dynamic allocation, matching the primitive
  philosophy (stateless per call, caller owns buffers).
- No conflicts with `line_mod`/`line_pattern` (different BSS names).

## Testing (when implemented)

- Compile-check: `prog8c -target amiga1200 examples/amiga/<textdemo>.p8 -check`
  and `amiga500`.
- Assemble one example end-to-end to confirm the generated `.asm` is valid
  for both targets.
- Visual verification via vamos/FS-UAE or a real Amiga; no automated
  pixel comparison exists yet.
- Draw a glyph in color 0 over nonzero pixels: covered glyph pixels must
  become 0 while pixels in the glyph's transparent background remain intact.
- Verify that setting bit 7 on a character renders the same glyph in reverse
  video, including at non-byte-aligned X positions.
