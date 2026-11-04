# Amiga bare metal helper libraries

## Sprite Helper Library

Add a bare-metal `sprites` helper module for the `amiga500` and `amiga1200`
targets, using the registers exposed by `custom`.

### Plan

- Provide helpers to pack sprite position and control words.
- Provide helpers to set the hardware sprite pointers to sprite data in
  chip RAM.
- Support AGA's global `FMODE` sprite fetch-width setting (up to 64 pixels in
  low resolution), with the matching data/control-word layout; retain the
  standard 16-pixel sprite format on non-AGA hardware.
- Keep the module usable by demos and games after `custom.grab_system()`,
  without OS dependencies or NDK/generator changes.
- **Primitives only.** The helper provides low-level hardware operations
  (pack control words, set pointers, enable/disable in DMACON). It does NOT
  manage sprite state, animation, collision, or multiplexing. The main
  program owns sprite data in chip RAM, positions, and lifecycle. This keeps
  the library small, stateless, and composable — same philosophy as the blitter
  helpers (primitives only, resource handling left to the main program).

- **Composable, not constraining.** The primitives must not prevent or
  complicate higher-level usage. Specifically:
  - **Animation**: primitives operate on raw chip-RAM data pointers. The main
    program can swap the data pointer per frame (sprite sheet indexing) without
    any helper involvement. No internal caching or state that would stale.
  - **Multiplexing**: primitives are stateless per-call. The main program (or
    copper list) can call `sprite.pos()` / `sprite.init()` multiple times per
    frame for the same slot at different scanlines. The helper doesn't track
    "which slot is in use" or enforce a one-object-per-slot model.
  - **Palette management**: primitives don't touch palette entries beyond what
    the caller explicitly sets via BPLCON4. The main program owns the palette
    (COLOR00-COLOR255). The helper provides `set_even_bank`/`set_odd_bank`
    but never allocates or reserves palette ranges. Caller decides which entries
    to use for which bank.
  - **No hidden side effects**: primitives only write the registers they're
    named after. No global state, no "current sprite" pointer, no cached
    values that could go stale. Every call is independent and idempotent.

### Implementation phases

**Phase 1 — OCS-compatible sprites only (simple, ~50-100 lines)**

Start with just 16px, 4-color (2 bitplane) sprites. This is the common
denominator — works on every Amiga, no AGA detection, no FMODE/BPLCON4
changes needed:

- 2 data words per line (SPRxDATA, SPRxDATB) — standard OCS format
- No FMODE changes (leave at $0000)
- No BPLCON4 writes (palette entries 16-31, OCS default)
- No chipset detection
- No width bits (defaults to 16px)

Minimal API:
```
sprite.init(num, data_ptr)     ; set SPRxPT to data
sprite.pos(num, x, y)          ; set SPRxPOS
sprite.enable(num)             ; enable in DMACON
sprite.disable(num)            ; disable in DMACON
```

This covers 90% of what games need (HUDs, cursors, bullets, small objects).
Works everywhere without chipset detection.

**Phase 2 — AGA enhancements (additive, opt-in)**

Layer on AGA features only when `detect_chipset() == CHIPSET::AGA`. Each
feature is a separate opt-in function that a game calls only if needed:

- `sprite.aga_16color()` — set BPLCON4 banks, store 4 words per line
- `sprite.aga_wide(num, width)` — set SPRxCTL width bits (32/64px), store
  8/16 words per line, set FMODE bits 0-1

A game that only needs 16px 4-color sprites never touches BPLCON4 or FMODE.
Phase 1 code keeps working unchanged on AGA hardware (OCS compatibility mode).

**Why phased?** The full sprite module (all modes in one) is complex because
it must coordinate FMODE, control words, data layout, and palette banks
simultaneously. Splitting into phases keeps the basic API clean and the
complexity opt-in. Phase 1 is a thin register wrapper; Phase 2 adds AGA
features as separate functions.

### AGA vs OCS sprite modes

AGA is backward compatible with OCS. OCS programs run unmodified on AGA
because the hardware defaults to OCS compatibility mode (2 bitplanes, 4 colors,
16px wide) until AGA-specific registers are written. The switch to AGA sprite
mode happens when you write to registers that don't exist on OCS.

