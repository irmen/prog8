# Amiga Sprite Helper Library

Add a bare-metal `sprites` helper module for the `amiga500` and `amiga1200`
targets, using the registers exposed by `custom`.

## Plan

- Provide helpers to pack sprite position and control words.
- Provide helpers to set the hardware sprite pointers to sprite data in
  chip RAM.
- Support AGA's global `FMODE` sprite fetch-width setting (up to 64 pixels in
  low resolution), with the matching data/control-word layout; retain the
  standard 16-pixel sprite format on non-AGA hardware.
- Keep the module usable by demos and games after `custom.grab_system()`,
  without OS dependencies or NDK/generator changes.

## References

- [AGA sprite documentation](https://jvaltane.kapsi.fi/amiga/howtocode/aga.html#sprites)
- [Anguish AGA walkthrough](https://www.youtube.com/watch?v=mRZfwJAkkIA&t=62s), showing 64-pixel-wide sprites
