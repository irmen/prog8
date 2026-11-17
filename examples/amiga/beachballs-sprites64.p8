; AGA hardware-sprite beachballs (16 colors via attached sprites).
; AGA only, compile with -target amiga1200.
; 64x64 pixel AGA sprites (64px wide, 64 high).
%import graphics
%import custom
%import playfield
%import copper
%import sprites
%import mobs
%import blitter
%import math
%import textio
%import gfxtext

main {
    const word WIDTH = 320
    const word HEIGHT = 256
    const ubyte DEPTH = 4
    const ubyte NUM_BALLS = 4
    const ubyte BALL_WIDTH = 64
    const ubyte BALL_HEIGHT = 64
    ; 8-word header + 8 words/line (64px, 2 planes).
    const uword SPRITE_WORDS = 8 + (BALL_HEIGHT as uword) * 8 + 8  ; header + data + terminator
    const word MAX_X = WIDTH-BALL_WIDTH
    const word MAX_Y = HEIGHT-BALL_HEIGHT
    const uword ROW_BYTES = 40
    const word STAR_RADIUS = 120

    mobs.Mob[NUM_BALLS] mobdata
    mobs.Sprite[NUM_BALLS] even_balls
    mobs.Sprite[NUM_BALLS] odd_balls
    word[NUM_BALLS] vx
    word[NUM_BALLS] vy
    long[16] palette = [
        $000000, $ffffff, $c4c4c4, $585858, $7a0000, $ff4040, $7a7a00, $ffe040,
        $006000, $30ff60, $00007a, $6060ff, $600060, $ff50ff, $252525, $ffb37c
    ]
    uword[16] sprite_palette = [
        $0000, $0f00, $00f0, $000f, $0ff0, $0f0f, $00ff, $0700,
        $0070, $0007, $0770, $0707, $0077, $0000, $0fff, $0ccc
    ]
    ^^graphics.BitMap bm
    ^^custom.DisplayRegs regs
    uword copperPlaneBase

    sub start() {
        if not playfield.create(WIDTH, HEIGHT, WIDTH, HEIGHT, DEPTH, custom.DISPLAY_DEFAULT, 0) {
            txt.print("Could not create playfield.\n")
            return
        }
        bm = playfield.bitmap
        regs = playfield.regs
        ; The copper list rewrites FMODE/BPLCON0 every frame, so include the
        ; 64px sprite fetch bits (FMODE 3-2) and ECSENA (needed for border
        ; sprites) in the values it writes.
        regs.fmode = regs.fmode | %1100
        regs.bplcon0 = regs.bplcon0 | $0001   ; ECSENA gates border sprites
        prepare_balls()
        copperPlaneBase = build_copperlist()
        playfield.patch_copper(&copperdata.copper_list, copperPlaneBase)

        gfxtext.init(ROW_BYTES, DEPTH, &bm.Planes)

        custom.grab_system(true)
        custom.waitvsync()
        sprites.aga_set_width(sprites.AgaWidth::W64)
        for i in 0 to NUM_BALLS-1 {
            sprites.aga_init_attached(&even_balls[i], &odd_balls[i])
        }
        custom.COP1LC = &copperdata.copper_list
        custom.DMACON = custom.DMAF_SETCLR | custom.DMAF_MASTER | custom.DMAF_COPPER
        custom.COPJMP1 = 0
        sprites.set_colors16(&sprite_palette as ^^uword)
        custom.load_colors(&palette, 16)
        if custom.detect_chipset() == custom.CHIPSET::AGA {
            custom.BPLCON4 = $0011
        }
        draw_starburst()
        gfxtext.print(20, 20, iso:"Beachball sprites, AGA 64x64", 1)
        blitter.wait()
        sprites.enable()

        repeat {
            custom.waitvsync()
            for i in 0 to NUM_BALLS-1 {
                ^^mobs.Mob m = even_balls[i].mob
                m.x_pos += vx[i]
                if m.x_pos < 0 {
                    m.x_pos = 0
                    vx[i] = -vx[i]
                } else if m.x_pos > MAX_X {
                    m.x_pos = MAX_X
                    vx[i] = -vx[i]
                }
                m.y_pos += vy[i]
                if m.y_pos < 0 {
                    m.y_pos = 0
                    vy[i] = -vy[i]
                } else if m.y_pos > MAX_Y {
                    m.y_pos = MAX_Y
                    vy[i] = -vy[i]
                }
                sprites.aga_pos_attached(&even_balls[i], &odd_balls[i])
            }
            bool pressed
            ubyte keycode
            pressed, keycode = custom.raw_keyboard_poll()
            if pressed and (keycode & $80)==0 and keycode==custom.KeyCode::ESC
                break
        }
        custom.restore_system()
        playfield.destroy()
    }

    sub prepare_balls() {
        math.rndseed(custom.JOY0DAT, custom.VHPOSR)
        for i in 0 to NUM_BALLS-1 {
            even_balls[i].mob = &mobdata[i]
            odd_balls[i].mob = &mobdata[i]
            even_balls[i].slot = i * 2
            ^^mobs.Mob m = even_balls[i].mob
            m.width = BALL_WIDTH
            m.height = BALL_HEIGHT
            m.origin_x = 0
            m.origin_y = 0
            m.x_pos = math.randrangew((MAX_X+1 as uword)) as word
            m.y_pos = math.randrangew((MAX_Y+1 as uword)) as word
            vx[i] = (math.randrangew(5) as word)-2
            if vx[i] == 0 { vx[i] = 1 }
            vy[i] = (math.randrangew(5) as word)-2
            if vy[i] == 0 { vy[i] = -1 }
            uword base = (i as uword) * SPRITE_WORDS
            even_balls[i].data = &spritedata.sprite_even_buffer[base]
            odd_balls[i].data = &spritedata.sprite_odd_buffer[base]
            for j in 0 to SPRITE_WORDS-1 {
                spritedata.sprite_even_buffer[base + j] = spritedata.sprite_even_template[j]
                spritedata.sprite_odd_buffer[base + j] = spritedata.sprite_odd_template[j]
            }
        }
    }

    sub build_copperlist() -> uword {
        copper.init(&copperdata.copper_list, len(copperdata.copper_list))
        void copper.move($0096, $83e0)
        uword base = copper.base_list(regs, DEPTH)
        copper.end()
        return base
    }

    sub draw_starburst() {
        blitter.line_init($ffff, $8000, $ffff, ROW_BYTES)
        for i in 0 to 127 {
            word dx = math.cos8(i*2)
            word dy = math.sin8(i*2)
            word x2 = WIDTH/2 + dx*STAR_RADIUS/127
            word y2 = HEIGHT/2 + dy*STAR_RADIUS/127
            blitter.line_color(WIDTH/2, HEIGHT/2, x2 as uword, y2 as uword, i/8, DEPTH, &bm.Planes)
        }
    }
}