**OCS sprites (default/compatibility mode):**
- 2 bitplanes = 4 colors (color 0 = transparent)
- 16 pixels wide, fixed
- 2 data words per line (SPRxDATA, SPRxDATB)
- Colors from palette entries 16-31 (fixed bank)
- No attachment needed for 4 colors

**AGA sprites (enhanced mode):**
- 4 bitplanes = 16 colors (color 0 = transparent)
- 16, 32, or 64 pixels wide
- 4 data words per line (for 4 bitplanes)
- Colors from 256-color palette, bank selectable via BPLCON4
- Attached sprites give 256 colors (8 bitplanes) but cost 2 sprite slots

**Switching to AGA mode requires writing to AGA-only registers:**
- `BPLCON4` ($dff10c) — enables AGA sprite color banking; writing to it
  signals "this program knows about AGA"
- `SPRxCTL` width bits — bits in the sprite control word select 16/32/64px
- `FMODE` ($dff1fc) bits 0-1 — sprite DMA fetch mode (16/32/64-bit)

Without these writes, sprites stay in OCS compatibility mode (2 bitplanes, 4
colors, 16px). This is why OCS games work unmodified on AGA hardware.

**FMODE sprite DMA modes:**

| FMODE bits 0-1 | Fetch width | Supports |
|---|---|---|
| 00 | 16-bit | 16px sprites (OCS compatible) |
| 01 | 32-bit | 16px or 32px sprites |
| 10 | 64-bit | 16px, 32px, or 64px sprites |

FMODE controls DMA fetch width, NOT sprite width. The sprite control words
determine the actual displayed width. They must be set consistently — FMODE
too low for the sprite width causes garbled/partial sprites.

**Sprite data word layout:**

| Width | Bitplanes | Words per plane | Total words per line |
|---|---|---|---|
| 16px | 2 (OCS) | 1 | 2 (SPRxDATA, SPRxDATB) |
| 16px | 4 (AGA) | 1 | 4 (planes 0-3) |
| 32px | 4 (AGA) | 2 | 8 (planes 0-3, 2 words each) |
| 64px | 4 (AGA) | 4 | 16 (planes 0-3, 4 words each) |

For AGA 4-bitplane sprites, each line stores 4/8/16 words (planes 0-3,
interleaved by plane). Width is encoded in the SPRxCTL control word; the number
of data words stored must match. FMODE sprite DMA mode must be set high enough
to fetch all words per line within the available DMA time.

**Registers available in custom.p8 (shared_amiga_custom.p8):**

All of the following are already defined and usable from Prog8:

- `BPLCON4` ($dff10c) — sprite color banking + resolution (AGA)
- `BPLCON3` ($dff106) — palette control 3 (AGA, includes sprite resolution)
- `FMODE` ($dff1fc) — fetch mode (AGA, bits 0-1 = sprite DMA fetch width)
- `SPR0CTL`..`SPR7CTL` ($dff142, $dff14a, ...) — sprite control words
  (AGA width bits are in the upper bits of the control word)
- `SPR0POS`..`SPR7POS` — sprite position
- `SPR0DATA`/`SPR0DATB`..`SPR7DATA`/`SPR7DATB` — sprite data A/B
- `SPR0PTH`/`SPR0PTL`.. — sprite data pointers (32-bit)
- `detect_chipset()` — returns CHIPSET::AGA on AGA hardware
- `grab_system()` / `restore_system()` — system takeover/restore

The sprite helper should check `detect_chipset() == CHIPSET::AGA` before
enabling AGA-specific features (wider sprites, 16 colors, BPLCON4 banking).
On non-AGA hardware, fall back to 16px / 4-color / 2-bitplane sprites.

### AGA sprite palette banks

AGA has 256 palette entries (0-255), divided into 16 banks of 16 colors each:

| Bank | Palette entries |
|------|----------------|
| 0 | 0-15 |
| 1 | 16-31 |
| ... | ... |
| 15 | 240-255 |

A 16-color sprite uses one bank (16 consecutive palette entries). The sprite's
4-bit pixel value (0-15) is the offset within its bank. Color 0 of each bank is
transparent.

**BPLCON4 bank selection** — two 4-bit fields, one for even sprites and one
for odd sprites:

| BPLCON4 bits | Controls | Sprites |
|---|---|---|
| 0-3 | Even sprite bank | SPR0, SPR2, SPR4, SPR6 |
| 4-7 | Odd sprite bank | SPR1, SPR3, SPR5, SPR7 |

