# Amiga Library and Hardware Helper Ideas

Status: OS-level library coverage is feature-complete for the non-niche use
cases. The Amiga targets (`amiga500`, `amiga1200`) are experimental. This
document records the wrapped libraries and the still-suggested hardware
helper modules.

## Current coverage

### AmigaOS libraries (wrapped)

`exec`, `dos`, `graphics`, `intuition`, `utility`, `icon`, `iffparse`,
`arexx`, `lowlevel`, `diskfont`, `asyncio`, `asl` (1200/500 2.0+),
`datatypes` (1200 only, 3.0+, essentials subset), plus device-level wrappers
`timer`, `audio`, `trackdisk`. Hand-made helpers: `easygfx`, `conv`,
`strings`, `lists`, `sorting`, `math`, `floats`, `compression`,
`coroutines`, `buffers`, `lineclip`, `emudbg`, `adpcm`, `wavfile`, `ahi`
(1200 only), `blitter`, `ptplayer`, `sound`, `custom`.

### Hardware (registers exposed)

The `custom` module exposes the full custom-chip register set (copper,
blitter, 8 sprites, 8 bitplanes, 4 audio channels, disk DMA, beam position,
CIA-A/B, AGA extended registers) plus helpers: chipset detection,
`grab_system`/`restore_system`, AGA palette, joystick/mouse, raw keyboard
matrix, audio filter, vsync, PAL/NTSC detection.

## Hardware-banging helper modules (still suggested)

`easygfx` already covers OS-level screens (`fullscreen(w,h,depth,viewmodes)`).
The gap is **bare-metal display setup** for demos/games that disable the OS
via `custom.grab_system()`. These are pure Prog8/asm helpers on top of
already-exposed registers, so they need no NDK or regeneration.

1. **Hardware display setup** (highest value) — given `width, height, depth,
   hires, lace, superhires`, compute and write `DIWSTRT/STOP`,
   `DDFSTRT/STOP`, `BPLCON0/1`, `BPL1/2MOD`, and the `BPLxPT` registers.
   This is fiddly and a common source of "black screen" bugs. A
   `display.setup(...)` would save a lot of pain.

   Safer alternative: let the OS set up the display first by opening an
   Intuition screen (or `easygfx.fullscreen(...)`) with the desired mode,
   then call `custom.grab_system()`. The display registers keep their
   OS-configured values, so the screen stays visible while the OS is
   disabled. This avoids hand-computed timing and is recommended whenever
   the requested mode is one Intuition supports; direct register setup is
   still needed for modes the OS does not provide (e.g. some superhires/HAM
   or exact custom timings).

2. **Bitplane manager** — allocate chip-RAM bitplanes, set pointers, clear
   them, handle modulo/interleaved layouts. Pairs with #1.

3. **Copper list builder** — currently only `CMove`/`CWait` extsubs exist. A
   small helper generating copper lists for common effects (raster bars,
   color cycling, per-scanline palette, smooth scrolling) would be very
   demo-friendly. Could live in a `copper` module using `exec.AllocMem` plus
   `graphics.CMove`/`CWait`.

4. **Palette helpers** — `LoadRGB4`/`SetRGB4` already exist; add AGA-aware
   loaders (building on `custom.set_aga_color`), HAM palette helpers, and
   color-cycling routines.

5. **Sprite helpers** — position/control word packing, attach sprite
   pointers, AGA 64-wide sprite setup. Registers are there but
   boilerplate-heavy.

6. **Named display-mode constants** — a small set of named modes such as
   `MODE_LORES_320x256x5`, `MODE_HIRES_640x256x4`, `MODE_SUPERHIRES`,
   `MODE_HAM6`, `MODE_HAM8` carrying their computed register values, so
   users can just do `custom.BPLCON0 = MODE_LORES_...`.

## Notes

- The Amiga targets are still **experimental**; `-emu2` (Vamos) does not
  support many OS calls, so testing needs a real emulator (WinUAE/FS-UAE)
  or Copperline.
- Startup code opens `dos`, `graphics`, `intuition`, `icon`, `utility` and
  closes every opened library (including on-demand ones) at exit.