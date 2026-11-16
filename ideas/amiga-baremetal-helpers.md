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

Implemented: the `mobs` module (`amiga500`/`amiga1200`, `%import mobs`) holds
`Mob`, `Bob`, `Sprite` and the integer-only geometry helpers. See the library
reference for the struct layouts and routine list.

## Remaining Sprite Enhancements

The OCS/ECS 16-pixel, 4-color sprite helpers are implemented in
`compiler/res/prog8lib/shared_amiga_sprites.p8`. Attachment is implemented too
(`attach()`, `init_attached()`, `pos_attached()`, `set_colors16()`). The
remaining sprite work is the AGA-specific modes: wide (32/64px) sprites and the
`FMODE` fetch-width setting, to be exposed as `aga_`-prefixed routines in the
same module.

### Sprite attachment (OCS/ECS and AGA)

Sprite attachment combines two sprite channels (an even sprite plus the following
odd one) into a single sprite with double the color depth:

- OCS/ECS: attached pair = 4 bitplanes = 16 colors (vs. 2 bitplanes / 4 colors
  for a single sprite). Costs two of the eight sprite slots.
- AGA: attached pair = 8 bitplanes = 256 colors.
- Enabled by setting the attach bit in the odd sprite's `SPRxCTL` control word;
  the odd sprite's control word still carries the position/size data.
- Data layout (OCS/ECS 16-color case): each channel keeps its own 2-plane data
  (2 words per line at 16px); the attached pair together contributes 4 words
  per line (4 planes). AGA 256-color attachment uses 4-plane channels instead.
  Each channel's data structure has its own POS/CTL header and must be kept at
  the same screen position or attachment stops working.

Implemented as an opt-in: `attach(odd, on)` sets the bit, and
`init_attached()` / `pos_attached()` handle the even+odd pair. Attachment works
on all Amiga chipsets, including original OCS, so it is not AGA-only. The
caller pays the two-slot cost and provides one data block per channel.

### AGA vs OCS sprite modes

AGA is backward compatible with OCS. OCS programs run unmodified on AGA
because the hardware defaults to OCS compatibility mode (2 bitplanes, 4 colors,
16px wide) until an AGA-only register is written. The switch to AGA sprite
mode happens on the first write to a register that does not exist on OCS, for
example `FMODE` or `BPLCON4`.

**OCS sprites (default/compatibility mode):**
- 2 bitplanes = 4 colors (color 0 = transparent)
- 16 pixels wide, fixed
- 2 data words per line (SPRxDATA, SPRxDATB)
- Colors from palette entries 16-31 (fixed bank)
- No attachment needed for 4 colors

**AGA sprites (enhanced mode):**
- 4 bitplanes = 16 colors (color 0 = transparent)
- 16, 32, or 64 pixels wide (global, see `FMODE`)
- 4 data words per line for 4 bitplanes at 16px
- Colors from the 256-color palette, bank selectable via `BPLCON4`
- Attached sprites give 256 colors (8 bitplanes) but cost 2 sprite slots

**FMODE ($dff1fc) bit fields relevant to sprites:**
- bits 3-2 (SPAGEM/SPR32): sprite fetch/display width. `00` = 16 bits,
  `01` = 32 bits, `10` = 32 bits in page mode, `11` = 64 bits.
- bits 1-0: bitplane fetch width (not sprite).
- The width is **global**: all eight channels use it. You cannot mix 16/32/64
  px sprites in one display (the Copper can change `FMODE` mid-frame only with
  careful DMA timing).

There are no per-sprite width bits in `SPRxCTL`. Setting `FMODE` bits 3-2 is
what selects the width, and the control-word and per-line data layout change
with it.

**Control-word header and per-line layout by width**

Each sprite/channel data block starts with a header of `2 * (width/16)` words:
the POS word first, the CTL word in the middle, zero padding around them.
After the header come the image words, grouped per plane, and the block ends
with a terminator of the same size as the header (all zero words).

Let N = width/16 (1, 2 or 4) and P = number of planes in that channel
(2 for OCS/attached 2-plane channels, 4 for AGA 4-plane sprites).

| Width | N | Header size | POS at | CTL at | Data starts | Pointer alignment | Words per plane per line |
|---|---|---|---|---|---|---|---|
| 16px | 1 | 2 words (4 bytes) | +0 | +2 | +4 | 2 bytes | 1 |
| 32px | 2 | 4 words (8 bytes) | +0 | +4 | +8 | 4 bytes | 2 |
| 64px | 4 | 8 words (16 bytes) | +0 | +8 | +16 | 8 bytes | 4 |

The header is literally `POS, 0..., CTL, 0...` (for 64px:
`POS, 0, 0, 0, CTL, 0, 0, 0`). The terminator after the last line is
`2*N` zero words (4 words for 32px, 8 words for 64px).

Per line, the words are grouped by plane: all words of plane 0 for that line,
then all words of plane 1, and so on. So:

- line words = P * N
- line bytes = P * N * 2
- total block words = 2*N (header) + height * P * N + 2*N (terminator)
- total block bytes = total words * 2

Examples:
- 16px, 2 planes, height 16: header 2 + 16*2 + term 2 = 36 words (72 bytes).
- 32px, 2 planes, height 16: header 4 + 16*4 + term 4 = 72 words (144 bytes).
- 64px, 2 planes, height 16: header 8 + 16*8 + term 8 = 144 words (288 bytes).
- 64px, 4 planes, height 16: header 8 + 16*16 + term 8 = 272 words (544 bytes).