At any given time, all even sprites share one 16-color bank and all odd
sprites share a (possibly different) 16-color bank. You cannot give each of
the 8 sprites an independent bank — they are grouped into even/odd pairs.

**Attached sprites** (even+odd combined) use both banks simultaneously for
256 colors: the even bank provides the low 4 bits, the odd bank the high 4
bits. This costs 2 sprite slots and yields one 256-color sprite.

**Dynamic bank changes**: BPLCON4 can be changed via copper between scanlines,
so different sprites can use different color ranges at different times. But on
any single scanline, the even/odd bank pairing applies.

**Implications for the sprite helper API:**
- `sprite.color_bank(sprite_num, bank)` is not possible per-sprite — bank is
  per even/odd group. The API should expose `sprite.set_even_bank(bank)` and
  `sprite.set_odd_bank(bank)` instead.
- For games needing more than 2 distinct 16-color palettes simultaneously,
  use attached sprites (256 colors) or copper-driven bank switches.
- The helper should document which palette entries each bank consumes so the
  caller can allocate palette ranges without conflicts.

### References

- [AGA sprite documentation](https://jvaltane.kapsi.fi/amiga/howtocode/aga.html#sprites)
- [Anguish AGA walkthrough](https://www.youtube.com/watch?v=mRZfwJAkkIA&t=62s), showing 64-pixel-wide sprites

## IRQ handler support (umbrella)

The highest-value missing piece: cx16-style IRQ helpers reshaped for the
68000/Amiga model. Both entries below converge on it.

- `custom.set_handler(level, sub)` / `custom.restore_handler(level)` plus
  Paula source-bit enable/disable (`INTENA`/`INTREQ` discipline). A straight
  copy of `sys.set_rasterirq` doesn't map: eight autovector levels carry
  multiplexed Amiga sources (VERTB and COPER share level 3, CIA-A/B sit on
  2/6).
- **VBR-aware**: the handler mechanism must use `custom.cpuVBR` as the vector
  base address (not hardcode $000000). On 68000, VBR is always 0 (no VBR
  register). On 68010+ (68020/68030/68040/68060), VBR can be relocated.
  `custom.cpuVBR` is already populated by `grab_system()` (or equivalent)
  via supervisor-mode `MOVEC VBR,d0` on capable CPUs, defaulting to 0 on
  68000. All vector reads/writes go through `custom.cpuVBR + level*4`.
- Likely needs codegen work, not just library: handler subs need
  save/restore + `rte` frames, which the m68k backend may not emit today.
- **Do NOT modify `grab_system`/`restore_system`.** Instead, add a separate
  handler registry: `set_handler` records the installed handler (level + sub)
  in a small internal table (8 slots, one per autovector level). `restore_system`
  iterates this table and calls `restore_handler` for each entry before
  restoring the original vectors. This keeps `grab_system`/`restore_system`
  unchanged and the IRQ mechanism fully opt-in.

  **Why a separate registry is simpler than modifying `grab_system`:**
  - `grab_system` runs at startup before user code — it saves original vectors
    but cannot know which handlers will be installed later.
  - The 68000 has 7 autovector levels (1-7), but only a handful are used on
    Amiga (2, 3, 4, 6). A fixed 8-slot table is tiny and covers all cases.
  - Modifying `grab_system` to allocate and manage a handler table would
    couple IRQ tracking to system takeover, making it harder to test and
    increasing the risk of breaking existing `grab_system` behavior.
  - The separate registry is a pure addition — programs that never call
    `set_handler` are completely unaffected.
- `restore_handler(level)` can also be called manually for early teardown.
- VERTB and CIA timers below become thin consumers of this API.

## VERTB IRQ handler

No bare-metal vertical-blank example exists (`lowlevel-timer.p8` is OS-based;
m68k targets have no `sys.set_irq` helpers, see `targetsystem.rst`).

- Install an autovector level-3 handler via `custom.cpuVBR` after
  `custom.grab_system()`, enable `VERTB` in `INTENA`, acknowledge via
  `INTREQ` every field, return with `rte`. Restoration is handled by the
  separate handler registry (see section 2) plus `restore_system` — no
  changes to `grab_system` needed.
- Keep the handler minimal (set a frame flag, like the timer example);
  the main loop does the work.
- `playfield.waitvsync` stays the polling fallback; document when the IRQ
  version pays off (frame time too tight to spin on the beam).

## CIA timer IRQ helpers

Same gap one layer down: `custom` exposes the CIA registers
(`CIAA_TALO/TAHI/CRA/ICR`, same for CIA-B) so hand-rolled setup is possible,
and `lowlevel-timer.p8` covers the OS path via `lowlevel.library`, but no
bare-metal helper or example exists.

- Load latch (TALO/TAHI), force-load and start mode via CRA/CRB, enable via
  ICR with bit 7 set.
- Route to CPU levels 2 (CIA-A) / 6 (CIA-B) through `INTENA` PORTS/EXTER,
  install the handler via `custom.cpuVBR`, acknowledge by *reading* ICR,
  return with `rte`, restore on exit.
- Keep the handler minimal (tick counter / frame flag), like the VERTB one.

### TOD (Time of Day) clock support

Both CIAs have a 24-bit BCD TOD clock (hours:minutes:seconds.tenths) driven
by the power line frequency (50 Hz PAL / 60 Hz NTSC, divided to 10 Hz).
Registers: `CIAA_TODHI/MID/LO` ($bfe801/$bfe901/$bfea01) and
`CIAB_TODHI/MID/LO` ($bfd801/$bfd901/$bfda01).

Helpers to provide:
- `cia.tod_read(cia) -> (hours, minutes, seconds, tenths)` — read all 3 bytes
  with latch (read TODHI first to latch, then MID, then LO). Returns BCD
  values; caller converts to binary if needed.
- `cia.tod_write(cia, h, m, s, t)` — set the time. Write TODHI first (stops
  clock), then MID, then TODLO (restarts clock).
- `cia.tod_alarm(cia, h, m, s, t)` — set the alarm register. When TOD matches,
  triggers an interrupt on the CIA's CPU level (level 2 for CIA-A, level 6 for
  CIA-B).
- `cia.tod_alarm_enable(cia, bool)` — enable/disable the alarm interrupt via
  ICR (bit 4 = TOD alarm).

Notes:
- TOD counts up (increments every 0.1s), rolls over at 23:59:59.9.
- Accuracy is power-line dependent (±0.1%), not crystal. Good for game timing,
  not for precision measurement.
- No date storage — only time-of-day. Date is maintained in software.
- CIA-A TOD is typically used by the OS for the system clock. After
  `grab_system()`, it's free to use. CIA-B TOD is often used for disk timing.
- Reading TODHI latches all three bytes for a consistent snapshot. Not reading
  TODHI first gives inconsistent (torn) reads.
- Writing TODHI stops the clock; writing TODLO restarts it. The set sequence
  must be HI → MID → LO.

## BOB helpers (extensions to shared_amiga_blitter.p8)

`shared_amiga_blitter.p8` IS the blitter library. These are additions to it,
not a separate module.

- **Soft-scroll BOB blit — needed (decision: implement).** `masked_blit` is
  word-aligned only, which forces BOBs to 16-pixel boundaries and looks jerky
  for bullets, scrolling sprites and smooth motion. The Amiga blitter's
  ASHIFT/BSHIFT bits (BLTCON0 bits 12-15 / 4-7) plus BLTAFWM/BLTALWM do
  exactly this in hardware, so a primitive exposing them is warranted.
  Proposed routine:
  ```
  blitter.soft_blit(pointer src @A0, pointer mask @A1, pointer dst @A2,
                    uword width_words @D0, uword height @D1,
                    uword src_mod @D2, uword dst_mod @D3,
                    ubyte shift @D4, uword minterm @D5)
  ```
  - `src`/`mask`/`dst` are chip-RAM pointers. `mask == 0` disables the mask
    channel (USEB=0) for solid copies/erases; otherwise USEA+USEB+USEC+USED.
  - `width_words` is the BOB width in *destination* words (W). `shift` is
    `x & 15` (0-15). For `shift != 0` the source must be **W+1 words wide**
    per line (caller pads each line with one zero word); for `shift == 0`
    the source is W words wide.
  - BLTCON0 = `(shift << 12) | (shift << 4) | USEA | (USEB if mask) | USEC | USED | minterm`
    (BSHIFT follows ASHIFT so the mask scrolls with the data).
  - `shift == 0`: BLTAFWM/BLTALWM = $ffff, A/B modulo = pitch - W*2.
  - `shift != 0`: BLTAFWM = `$ffff >> shift`, BLTALWM = `($ffff << (16-shift)) & $ffff`,
    A/B modulo = pitch - (W+1)*2, C/D modulo = pitch - W*2.
  - BLTSIZE width = W, height = height. BLTCON1 = $0800 (B32 AGA mode).
  - Waits for blitter idle at entry; returns immediately after triggering.
  - Hard limit: `width_words <= 63` when `shift != 0` (source fits the
    64-word blitter max: W+1 <= 64). For `shift == 0`, `width_words <= 64`.
  - Caller computes the destination pointer (`bitmap + y*pitch + (x>>4)*2`)
    and `shift = x & 15`; the routine stays a pure DMA primitive with no
    position math, no state, no allocation — composable with the other blitter
    helpers (use `copy_rect`/`blit_copy` to save+restore the background for
    erase; `soft_blit` with `MINTERM_A_ONLY` and no mask draws a solid BOB).
  - `masked_blit` remains the simpler, cheaper path when the caller already
    knows the BOB is word-aligned (shift == 0); `soft_blit` generalises it.

### BOB animation lifecycle (why soft_blit alone is not enough)

A *moving* BOB needs three primitives per frame, in this order:

1. **Erase** the BOB at its old position — restore the saved background:
   `copy_rect(savebuf, dst, W, H, 0, dst_mod, MINTERM_A_ONLY)` (or
   `soft_blit(savebuf, 0, dst, W, H, 0, dst_mod, old_shift, MINTERM_A_ONLY)`
   if the BOB was soft-scrolled and the save buffer is W+1 words wide).
2. **Save** the background at the new position into the save buffer:
   `copy_rect(dst_new, savebuf, W, H, dst_mod, 0, MINTERM_A_ONLY)` (W+1 words
   for soft-scroll).
3. **Draw** the BOB at the new position:
   `soft_blit(bob, mask, dst_new, W, H, src_mod, dst_mod, shift, MINTERM_MASKED)`.

The save buffer must be BOB-sized (W words, or W+1 for soft-scroll) and live
in chip RAM. With multiple BOBs, erase in reverse draw order (back to front)
to avoid one BOB erasing another's pixels; the main program owns the save
buffers and the old/new position state — the helpers stay stateless.

This is why the library deliberately stops at primitives: a "move_bob" helper
would have to own save buffers, positions, and frame ordering, which is
game-specific. The three-primitive sequence above is the standard Amiga BOB
pattern and composes cleanly with the existing `copy_rect`/`soft_blit`.

- do we need a routine to clear the BOB as well or can we use masked blit for that too efficiently maybe? -> use `fill_rect` with pattern $0000 for a region, or `copy_rect` (save/restore background) for erase; no dedicated clear-BOB routine.
- what else is missing to easily handle BOBS? -> see the soft_blit design above; otherwise the existing primitives (copy_rect, masked_blit, clear_plane, fill_rect) cover save/restore, draw and erase. Collision detection stays CPU/hardware (CLXCON/CLXDAT), not a blitter job.
- `blitter.wait()` already exists in `shared_amiga_blitter.p8` (busy-waits on
  DMACONR bit 6). No new helper needed — just reuse/alias it.

## Filled polygon support (blitter line/polygon helpers, not BOB helpers)

The Amiga blitter has a **hardware area-fill mode** (BLTCON1 FILL bit). When
enabled, the blitter scans each scanline left-to-right, toggling a fill state
when it encounters a set bit in the source. While fill state is on, it writes
the fill pattern to the destination. This produces a filled polygon from an
outline in a single blit pass.

**No-seams strategy (exclusive endpoints):**

The core problem with polygon filling is shared vertices. When two edges meet
at a vertex, that pixel gets drawn by both lines. This causes the fill toggle
to fire at the wrong position, creating thin gaps or overlaps ("seams")
between adjacent filled polygons.

The solution is **exclusive endpoints**: every line draws its first pixel but
skips its last. This ensures shared vertices are drawn exactly once. The
blitter's SING bit controls this:
- SING=0: skip the last pixel (exclusive)
- SING=1: draw the last pixel (inclusive)

For fill mode, the convention is inverted from normal line drawing:
- **Lines going down** (dy >= 0): SING=1 (inclusive — draw last pixel)
- **Lines going up** (dy < 0): SING=0 (exclusive — skip last pixel)

This way, at a shared vertex between a downward edge and an upward edge, only
the downward edge draws the vertex. Each scanline ends up with an even number
of set pixels (left/right edge pairs), and the fill toggles correctly.

When two polygons share an edge, polygon A draws it with SING=1 on downward
lines and polygon B draws the same edge with SING=0 on upward lines. The shared
pixels are drawn exactly once — no overlapping, no gaps.

**Two-part requirement:**

1. **Outline drawing in fill-compatible mode.** The polygon edges must be
   drawn with specific endpoint handling so each scanline has an even number
   of set pixels (left/right edge pairs). The blitter's line mode has an
   `SING` (single) bit in BLTCON1 that controls whether the first/last pixel
   of each line is drawn.

   The existing `line_draw` sets SING based on line direction:
   - dy >= 0 (going down): SING=0 (exclusive — skip last pixel)
   - dy < 0 (going up): SING=1 (inclusive — draw last pixel)

   For fill mode, this must be **inverted**:
   - dy >= 0 (going down): SING=1 (inclusive — draw last pixel)
   - dy < 0 (going up): SING=0 (exclusive — skip last pixel)

   This ensures shared vertices are drawn exactly once: when two edges meet
   at a vertex, the downward edge draws it and the upward edge skips it.

   **Implementation:** The change is 3 lines in the octant computation. The
   current code does `addq.b #8,d6` when dy < 0 (selecting SING=1 entries
   in OctTbl). For fill mode, move this to the dy >= 0 branch instead:

   ```
   ; Current (normal lines):
   sub.w   d1,d3                ; d3 = y2 - y1
   bpl.s   .dyplus              ; if dy >= 0
   neg.w   d3                   ; dy < 0
   addq.b  #8,d6                ; SING=1 for upward lines
   .dyplus:

   ; Fill mode (inverted):
   sub.w   d1,d3                ; d3 = y2 - y1
   bmi.s   .dyneg               ; if dy < 0
   addq.b  #8,d6                ; SING=1 for downward lines
   bra.s   .dydone
   .dyneg:
   neg.w   d3                   ; dy < 0
   .dydone:
   ```

   All other octant adjustments (dx sign, slope, sign bit) remain identical.
   The OctTbl already has both SING variants (16 entries = 8 octants x 2).

   A separate `line_draw_fill` function is cleanest — it's identical to
   `line_draw` except for these 3 lines. Alternatively, a `fill_mode` flag
   stored by `line_init` could select between them at runtime, but a separate
   function avoids the branch overhead.

2. **Scanline fill blit.** After the outline is drawn, a single blit with
   FILL enabled fills all scanlines between the leftmost and rightmost set
   pixels. This is a read-modify-write: source = destination = the bitplane.

   Registers:
   - BLTCON0: USEA=1, USEC=1, USED=1, minterm=$CA (masked)
   - BLTCON1: FILL=1 (bit 4)
   - BLTAFWM/BLTALWM: $ffff
   - BLTBDAT: pattern (default $ffff = solid; $aaaa = 50% dither, etc.)
   - BLTAMOD/BLTCMOD/BLTDMOD: mod
   - BLTAPT/BLTCPT/BLTDPT: src (same as dst for read-modify-write)
   - BLTSIZE: w x h

   The fill pattern comes from BLTBDAT. Set to $ffff for solid fill, $aaaa
   for 50% checkerboard dither, $cccc for 25% dots, etc. The pattern is
   applied per-word during the fill, aligned to word boundaries (affected by
   BLTCON0 shift bits). No extra passes needed — stippled fills are free.

   Proposed function signature:
   ```
   blitter.area_fill(src, w, h, mod, pattern)     ; pattern in BLTBDAT
   ```
   Always pass a pattern. Use $ffff for solid fills, $aaaa for 50% dither,
   $cccc for 25% dots, etc. No optional parameter — simpler API, caller always
   specifies intent.

   **Naming (decided):** keep `fill_rect` / `fill_plane` unchanged and name
   the new routine `area_fill`. Rationale: the existing names are already in
   use, and "area fill" is the Amiga hardware term for the BLTCON1 FILL mode,
   so the odd one out is named for what it actually is. The distinction is
   documented instead of encoded in a rename: `fill_rect`/`fill_plane` write a
   repeating 16-bit pattern (pattern fill), while `area_fill` toggles a fill
   state on set source bits and fills between the edges (hardware area fill).

**Standard filled polygon workflow:**
1. Clear the target bitplane region (`clear_plane`)
2. Draw polygon outline with fill-compatible lines (`line_draw_fill`)
3. Call `area_fill` to fill all scanlines between edges
4. Result: filled polygon in the bitplane

**Limitations:**
- The outline must be a closed polygon with no self-intersections
- Lines must be drawn with correct endpoint handling (inclusive/exclusive)
- The fill is horizontal (scanline-based) — works for any polygon but the
  outline quality affects fill correctness
- Only fills one bitplane at a time (call per plane for multi-plane BOBs)

**Note:** This is a well-known Amiga technique. The blitter's fill mode is
specifically designed for this purpose. The main work is getting the line
endpoint handling right; the fill blit itself is a single blitter operation.

- **Composable, not constraining.** Same philosophy as the sprite helpers: the
  BOB primitives must not prevent or complicate higher-level usage. Specifically:
  - **Animation**: primitives operate on raw chip-RAM data pointers. The main
    program can swap the source pointer per frame (sprite sheet indexing) without
    any helper involvement. No internal caching or state.
  - **Multiplexing**: primitives are stateless per-call. The main program can
    blit the same BOB at different positions each frame without helper tracking.
  - **Palette management**: primitives don't touch palette entries. The main
    program owns the palette. The blitter only moves pixel data; color mapping
    is implicit via the shared palette.
  - **No hidden side effects**: primitives only write the blitter registers
    they're named after. Every call is independent and idempotent.

## Text output library

Need a simple library to quickly write text onto a bare-metal bitmap screen.

Conclusions:

- Font: embed, don't extract. Topaz 8 lives in Kickstart ROM but its address
  varies per ROM version, and reading it needs the OS (diskfont) anyway, which
  breaks the no-OS rule. Embed `font8x8_basic` (Daniel Hepper, public domain,
  128 chars x 8 bytes = 1 KB) as a chip-RAM data block instead. KS-independent
  and valid after `custom.grab_system()`.
- Renderer: CPU, not blitter. HUD quantities (a few dozen glyphs per frame)
  are faster with plain read-OR-write loops: no DMA setup, no waits, no chip
  contention management. The blitter only wins for bulk text (full-screen
  consoles, scrolling logs) and multi-plane opaque fills.
- Arbitrary X position, with a byte-aligned fast path. For shift = x & 7,
  each glyph row splits across two destination bytes
  (`glyph >> shift`, `glyph << (8 - shift)`); shift == 0 skips the second
  byte. Right-edge rows need a bounds check on the +1 byte.
- **Word-combined writes (bandwidth optimization).** Two adjacent glyphs can
  be merged into a single 16-bit word (`glyph0 << 8 | glyph1`, with the
  appropriate shift applied) and written to chip RAM in one `move.w`,
  halving the number of chip-RAM writes versus per-byte stores. This pays
  off most for byte-aligned (shift == 0) runs; at non-zero shifts the merge
  requires shifting both glyphs and OR-ing before the store, but still saves
  a write. The foreground plane set is fixed by `text.color(fg)` (a single
  color for the whole run), so no per-glyph plane check is needed — just
  write that fixed set of planes with the combined word. 0 bits never clear
  existing pixels under OR semantics. Edge glyphs at the right margin fall
  back to the per-byte path.
- Transparent only: 0 pixels never touch the destination (OR semantics), so
  only planes with the foreground bit set are visited (cost scales with
  popcount of the color). Erasing/redrawing backdrops stays the caller's job
  (save buffer, blitter re-fill, or full redraw as in `3d`), like the other
  blitter primitives the helper stays write-only.
- Minimal API: `text.init(bitmap)`, `text.color(fg)`,
  `text.print(x, y, str)` / `text.char(x, y, ch)`. No separate
  `locate` — position is passed to each print call, keeping the API
  stateless and avoiding cursor-state bugs. Coordinates first, matching
  Prog8's graphics convention (`plot(x, y, color)`, `line(x1, y1, x2, y2, color)`).
  Replaces the hand-stamped message bitmap in `3d` with per-glyph blits.
