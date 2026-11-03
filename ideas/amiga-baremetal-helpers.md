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
- Likely needs codegen work, not just library: handler subs need
  save/restore + `rte` frames, which the m68k backend may not emit today.
- Extend `grab_system`'s existing IRQ1/3/4 vector save/restore to cover
  installed handlers.
- VERTB and CIA timers below become thin consumers of this API.

## VERTB IRQ handler

No bare-metal vertical-blank example exists (`lowlevel-timer.p8` is OS-based;
m68k targets have no `sys.set_irq` helpers, see `targetsystem.rst`).

- Install an autovector level-3 handler via `custom.cpuVBR` after
  `custom.grab_system()`, enable `VERTB` in `INTENA`, acknowledge via
  `INTREQ` every field, return with `rte`, restore on exit (the IRQ1/3/4
  vectors `grab_system` already preserves cover save/restore).
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

## more BOB helpers?

- blitter.masked_blit is too limited: no soft-scroll etc.
- need a more versatile routine. still needs to be called once per bitplane no way around that.
- do we need a routine to clear the BOB as well or can we use masked blit for that too efficiently maybe?
- what else is missing to easily handle BOBS? note that resource handling is left to the main program

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
- Transparent only: 0 pixels never touch the destination (OR semantics), so
  only planes with the foreground bit set are visited (cost scales with
  popcount of the color). Erasing/redrawing backdrops stays the caller's job
  (save buffer, blitter re-fill, or full redraw as in `3d`), like the other
  blitter primitives the helper stays write-only.
- Minimal API: `text.init(bitmap)`, `text.color(fg)`, `text.locate(x, y)`,
  `text.print(str)` / `text.print_char(ch)`. Replaces the hand-stamped
  message bitmap in `3d` with per-glyph blits.