For an attached pair, each channel keeps its own 2-plane block; the two
channels together form the 4 planes. Both channels must use the same width and
stay at the same position. (256-color AGA attached mode uses two 4-plane
channels, i.e. 8 planes across the pair.)

**API: keep one module, prefix the AGA routines with `aga_`**

Do not add a separate module. Put the AGA extensions in the existing
`shared_amiga_sprites.p8` block and prefix them with `aga_`, so one
`%import sprites` covers both and the AGA dependency is visible at each call
site. Unused `aga_*` subroutines are stripped on ECS builds by the block's
`%option ignore_unused`, so `amiga500` pays nothing.

- `enum AgaWidth { W16 = 0, W32 = 1, W64 = 3 }` (the values are `FMODE`
  bits 3-2; `W32` may also be 2 for page mode).
- `aga_set_width(AgaWidth width)` — sets `FMODE` bits 3-2, preserves the
  bitplane bits, and records the selected width in a private module variable
  (declared like `blitter.line_mod`) for the wide routines to use.
- `aga_init(^^mobs.Sprite spr)` — pointer setup plus the width-specific header
  (same role as `init()`, but the caller must have called `aga_set_width`).
- `aga_pos(^^mobs.Sprite spr)` — wide-aware replacement for `pos()`.
- `aga_hide(^^mobs.Sprite spr)` — wide-aware replacement for `hide()`.
- `aga_set_resolution(ubyte res)` — sprite resolution via `BPLCON3`.
- `aga_set_bank(ubyte even_bank, ubyte odd_bank)` — `BPLCON4` palette banks.
- Reuse the existing `attach()`, `set_colors16()`, `enable()` and `disable()`
  where the width does not matter. Keep the 16px `init()`/`pos()`/`hide()`
  unchanged so OCS/ECS code is unaffected.

Store the width as a private variable named e.g. `aga_width`. The caller must
call `aga_set_width()` before any wide sprite use and reset it to `W16` (and
`FMODE` to 0) before returning to OCS mode.

**Implementation notes**

- `aga_pos()` must write the POS word at data+0 and the CTL word at the width's
  CTL offset (`+2`, `+4` or `+8`), not always at `+2`. Leave the padding words
  zero. `aga_hide()` must terminate the block with `2*N` zero words.
- The attach bit is still bit 7 of the CTL word.
- Keep the sprite data pointer aligned to 2/4/8 bytes (word/longword/quadword)
  for 16/32/64 px. Prog8 arrays are not guaranteed to be 8-byte aligned, so the
  wide helper should align the data pointer (or allocate the buffers with an
  alignment-aware mechanism) before handing it to `SPRxPT`.
- Set `FMODE` bits 3-2 before enabling sprite DMA. Writing any AGA register
  switches the chip out of OCS compatibility mode; reset `FMODE` to 0 (and
  `BPLCON4` as needed) when returning to OCS mode.

**CTL word bits in AGA mode (lores use)**

`SPRxCTL` bits 15-8 = VSTOP low 8 bits, bit 7 = attach, bits 6/5 = VSTART/
VSTOP 10th bit, bits 4/3 = 70ns/35ns horizontal start bits, bit 2 = VSTART
9th bit, bit 1 = VSTOP 9th bit, bit 0 = HSTART low bit (140ns). The existing
lores formula (bits 2, 1, 0) is unchanged; leave bits 6-3 zero unless using
hires/superhires positioning.

**Registers available in custom.p8 (shared_amiga_custom.p8):**

All of the following are already defined and usable from Prog8:

- `BPLCON4` ($dff10c) — sprite color banking + resolution (AGA)
- `BPLCON3` ($dff106) — palette control 3 (AGA, includes sprite resolution)
- `FMODE` ($dff1fc) — fetch mode (AGA; bits 3-2 = sprite fetch/display width,
  bits 1-0 = bitplane fetch width)
- `SPR0CTL`..`SPR7CTL` ($dff142, $dff14a, ...) — sprite control words (no
  per-sprite width bits; width comes from `FMODE` bits 3-2)
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

## CIA timer interrupt helpers

Interrupt-driven CIA timer support, the part that needs design decisions
rather than register pokes:

- Route to CPU levels 2 (CIA-A) / 6 (CIA-B) through `INTENA` PORTS/EXTER,
  install the handler via `custom.cpuVBR`, acknowledge by *reading* ICR,
  return with `rte`, restore on exit.
- Keep the handler minimal (tick counter / frame flag), like the VERTB one.
- Decide the callback shape: a Prog8 `sub` pointer called from the stub (needs
  a register contract the stub upholds), a bare address of hand-written asm, or
  an `exec.Interrupt`-like node the user fills in.
- Decide the OS story: `cia.resource/AddICRVector` and `RemICRVector` are not
  wrapped anywhere in the library (only `ptplayer` calls them from inline asm),
  while `timer.device` claims CIA-A Timer A and Timer B for its microhertz unit
  and the 50/60 Hz tick. Direct ICR writes while the OS runs are explicitly
  discouraged, so this helper can only be bare-metal until someone wraps
  `cia.resource`.
- Name it once it exists: something like `cia.timer_irq`, kept separate from
  the polling helpers.
