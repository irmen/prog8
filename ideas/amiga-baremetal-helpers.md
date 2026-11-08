# Amiga bare metal helper libraries

### CPU scope

Only **classic M680x0 CPUs** (68000, 68010, 68020, 68030, 68040, 68060) are considered.
**Dragonball and ColdFire variants are fully ignored** — their instruction sets,
exception frames, and supervisor/user privilege rules are not part of this design.
This matches the `m68k-coder` skill scope: write/understand classic M680x0 assembly
in vasm mot syntax only; do not target or reference Dragonball/ColdFire.

- **Never use `CALLM` or `RTM`.** Both exist **only** on the MC68020; they are
  removed on every later classic CPU (68030/68040/68060) and on ColdFire, where
  executing them takes an unimplemented-instruction exception. They are the
  module call/return pair tied to the MC68851 access-control model Motorola
  dropped once the on-chip MMU replaced it. Use a normal `jsr`/`rts` subroutine
  call instead.

## Shared object metadata

Keep movable-object metadata separate from renderer-specific image data. `Mob`
lives in the lightweight `amiga_objects` types module imported by `sprites` and
`blitter`; it contains no bitmap pointers and requires no OS libraries.

```prog8
struct Mob {
    uword id
    word x_pos
    word y_pos
    word origin_x
    word origin_y
    uword width
    uword height
    uword state
}

```

`x_pos`/`y_pos` are the screen coordinates of the image's top-left; the
client-selected anchor is at `(x_pos + origin_x, y_pos + origin_y)`. Width is
the exact pixel width and height is in rows. `Mob.state` is a client-defined
state identifier, independent of rendering. Client code owns animation timing
and frame selection; image/frame pointers stay in each renderer-specific object.

## Sprite Helper Library

Add a bare-metal `sprites` helper module for the `amiga500` and `amiga1200`
targets, using the registers exposed by `custom`.

The sprite descriptor will reference the shared `Mob`; sprite-specific payload
fields are deferred until the sprite implementation.

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

These helpers are for programs that have taken over the machine with
`custom.grab_system()`, not for OS-friendly interrupt installation. Programs
that leave AmigaOS running should use `AddIntServer()`/`RemIntServer()` for
shared Exec interrupt chains, `AddICRVector()`/`RemICRVector()` for CIA sources,
and `timer.device` for ordinary timing. Exec calls these routines as
subroutines, so they return with `rts`; the bare-metal vector trampoline returns
with `rte`.

- `custom.set_handler(level, sub)` / `custom.restore_handler(level)` plus
  Paula source-bit enable/disable (`INTENA`/`INTREQ` discipline). A straight
  copy of `sys.set_rasterirq` doesn't map: seven autovector levels (1-7) carry
  multiplexed Amiga sources (VERTB and COPER share level 3, CIA-A/B sit on
  2/6, and level 7 is NMI and not maskable through `INTENA`).
- **VBR-aware**: the handler mechanism must use `custom.cpuVBR` as the vector
  base address (not hardcode $000000). On 68000, VBR is always 0 (no VBR
  register). On 68010+ (68020/68030/68040/68060), VBR can be relocated.
  `custom.cpuVBR` is already populated by `grab_system()` (or equivalent)
  via supervisor-mode `MOVEC VBR,d0` on capable CPUs, defaulting to 0 on
  68000. Autovector level N lives at `custom.cpuVBR + (24+N)*4`, i.e.
  `custom.cpuVBR + $60 + N*4`; the existing `custom.IRQ1`..`custom.IRQ7`
  constants hold exactly those offsets ($64..$7C). `cpuVBR + N*4` would land
  on the reset/exception vectors instead. Call `set_handler` only after
  `grab_system()`, so that `cpuVBR` is populated and the vectors it saves are
  the OS ones.
- **No codegen work needed.** An `asmsub` trampoline supplies the frame: save
  `D0-D7/A0-A6`, derive the level from `SR` (valid because exception entry
  runs in supervisor mode with `IPL = level`), `jsr` the handler, restore the
  registers and return with `rte`. The handler itself is an ordinary Prog8
  `sub` with no parameters and no return value, returning with a normal `rts`.
  `rte` is accepted as an asmsub terminator (alongside `rts`/`rti`), so the
  trampoline needs no `; !notreached!` marker.
