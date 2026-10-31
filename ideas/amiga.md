# Amiga Library and Hardware Helper Ideas

Status: proposal / backlog. The Amiga targets (`amiga500`, `amiga1200`) are
experimental. This document collects suggested additions on top of the
existing Amiga library set, split into (1) AmigaOS libraries not yet wrapped
and (2) hardware-banging helper modules for bare-metal demos/games.

## Current coverage

### AmigaOS libraries (wrapped)

`exec`, `dos`, `graphics`, `intuition`, `utility`, `icon`, `iffparse`,
`arexx`, `lowlevel`, `diskfont`, `asyncio`, plus device-level wrappers
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

## 1. AmigaOS libraries worth adding

These are in the generator's bank map (`scripts/amigalibs2prog8.py`) but not
in `AMIGA_LIBS`, so they are not generated. Adding one is mostly: append to
`AMIGA_LIBS`, regenerate, then hand-add `openlib`/`closelib` helpers for
on-demand libraries (following the `lowlevel`/`diskfont` pattern) and register
the base in `syslib.p8`. Requires the Amiga NDK locally.

### High value

- `asl.library` — **implemented** (both targets, kickstart 2.0+). Module `asllib`
  (named to avoid clashing with the 68k `asl` shift mnemonic), shared module
  `shared_amiga_asllib`, `sys.AslBase` on demand. See
  `examples/amiga/aslfile.p8`.
- `datatypes.library` — load/save images and sounds via DataType objects;
  big win for asset pipelines
- `layers.library` — layer management for multi-window apps (some layer
  functions currently live in `graphics`)
- `keymap.library` — keyboard layout conversion for text input
- `nonvolatile.library` — battery-backed NVRAM save; ideal for game high scores
- `console.device` — mostly redundant: `dos.library` read/write on `CON:`/
  `RAW:` windows already routes through console.device, and ANSI escape
  sequences for cursor control/colors work via normal `dos.PutStr`. What dos
  alone cannot easily do is keymap get/set (`CD_ASKKEYMAP`/`CD_SETKEYMAP`)
  and switching cooked/raw input modes at runtime. Only worth adding if
  someone needs those specific features.

### Medium value

- `commodities.library` — hotkeys and background utilities
- `locale.library` — localization
- `workbench.library` — Workbench integration beyond `icon`
- `realtime.library` — multimedia timing/synchronization
- `input.device` / `gameport.device` — raw input (common cases already
  covered by `custom`)

### Low / niche value

- `translator.library` / `narrator.device` (speech), `expansion.library`,
  `cardres`, `battmem`/`battclock`, `potgo`/`misc` resources
- Math libraries (`mathffp`, `mathieeesingbas`, etc.) — floats are already
  software-implemented
- Boopsi gadget classes (`gadtools`, `button`, `layout`, ...) — mainly for
  GUI applications; games/demos rarely need them

## 2. Hardware-banging helper modules

`easygfx` already covers OS-level screens (`fullscreen(w,h,depth,viewmodes)`).
The gap is **bare-metal display setup** for demos/games that disable the OS
via `custom.grab_system()`. These are pure Prog8/asm helpers on top of
already-exposed registers, so they need no NDK or regeneration.

### Suggested, in priority order

1. **Hardware display setup** (highest value) — given `width, height, depth,
   hires, lace, superhires`, compute and write `DIWSTRT/STOP`,
   `DDFSTRT/STOP`, `BPLCON0/1`, `BPL1/2MOD`, and the `BPLxPT` registers.
   This is fiddly and a common source of "black screen" bugs. A
   `display.setup(...)` would save a lot of pain.

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

### Recommendation

Start with #1 + #6 (display setup + named modes) and #2 (bitplane manager):
they unlock bare-metal demos, which is where Amiga homebrew shines. Then #3
(copper builder) as the next biggest win. The rest (#4, #5) are incremental
and can grow from real use.

## Notes

- The Amiga targets are still **experimental**; `-emu2` (Vamos) does not
  support many OS calls, so new libraries/helpers need testing under a real
  emulator (WinUAE/FS-UAE) or Copperline.
- No Amiga NDK is present in this environment, so OS-library regeneration
  requires installing the NDK locally first.
- Hardware helpers (#1-#6) are the cheaper, higher-impact starting point.