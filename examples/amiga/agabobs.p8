; AGA demo for three graphics helpers no other example used:
;   custom.load_colors()  - load a 24-bit, 32-color palette into AGA COLORxx
;   blitter.line_color()  - draw an exact-pen line across all 5 bitplanes
;   blitter.masked_blit() - cookie-cut a masked sprite into the planes
; For amiga1200 only: compile with  prog8c -target amiga1200 agabobs.p8
; A 320x256, 5-bitplane (32 pen) display is driven by a copper list built
; with playfield and copper. No copper gradient: the list deliberately leaves
; COLORxx alone, so the CPU-written AGA palette from load_colors() persists.
; 64 lines radiate from the center like a starburst (math.sin8/cos8), two per
; pen and skipping pen 0 (the background). Five copies of a masked dragon
; sprite (static CHIP RAM data, converted from the cx16
; examples/cx16/sprites/DRAGONSPRITE.BIN) sit in a die-5 pattern; the
; starburst shows through the transparent mask. Press ESC to exit.

%import graphics
%import custom
%import playfield
%import copper
%import blitter
%import math
%import textio

main {
    const word WIDTH = 320
    const word HEIGHT = 256
    const ubyte DEPTH = 5                 ; 2^5 = 32 pens
    const ubyte NUM_COLORS = 32
    const uword ROW_BYTES = 40            ; bytes per bitplane row (320/8)

    const uword CX = 160                  ; starburst center
    const uword CY = 128
    const word STAR_RADIUS = 120
    const ubyte NUM_LINES = 64            ; two lines per pen
    const ubyte ANGLE_STEP = 4           ; 256 / NUM_LINES

    const uword SPRITE_WORDS = 2          ; 32 pixels wide
    const uword SPRITE_HEIGHT = 32
    const uword PLANE_STRIDE = SPRITE_WORDS * SPRITE_HEIGHT   ; 64 words

    ; die-face "5": four corners + center (top-left pixel of each 32x32 sprite).
    ; Outer four pulled ~20 px toward the center so they overlap more spokes.
    ; X stays a multiple of 16 for word-aligned blits.
    uword[10] die_xy = [64, 48, 224, 48, 64, 160, 224, 160, 144, 112]

    ; Canonical $00RRGGBB palette, filled with a 24-bit rainbow at startup.
    long[NUM_COLORS] palette

    ^^graphics.BitMap bm
    ^^custom.DisplayRegs regs

    sub start() {
        if custom.detect_chipset() != custom.CHIPSET::AGA {
            txt.print("agabobs needs AGA (compile for amiga1200).\n")
            return
        }
        if not playfield.create(WIDTH, HEIGHT, WIDTH, HEIGHT, DEPTH, custom.DISPLAY_DEFAULT, 0)
            return
        bm = playfield.bitmap
        regs = playfield.regs
        make_palette()
        uword planeBase = build_copperlist()
        playfield.patch_copper(&copperdata.copper_list, planeBase)

        custom.grab_system(true)
        custom.waitvsync()
        custom.COP1LC = &copperdata.copper_list
        custom.DMACON = custom.DMAF_SETCLR | custom.DMAF_MASTER | custom.DMAF_COPPER | custom.DMAF_BLITTER
        custom.COPJMP1 = 0

        ; the copper never touches COLORxx, so this 24-bit AGA palette persists
        custom.load_colors(&palette, NUM_COLORS)

        blitter.line_init($ffff, $8000, $ffff, ROW_BYTES)
        draw_starburst()
        draw_dragons()
        blitter.wait()

        repeat {
            custom.waitvsync()
            bool pressed
            ubyte keycode
            pressed, keycode = custom.raw_keyboard_poll()
            if pressed and (keycode & $80)==0 and keycode==custom.KeyCode::ESC
                break
        }

        custom.FMODE = $0000
        custom.restore_system()
        playfield.destroy()
    }

    sub make_palette() {
        ; Pen 0 is the dark background. Pens 1..31 are a full-saturation
        ; 24-bit HSV rainbow (one full hue turn). The fine low nibbles from
        ; the sector ramp are what OCS/ECS would truncate to 4 bits/channel.
        palette[0] = $101820
        for i in 1 to NUM_COLORS-1 {
            ubyte h = ((i-1) as ubyte) * 8       ; 0..240
            ubyte sector = h / 43                ; 0..5
            uword f = ((h % 43) as uword) * 255 / 43   ; 0..255
            long r = 0
            long g = 0
            long b = 0
            when sector {
                0 -> {
                    r = 255
                    g = f
                    b = 0
                }
                1 -> {
                    r = 255-f
                    g = 255
                    b = 0
                }
                2 -> {
                    r = 0
                    g = 255
                    b = f
                }
                3 -> {
                    r = 0
                    g = 255-f
                    b = 255
                }
                4 -> {
                    r = f
                    g = 0
                    b = 255
                }
                else -> {
                    r = 255
                    g = 0
                    b = 255-f
                }
            }
            palette[i] = (r << 16) | (g << 8) | b
        }
    }

    sub build_copperlist() -> uword {
        copper.init(&copperdata.copper_list, len(copperdata.copper_list))
        void copper.move($0096, $83c0)   ; DMACON: SET | DMAEN | BPLEN | COPEN | BLTEN
        uword planeBase = copper.attach_planes(DEPTH)
        void copper.move($0100, regs.bplcon0)
        void copper.move($008e, regs.diwstrt)
        void copper.move($0090, regs.diwstop)
        void copper.move($0092, regs.ddfstrt)
        void copper.move($0094, regs.ddfstop)
        void copper.move($01fc, regs.fmode)
        void copper.move($0108, regs.bpl1mod)
        void copper.move($010a, regs.bpl2mod)
        void copper.move($0102, 0)       ; BPLCON1: no fine scroll
        void copper.move($01e4, regs.diwhigh)
        void copper.move($0104, 0)       ; BPLCON2: single playfield
        copper.end()
        return planeBase
    }

    sub draw_starburst() {
        ; 64 radial lines, about two per pen, skipping pen 0 (the dark
        ; background) so no spoke is invisible. sin8/cos8 give a signed
        ; -127..127 unit vector; scaling keeps every endpoint on screen.
        for i in 0 to NUM_LINES-1 {
            ubyte angle = (i as ubyte)*ANGLE_STEP
            word dx = (math.cos8(angle) as word)
            word dy = (math.sin8(angle) as word)
            uword x2 = ((CX as word) + dx*STAR_RADIUS/127) as uword
            uword y2 = ((CY as word) + dy*STAR_RADIUS/127) as uword
            ubyte pen = (1 + (i as uword)*(NUM_COLORS-1)/NUM_LINES) as ubyte
            blitter.line_color(CX, CY, x2, y2, pen, DEPTH, &bm.Planes)
        }
    }

    sub draw_dragons() {
        ; five cookie-cut blits of the same dragon. One masked_blit per
        ; bitplane; where the mask is 0 the starburst underneath shows through.
        uword dst_mod = ROW_BYTES - SPRITE_WORDS*2
        for d in 0 to 4 {
            uword sx = die_xy[(d as uword)*2]
            uword sy = die_xy[(d as uword)*2+1]
            uword offset = sy*ROW_BYTES + sx/8
            for p in 0 to DEPTH-1
                blitter.masked_blit(&dragondata.src[(p as uword)*PLANE_STRIDE], &dragondata.mask,
                                    bm.Planes[p] + offset, SPRITE_WORDS, SPRITE_HEIGHT, 0, 0, dst_mod)
        }
    }
}