copperdata {
    %option amiga_chipram
    uword[96] copper_list
}

spritedata {
    %option amiga_chipram
    uword[main.SPRITE_WORDS] sprite_even_template = [
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $00ff, $0000, $ff00, $0000, $0000, $0000,
        $0000, $0000, $07ff, $0000, $ffe0, $0000, $0000, $0000,
        $0000, $0000, $1f00, $0000, $00f8, $0000, $0000, $0000,
        $0000, $0000, $7800, $0000, $001e, $0000, $0000, $0000,
        $0001, $0000, $e000, $0000, $0007, $0000, $8000, $0000,
        $0003, $0000, $8000, $0000, $0001, $0000, $c000, $0000,
        $000f, $0000, $c000, $c000, $0003, $0000, $f000, $0000,
        $001f, $0003, $c000, $c000, $0003, $0000, $f800, $0000,
        $003f, $0007, $e000, $e000, $0007, $0000, $fc00, $0000,
        $007f, $000f, $f000, $f000, $000f, $0000, $fe00, $0000,
        $00ff, $001f, $f000, $f01f, $000f, $f800, $ff00, $0000,
        $01ff, $003f, $f800, $f8ff, $001f, $ff00, $ff80, $0000,
        $01ff, $007f, $f800, $fbff, $001f, $ffc0, $ff80, $0000,
        $03ff, $00ff, $fc00, $f3ff, $003f, $fff0, $ffc0, $0000,
        $07ff, $00ff, $fc00, $e3ff, $003f, $fff8, $ffe0, $0000,
        $07ff, $01ff, $fe00, $c1ff, $007f, $fffc, $ffe0, $0000,
        $0fff, $03ff, $ff00, $80ff, $00ff, $fffe, $fff0, $0000,
        $0fff, $03ff, $ff00, $00ff, $00ff, $ffff, $fff0, $0000,
        $1fff, $07fe, $ff80, $007f, $01ff, $ffff, $fff8, $8000,
        $1fff, $07fc, $ff80, $007f, $01ff, $ffff, $fff8, $c000,
        $3fff, $0ffc, $ffc0, $003f, $03ff, $ffff, $fffc, $c000,
        $3fff, $0ff8, $ffe0, $001f, $07ff, $ffff, $fffc, $e000,
        $3fff, $0ff8, $ffe0, $001f, $07ff, $ffff, $fffc, $e000,
        $7fff, $1ff0, $ff80, $007f, $01ff, $ffff, $fffe, $f000,
        $7fff, $1ff0, $ff00, $00ff, $00ff, $ffff, $fffe, $f000,
        $7fff, $1ff0, $ff00, $00ff, $00ff, $ffff, $fffe, $f000,
        $7fff, $1fe0, $fe00, $01ff, $007f, $ffff, $fffe, $f800,
        $7fff, $1fe0, $fe00, $01ff, $007f, $ffff, $fffe, $f800,
        $7fff, $1fe0, $fc00, $03ff, $003f, $ffff, $fffe, $f800,
        $7fff, $1fe0, $fc00, $03ff, $003f, $ffff, $fffe, $f800,
        $7fff, $1fe0, $fc00, $03ff, $003f, $ffff, $fffe, $f800,
        $6000, $001f, $0000, $ffff, $0000, $ffc0, $0006, $07f8,
        $6000, $001f, $0000, $ffff, $0000, $ffc0, $0006, $07f8,
        $6000, $001f, $0000, $ffff, $0000, $ffc0, $0006, $07f8,
        $6000, $001f, $0000, $ffff, $0000, $ff80, $0006, $07f8,
        $6000, $001f, $0000, $ffff, $0000, $ff80, $0006, $07f8,
        $6000, $000f, $0000, $ffff, $0000, $ff00, $0006, $0ff8,
        $6000, $000f, $0000, $ffff, $0000, $ff00, $0006, $0ff8,
        $6000, $000f, $0000, $ffff, $0000, $fe00, $0006, $0ff8,
        $3000, $0007, $0000, $ffff, $0000, $f800, $000c, $1ff0,
        $3000, $0007, $0018, $ffe7, $1800, $e000, $000c, $1ff0,
        $3000, $0003, $003f, $ffc0, $fc00, $0000, $000c, $3ff0,
        $1800, $0003, $007f, $ff80, $fe00, $0000, $0018, $3fe0,
        $1800, $0001, $007f, $ff80, $fe00, $0000, $0018, $7fe0,
        $0c00, $0000, $00ff, $ff00, $ff00, $0000, $0030, $ffc0,
        $0c00, $0000, $00ff, $7f00, $ff00, $0001, $0030, $ffc0,
        $0600, $0000, $01ff, $3e00, $ff80, $0003, $0060, $ff80,
        $0700, $0000, $03ff, $1c00, $ffc0, $0007, $00e0, $ff00,
        $0300, $0000, $03ff, $0c00, $ffc0, $000f, $00c0, $ff00,
        $0180, $0000, $07ff, $0400, $ffe0, $003f, $0180, $fe00,
        $01c0, $0000, $07ff, $0700, $ffe0, $00ff, $0380, $fc00,
        $00e0, $0000, $0fff, $0fe0, $fff0, $07ff, $0700, $f800,
        $0070, $0000, $0fff, $0fff, $fff0, $ffff, $0e00, $f000,
        $0038, $0000, $1fff, $1fff, $fff8, $ffff, $1c00, $e000,
        $001c, $0000, $3fff, $3fff, $fffc, $ffff, $3800, $c000,
        $000f, $0000, $3fff, $3fff, $fffc, $ffff, $f000, $0000,
        $0003, $0000, $ffff, $7fff, $ffff, $fffe, $c000, $0000,
        $0001, $0000, $ffff, $1fff, $ffff, $fff8, $8000, $0000,
        $0000, $0000, $7fff, $07ff, $fffe, $ffe0, $0000, $0000,
        $0000, $0000, $1fff, $00ff, $fff8, $ff00, $0000, $0000,
        $0000, $0000, $07ff, $0000, $ffe0, $0000, $0000, $0000,
        $0000, $0000, $00ff, $0000, $ff00, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
    ]
    uword[main.SPRITE_WORDS] sprite_odd_template = [
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $00ff, $00ff, $ff00, $ff00, $0000, $0000,
        $0000, $0000, $07ff, $07ff, $ffe0, $ffe0, $0000, $0000,
        $0000, $0000, $1f00, $1fff, $00f8, $fff8, $0000, $0000,
        $0000, $0000, $7800, $7fff, $001e, $fffe, $0000, $0000,
        $0001, $0001, $e000, $ffff, $0007, $ffff, $8000, $8000,
        $0003, $0003, $8000, $ffff, $0001, $ffff, $c000, $c000,
        $000f, $000f, $c000, $3fff, $0000, $ffff, $f000, $f000,
        $001f, $001c, $c000, $3fff, $0000, $ffff, $3800, $f800,
        $003f, $0038, $e000, $1fff, $0000, $ffff, $1c00, $fc00,
        $007f, $0070, $f000, $0fff, $0000, $ffff, $0e00, $fe00,
        $00ff, $00e0, $f000, $0fe0, $0000, $07ff, $0700, $ff00,
        $01ff, $01c0, $f800, $0700, $0000, $00ff, $0380, $ff80,
        $01ff, $0180, $f800, $0400, $0000, $003f, $0180, $ff80,
        $03ff, $0300, $f000, $0000, $0000, $000f, $00c0, $ffc0,
        $07ff, $0700, $e000, $0000, $0000, $0007, $00e0, $ffe0,
        $07ff, $0600, $c000, $0000, $0000, $0003, $0060, $ffe0,
        $0fff, $0c00, $8000, $0000, $0000, $0001, $0030, $fff0,
        $0fff, $0c00, $0000, $0000, $0000, $0000, $0030, $fff0,
        $1ffe, $1800, $0000, $0000, $0000, $0000, $0018, $7ff8,
        $1ffc, $1800, $0000, $0000, $0000, $0000, $0018, $3ff8,
        $3ffc, $3000, $0000, $0000, $0000, $0000, $000c, $3ffc,
        $3ff8, $3000, $0007, $0007, $e000, $e000, $000c, $1ffc,
        $3ff8, $3000, $001f, $001f, $f800, $f800, $000c, $1ffc,
        $7ff0, $6000, $007f, $007f, $fe00, $fe00, $0006, $0ffe,
        $7ff0, $6000, $00ff, $00ff, $ff00, $ff00, $0006, $0ffe,
        $7ff0, $6000, $00ff, $00ff, $ff00, $ff00, $0006, $0ffe,
        $7fe0, $6000, $01ff, $01ff, $ff80, $ff80, $0006, $07fe,
        $7fe0, $6000, $01ff, $01ff, $ff80, $ff80, $0006, $07fe,
        $7fe0, $6000, $03ff, $03ff, $ffc0, $ffc0, $0006, $07fe,
        $7fe0, $6000, $03ff, $03ff, $ffc0, $ffc0, $0006, $07fe,
        $7fe0, $6000, $03ff, $03ff, $ffc0, $ffc0, $0006, $07fe,
        $7fff, $7fe0, $ffff, $03ff, $ffff, $ffc0, $f806, $07fe,
        $7fff, $7fe0, $ffff, $03ff, $ffff, $ffc0, $f806, $07fe,
        $7fff, $7fe0, $ffff, $03ff, $ffff, $ffc0, $f806, $07fe,
        $7fff, $7fe0, $ffff, $01ff, $ffff, $ff80, $f806, $07fe,
        $7fff, $7fe0, $ffff, $01ff, $ffff, $ff80, $f806, $07fe,
        $7fff, $7ff0, $ffff, $00ff, $ffff, $ff00, $f006, $0ffe,
        $7fff, $7ff0, $ffff, $00ff, $ffff, $ff00, $f006, $0ffe,
        $7fff, $7ff0, $ffff, $007f, $ffff, $fe00, $f006, $0ffe,
        $3fff, $3ff8, $ffff, $001f, $ffff, $f800, $e00c, $1ffc,
        $3fff, $3ff8, $ffff, $0007, $ffff, $e000, $e00c, $1ffc,
        $3fff, $3ffc, $ffff, $0000, $ffff, $0000, $c00c, $3ffc,
        $1fff, $1ffc, $ffff, $0000, $ffff, $0000, $c018, $3ff8,
        $1fff, $1ffe, $ffff, $0000, $ffff, $0000, $8018, $7ff8,
        $0fff, $0fff, $ffff, $0000, $ffff, $0000, $0030, $fff0,
        $0fff, $0fff, $ffff, $8000, $fffe, $0001, $0030, $fff0,
        $07ff, $07ff, $ffff, $c000, $fffc, $0003, $0060, $ffe0,
        $07ff, $07ff, $ffff, $e000, $fff8, $0007, $00e0, $ffe0,
        $03ff, $03ff, $ffff, $f000, $fff0, $000f, $00c0, $ffc0,
        $01ff, $01ff, $fbff, $fc00, $ffc0, $003f, $0180, $ff80,
        $01ff, $01ff, $f8ff, $ff00, $ff00, $00ff, $0380, $ff80,
        $00ff, $00ff, $f01f, $ffe0, $f800, $07ff, $0700, $ff00,
        $007f, $007f, $f000, $ffff, $0000, $ffff, $0e00, $fe00,
        $003f, $003f, $e000, $ffff, $0000, $ffff, $1c00, $fc00,
        $001f, $001f, $c000, $ffff, $0000, $ffff, $3800, $f800,
        $000f, $000f, $c000, $ffff, $0000, $ffff, $f000, $f000,
        $0003, $0003, $8000, $ffff, $0001, $ffff, $c000, $c000,
        $0001, $0001, $e000, $ffff, $0007, $ffff, $8000, $8000,
        $0000, $0000, $7800, $7fff, $001e, $fffe, $0000, $0000,
        $0000, $0000, $1f00, $1fff, $00f8, $fff8, $0000, $0000,
        $0000, $0000, $07ff, $07ff, $ffe0, $ffe0, $0000, $0000,
        $0000, $0000, $00ff, $00ff, $ff00, $ff00, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
    ]
    uword[main.SPRITE_WORDS*4] @align64 sprite_even_buffer
    uword[main.SPRITE_WORDS*4] @align64 sprite_odd_buffer
}