- **Do not use floating point in handlers.** The supplied trampoline preserves
  all integer registers (`D0-D7/A0-A6`) but does not save FPU state. If a handler
  must use the FPU, it needs a dedicated wrapper that saves and restores the
  complete state for the installed FPU: `FP0-FP7`, `FPCR`, `FPSR`, `FPIAR`, and
  any implementation-specific internal state. Keep handlers and routines they
  call integer-only whenever possible.
- **Leave `grab_system` unchanged; add an additive teardown hook to
  `restore_system`.** `grab_system` must not manage handlers: it runs at
  startup, before any handler exists, and it only saves the IRQ1/IRQ3/IRQ4
  vectors anyway. Keep a separate registry (8 slots, one per autovector
  level) holding the handler address plus the vector that was in place when
  `set_handler` ran. `restore_handler(level)` puts that vector back.
  `restore_system()` gains one trailing loop that calls `restore_handler` for
  every active slot **after** restoring its own saved IRQ1/3/4 vectors. That
  ordering is what makes teardown safe: it also repairs the case where a
  handler was installed before `grab_system` (which would otherwise have
  saved the trampoline as the "original"). The loop is a no-op for programs
  that never call `set_handler`. Levels 2, 5, 6 and 7 have no saved vector in
  `grab_system`, so the registry's captured original is the only way to
  restore those.

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
  `custom.grab_system()`, enable `VERTB` in `INTENA` (including the master
  `INTEN` bit, which `grab_system` cleared), acknowledge each vblank by
  writing the VERTB bit to `INTREQ` (a write of 1 clears it), return with
  `rte`. Restoration is handled by the separate handler registry plus the
  trailing loop in `restore_system`; `grab_system` stays unchanged.
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
Registers (named as in ``custom``): `CIAA_TODLOW/MID/HI` ($bfe801/$bfe901/$bfea01)
and `CIAB_TODLOW/MID/HI` ($bfd800/$bfd900/$bfda00). CIA-A sits on the odd byte
lane, CIA-B on the even one, hence the different low address bit.

Helpers to provide:
- `cia.tod_read(cia) -> (hours, minutes, seconds, tenths)` — read all 3 bytes
  with latch (read TODHI first to latch, then TODMID, then TODLOW). Returns BCD
  values; caller converts to binary if needed.
- `cia.tod_write(cia, h, m, s, t)` — set the time. Write TODHI first (stops
  clock), then TODMID, then TODLOW (restarts clock).
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
- Writing TODHI stops the clock; writing TODLOW restarts it. The set sequence
  must be HI -> MID -> LOW.

## BOB helpers (extensions to shared_amiga_blitter.p8)

`shared_amiga_blitter.p8` IS the blitter library. These are additions to it,
not a separate module.

### BOB data descriptors

Use Prog8 `snake_case` names, following the Amiga `graphics.BitMap` concepts.
All BOB data is uncompressed, planar, and in CHIP RAM. Within each frame, store
each color plane contiguously in plane-major order. The mask, when present, is
stored separately from the color data, matching the Amiga `ImageData` and
`ImageShadow` convention. Plane 0 is the least-significant bit of the palette
index. The descriptor itself need not be in CHIP RAM.

Rows use one fixed format, so the descriptor does not store row or frame
strides. `width` preserves the exact image width in pixels. Derive
`width_words = ceil(width / 16)` for blitter operations, matching the Amiga BOB
convention. `row_words = width_words + 1`; the extra final word of
every color and mask row is a zero guard word for soft blits. Thus
`row_bytes = row_words * 2` and `plane_bytes = height * row_bytes`. The mask
uses the same row format as the color planes; its bits can mark unused pixels
in the final image word as transparent. Rows are left-justified: the leftmost
pixel is bit 15 of the first word; unused low bits in a partial final word are
zero, followed by the guard word.

`Bob` references the shared `Mob` and stores BOB-specific image metadata and
pointers:

```prog8
struct Bob {
    ^^amiga_objects.Mob mob
    uword depth
    pointer data
    pointer mask
}
```

`mob` is required. `data` points to plane 0 of the currently selected frame, and
`mask` points to that frame's separate mask or is null for an opaque BOB. Color
plane `p` starts at `data + p * plane_bytes`. Client code owns animation timing
and frame selection, updating `data` and `mask` before drawing. The multi-plane
BOB draw helper accepts a full `^^Bob` so it can read both the Mob metadata and
the image pointers; it does not mutate either.