dragondata {
    %option amiga_chipram

    ; mask = 1 where the sprite is opaque; src holds each pen's bit per plane.
    ; Converted from the cx16 dragon (4bpp, index 0 transparent).
    uword[64] mask = [
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0001, $8000, $e000, $e000, $3c00, $7000,
        $1fc0, $3c00, $07f8, $3e00, $03fe, $1f00, $01ff, $9f80,
        $00ff, $cfc0, $007f, $ffc0, $007f, $ffe0, $00ff, $fff0,
        $003f, $fff0, $000f, $fff8, $0007, $fff8, $0007, $fff0,
        $0001, $ffe0, $0003, $fff0, $0003, $fff0, $0007, $fff8,
        $001f, $fff8, $001f, $fff8, $003f, $fffc, $003f, $fffc
    ]
    uword[320] src = [
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0001, $8000, $e000, $e000, $2c00, $5000,
        $14c0, $3400, $06d8, $3a00, $03ea, $0d00, $01ba, $9680,
        $00c6, $c740, $0040, $5780, $0043, $2fa0, $001b, $95d0,
        $003e, $1e30, $000c, $3788, $0004, $1f88, $0004, $8ff0,
        $0001, $1de0, $0003, $07d0, $0002, $03b0, $0002, $01e8,
        $0014, $73b0, $0018, $8a38, $003a, $f7b4, $0024, $2f14,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $4000, $3000, $3000,
        $0e00, $1800, $00c0, $0e00, $0138, $0700, $00e7, $0380,
        $007d, $c180, $0070, $4080, $004f, $b760, $00fa, $d3c0,
        $000f, $d9f0, $0003, $b8a0, $0003, $70c0, $0001, $c480,
        $0000, $5700, $0000, $8940, $0002, $f450, $0005, $b260,
        $000b, $ffb0, $000b, $8e38, $001c, $5c98, $0016, $014c,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0700, $2000, $07e0, $1000, $00dc, $0800, $001b, $0400,
        $0003, $8600, $000f, $e740, $003f, $f080, $007d, $3820,
        $0010, $3a00, $0004, $7f50, $0000, $c030, $0003, $3400,
        $0000, $30a0, $0000, $b840, $0001, $8e60, $0003, $ecd0,
        $0004, $c050, $0004, $58c0, $001a, $8848, $0018, $0a44,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $1000, $0000, $0800,
        $0000, $0800, $0000, $0800, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0800, $0000, $0000,
        $0000, $8200, $0000, $40a0, $0000, $0000, $0000, $0000,
        $0000, $0008, $0000, $2120, $0001, $0000, $0001, $d0a0,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0001, $8000, $e000, $e000, $3c00, $7000,
        $1fc0, $3c00, $07f8, $3e00, $03fe, $1f00, $01ff, $9f80,
        $00ff, $cfc0, $007f, $ffc0, $007f, $ffe0, $00ff, $fff0,
        $003f, $fff0, $000f, $fff8, $0007, $fff8, $0007, $fff0,
        $0001, $ffe0, $0003, $fff0, $0003, $fff0, $0007, $fff8,
        $001f, $fff8, $001f, $fff8, $003f, $fffc, $003f, $fffc
    ]
}

copperdata {
    %option amiga_chipram

    uword[80] copper_list
}
