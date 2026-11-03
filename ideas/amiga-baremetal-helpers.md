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

## more BOB helpers?

- blitter.masked_blit is too limited: no soft-scroll etc.
- need a more versatile routine. still needs to be called once per bitplane no way around that.
- do we need a routine to clear the BOB as well or can we use masked blit for that too efficiently maybe?
- what else is missing to easily handle BOBS? note that resource handling is left to the main program

## Text output library

Need a simple library to quickly write text onto a bare-metal bitmap screen.

- what font though? Topaz 8 from rom? how to get the bitmap data from it? Or embedded font?
- colors?
- arbitrary X pixel position or byte aligned?