Sprite sheets are input assets, not a runtime BOB layout. Extracting and
repacking their cells into the required plane-major frames is the responsibility
of the client or its asset converter; the blitter library does not convert them.

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
enabled, each scanline is processed right-to-left starting from the fill
carry-in state, toggling the fill state on every set bit; while fill state
is on, ones are written. This produces a filled polygon from an outline in
a single blit pass. Fill mode only works in descending mode (HRM: "Area fill
only works correctly in descending mode").

**Endpoint handling:**

Fill-compatible edges use XOR, not OR, so overlapping edge pixels cancel.
Normalize every edge to run downward and XOR the first point before starting
the blit. The blitter then draws a half-open edge: the top endpoint is omitted
and the bottom endpoint is included. A through-vertex is counted once; extrema
are excluded by the paired toggles. Horizontal edges are not scanline
crossings and are skipped in the fill mask.

**Two-part requirement:**

1. **Outline drawing in fill-compatible mode.** The polygon edges must be
   drawn so each scanline holds exactly one pixel per edge crossing. Use the
   XOR minterm, normalize edges downward, omit each edge's first pixel, and
   skip horizontal edges. The blitter's line mode has a `SING` bit (BLTCON1
   bit 1: single bit per horizontal row, for use with subsequent area fill).
   The `OctTbl` pairs
   (`x`, `x+64`) are *not* SING variants — the +64 selects SIGNFLAG, which
   is derived per line from the sign of 2*S-L and stays identical in both
   modes. Fill-compatible lines use the exact same octant computation as
   normal lines, plus one instruction setting SING after the lookup:

   ```
   lea     OctTbl(pc),a0
   move.b  (a0,d6.w),d6         ; look up BLTCON bits for octant
   ori.w   #$0002,d6            ; SING: one pixel per row for area-fill outlines
   ```

   In the library this is `line_draw(..., fill=true)`. Draw the complete
   visible outline again with `fill=false` after `area_fill` so horizontal
   spans and omitted endpoints are visible without changing fill parity.

2. **Scanline fill blit.** After the outline is drawn, a single blit with
   FILL enabled fills all scanlines between the leftmost and rightmost set
   pixels. This is a read-modify-write: source = destination = the bitplane.

   Registers:
   - BLTCON0: USEA=1, USED=1, minterm=$F0 (D=A, solid copy)
   - BLTCON1: DESC (bit 1) | FILL_OR/FILL_XOR (bit 3/4) | B32 (bit 11)
   - BLTAFWM/BLTALWM: $ffff
   - BLTAPT/BLTDPT: last fetched word, (h-1)*(w+m)+(w-1) words from src
   - BLTAMOD/BLTDMOD: -mod (subtracted in descending mode)
   - BLTSIZE: w x h

   Single-blit fills are solid-only: the fill wraps the logic output, so a
   pattern in the logic would break the edge-toggle pairing. (Stippled fills
   need a second pass and a pattern source in chip RAM.)

   Implemented signature:
   ```
   blitter.area_fill(src, w, h, mod)
   ```

   **Naming (decided):** `fill_rect` writes a repeating 16-bit pattern
   (pattern fill; `clear_plane`/`fill_plane` were folded into it), while
   `area_fill` toggles a fill state on set source bits and fills between
   the edges (hardware area fill).

**Standard filled polygon workflow:**
1. Clear the target bitplane region (`fill_rect(..., 0, 0)`)
2. Draw fill-compatible edges (`line_draw(..., fill=true)`)
3. Call `area_fill` to fill all scanlines between edges
4. Redraw the complete visible outline (`line_draw(..., fill=false)`)
5. Result: filled polygon in the bitplane

**Limitations:**
- The outline must be a closed polygon with no self-intersections
- Each scanline must have an even number of fill-boundary crossings; an odd
  count leaks fill to the row end. Horizontal edges are omitted from the fill
  mask and restored by the outline pass.
- Only fills one bitplane at a time (call per plane for multi-plane BOBs)

**Note:** This is a well-known Amiga technique. The blitter's fill mode is
specifically designed for this purpose. The fill blit itself is a single
blitter operation; the line pass must preserve crossing parity.

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
