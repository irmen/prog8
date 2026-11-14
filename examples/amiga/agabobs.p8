; AGA 8-bitplane / 256-color demo for three graphics helpers:
;   custom.load_colors()  - load a 24-bit, 256-color palette into AGA COLORxx
;   blitter.line_color()  - draw an exact-pen line across all 8 bitplanes
;   blitter.masked_blit() - cookie-cut a masked bob into the planes
; For amiga1200 only: compile with  prog8c -target amiga1200 agabobs.p8
; The palette is 256 colors: pens 0..31 are the monster's OWN palette from
; monster.png (pen 0 black, index 0 magenta is transparent), pens 32..255 are
; a 224-color HSV rainbow. 255 radial lines (one per color, skipping pen 0)
; form a starburst drawn with math.sin8/cos8. Four copies of the monster
; (76x86, padded to 80x86) stay in the corners as 256-color bobs.
; The bob (8 planes) and mask use their original five-word rows without guard
; words. The binary files are included with %asmbinary into CHIP RAM. The
; starburst shows through the transparent mask. Four copies stay in the
; corners; ten overlapping center copies are offset by 3x2 pixels to
; demonstrate the blitter's barrel shift. No copper gradient. Press ESC to exit.

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
    const ubyte DEPTH = 8                 ; 2^8 = 256 pens
    const uword NUM_COLORS = 256
    const uword ROW_BYTES = 40            ; bytes per bitplane row (320/8)

    const uword CX = 160                  ; starburst center
    const uword CY = 128
    const word STAR_RADIUS = 120
    const ubyte NUM_LINES = 255           ; one per pen, skipping pen 0

    const uword SPRITE_WORDS = 5          ; 80 pixels (76 padded to a word)
    const uword SPRITE_HEIGHT = 86
    const uword PLANE_BYTES = SPRITE_WORDS*SPRITE_HEIGHT*2    ; 860

    ; Four corner BOBs remain word-aligned.
    uword[8] die_xy = [48, 16, 208, 16, 48, 152, 208, 152]

    ; monster.png palette (32 x $00RRGGBB). Color 0 was magenta transparent;
    ; it stays black here. Converted by the script that produced monster.bin.
    long[32] monster_palette = [
        $000000, $000000, $180f07, $4c0202, $191817, $670709, $322010, $252525,
        $2e2f2f, $42301f, $3a3b3b, $8a2c25, $54412f, $464747, $434a30, $5d4325,
        $8c4a0b, $ae4200, $585858, $795532, $6a694e, $d6600a, $af7c20, $968469,
        $fb8024, $c39b2e, $baa48f, $ffa45b, $ffb37c, $c4c4c4, $ffd1b5, $ffffff
    ]

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

        ; load_colors() takes a ubyte count, so 255 entries here and the
        ; last one via set_aga_color(); then reset the AGA bank window.
        custom.load_colors(&palette, 255)
        custom.set_aga_color(255, palette[255])
        custom.BPLCON3 = 0
        custom.BPLCON4 = 0

        blitter.line_init($ffff, $8000, $ffff, ROW_BYTES)
        draw_starburst()
        draw_bobs()
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
        ; pens 0..31: the monster's real colors, with pen 0 black.
        sys.memcopy(&monster_palette as long, &palette as long, 32 * 4)
        ; pens 32..255: a 224-color full-saturation HSV rainbow.
        ubyte r,g,b
        for uword i in 32 to NUM_COLORS-1 {
            uword h = ((i-32) as uword)*256/224      ; 0..255 hue
            ubyte sector = (h/43) as ubyte            ; 0..5
            ubyte f = (((h % 43) as uword)*255/43) as ubyte   ; 0..255 ramp
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
            palette[i] = mklong(0, r, g, b)
        }
    }

    sub build_copperlist() -> uword {
        copper.init(&copperdata.copper_list, len(copperdata.copper_list))
        void copper.move($0096, $83c0)   ; DMACON: SET | DMAEN | BPLEN | COPEN | BLTEN
        uword planeBase = copper.base_list(regs, DEPTH)
        copper.end()
        return planeBase
    }

    sub draw_starburst() {
        ; 255 radial lines, one per pen (1..255), pen 0 skipped because it
        ; is the black background. sin8/cos8 give a signed -127..127 vector.
        for i in 0 to NUM_LINES-1 {
            word dx = math.cos8(i)
            word dy = math.sin8(i)
            word x2 = (CX as word) + dx*STAR_RADIUS/127
            word y2 = (CY as word) + dy*STAR_RADIUS/127
            blitter.line_color(CX, CY, x2 as uword, y2 as uword, i+1, DEPTH, &bm.Planes)
        }
    }

    sub draw_bobs() {
        ; Four original word-aligned copies.
        const uword corner_src_mod = 0   ; tightly packed five-word rows
        const uword corner_dst_mod = ROW_BYTES - SPRITE_WORDS*2
        for d in 0 to 3 {
            uword corner_x = die_xy[(d as uword)*2]
            uword corner_y = die_xy[(d as uword)*2+1]
            uword offset = corner_y*ROW_BYTES + corner_x/8
            for p in 0 to DEPTH-1 {
                pointer corner_plane = &monsterbob.monster_planes + (p as uword)*PLANE_BYTES
                blitter.masked_blit(corner_plane, &monsterbob.monster_mask,
                                    bm.Planes[p] + offset, SPRITE_WORDS, SPRITE_HEIGHT,
                                    corner_src_mod, corner_src_mod, corner_dst_mod,
                                    0)
            }
        }

        ; Add ten overlapping copies around the screen center. Shifted pixels
        ; past the original five-word row width are clipped at the right edge.
        const uword trail_dst_mod = ROW_BYTES - SPRITE_WORDS*2
        const uword center_x = (WIDTH - (76 + 9*3))/2
        const uword center_y = (HEIGHT - (SPRITE_HEIGHT + 9*2))/2
        for uword i in 0 to 9 {
            uword trail_x = center_x + i*3
            uword trail_y = center_y + i*2
            ubyte shift = (trail_x & 15) as ubyte
            uword byte_off = trail_y*ROW_BYTES + (trail_x/16)*2
            for uword q in 0 to DEPTH-1 {
                pointer trail_plane = &monsterbob.monster_planes + q*PLANE_BYTES
                blitter.masked_blit(trail_plane, &monsterbob.monster_mask,
                                    bm.Planes[q] + byte_off, SPRITE_WORDS, SPRITE_HEIGHT,
                                    0, 0, trail_dst_mod,
                                    shift)
            }
        }
    }
}

monsterbob {
    %option amiga_chipram

    %align 4
monster_mask:
    %asmbinary "monster_mask.bin"

    %align 4
monster_planes:
    %asmbinary "monster.bin"
}

copperdata {
    %option amiga_chipram

    uword[96] copper_list
}
