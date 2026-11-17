; AGA hardware-sprite beachballs (16 colors via attached sprites).
; 64x64 pixel AGA sprites (64px wide, 64 high). Amiga 1200 +

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
        $050710, $fff3dc, $aebfd2, $56647c, $d94942, $f47d68, $c8892d, $f5d04d,
        $28754e, $6bcb86, $2f5eaa, $70b8e5, $71449b, $ce70ba, $364353, $e99056
    ]
    long[16] sprite_palette = [
        $000000, $f1443d, $35c769, $3568e8, $f0d33f, $dc43be, $37c9d5, $912a2b,
        $1c7849, $293f99, $937522, $832f7c, $1b7781, $09101b, $fff8e4, $aebccd
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
        ; 64px sprite fetch bits (FMODE 3-2) and ECSENA (gates the BPLCON3
        ; sprite resolution bits) in the values it writes.
        regs.fmode = regs.fmode | %1100
        regs.bplcon0 = regs.bplcon0 | $0001   ; ECSENA gates the BPLCON3 sprite resolution bits
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
        sprites.aga_set_colors16(&sprite_palette as ^^long)
        custom.load_colors(&palette, 16)
        if custom.detect_chipset() == custom.CHIPSET::AGA {
            ; BPLCON3/BPLCON4 are write-only on AGA and still hold the OS
            ; screen's values here: reset the sprite resolution (no BRDSPRT)
            ; and the sprite palette banks through the sprites module, which
            ; composes the register values without reading them back.
            sprites.aga_set_resolution(0)
            sprites.aga_set_bank(1, 1)
        }
        draw_starburst()
        gfxtext.print(20, 20, iso:"Beachball sprites, AGA 64x64", 1)
        blitter.wait()
        sprites.enable()

        repeat {
            custom.waitvsync()
            bounce()
            bool pressed
            ubyte keycode
            pressed, keycode = custom.raw_keyboard_poll()
            if pressed and (keycode & $80)==0 and keycode==custom.KeyCode::ESC
                break
        }
        custom.restore_system()
        playfield.destroy()
    }

    sub bounce() {
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
            blitter.line_color(WIDTH/2, HEIGHT/2, x2 as uword, y2 as uword, 1+i/9, DEPTH, &bm.Planes)
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
        $0000, $003f, $fc00, $0000, $0000, $0000, $0000, $0000,
        $0000, $03c8, $13c0, $0000, $0000, $0037, $ec00, $0000,
        $0000, $0c02, $4030, $0000, $0000, $03fd, $bfc0, $0000,
        $0000, $3000, $00ac, $0000, $0000, $0fff, $fff0, $0000,
        $0000, $c080, $0103, $0000, $0000, $077f, $fee8, $0000,
        $0003, $0800, $0030, $c000, $0000, $27ff, $ffe5, $0000,
        $0004, $3800, $001c, $2000, $0000, $07ff, $ffea, $8000,
        $0008, $f808, $109f, $1000, $0000, $0ff7, $eff4, $0000,
        $0013, $fc10, $083f, $c800, $0000, $03ef, $f7ea, $a000,
        $002f, $ff20, $04ff, $f400, $0002, $22df, $fb55, $4000,
        $005f, $ffe0, $07ff, $fa00, $0000, $001f, $fbee, $a000,
        $00bf, $ffff, $ffff, $fd00, $0008, $888a, $a955, $1000,
        $017f, $ffff, $ffff, $fe80, $0000, $0000, $04aa, $a800,
        $02ff, $ffff, $ffff, $ff40, $0022, $2222, $aa55, $4000,
        $03ff, $ffff, $ffff, $ffc0, $0000, $0000, $016a, $aa00,
        $07ff, $ffff, $ffff, $ffe0, $0088, $8aaa, $aa55, $0000,
        $07ff, $ffff, $ffff, $ffe0, $0000, $0000, $442a, $aa00,
        $0fff, $ffff, $ffff, $fff0, $0222, $202a, $aa95, $4000,
        $0fff, $ffff, $ffff, $fff0, $0000, $0000, $110a, $aa80,
        $1fff, $ffff, $ffff, $fff8, $0aaa, $aaaa, $aa91, $0000,
        $1fff, $ffff, $ffff, $fff8, $0000, $0004, $454a, $aa00,
        $3fff, $ffff, $ffff, $fffc, $0aa2, $a2aa, $aa84, $4000,
        $3fff, $ffff, $ffff, $fffc, $0000, $0001, $1142, $a880,
        $3fff, $ffff, $ffff, $fffc, $0aaa, $a2aa, $aae0, $0000,
        $3fff, $ffff, $ffff, $fffc, $0400, $0444, $5542, $a200,
        $7fff, $ffff, $ffff, $fffe, $2aaa, $8aaa, $aab0, $0000,
        $7fff, $ffff, $ffff, $fffe, $1000, $0111, $5552, $a800,
        $7fff, $ffff, $ffff, $fffe, $2aaa, $8aaa, $aef0, $0000,
        $7fff, $ffff, $ffff, $fffe, $1444, $0455, $5550, $2000,
        $7fff, $ffff, $ffff, $fffe, $2aaa, $8aaa, $abb8, $0000,
        $7fff, $ffff, $ffff, $fffe, $1111, $1115, $5550, $8800,
        $7fff, $ffff, $ffff, $fffe, $0aaa, $8aaa, $eff8, $0000,
        $5fff, $ffff, $ffff, $fffe, $2555, $1555, $5558, $0004,
        $6fff, $ffff, $ffff, $fffe, $32aa, $0aab, $bbf8, $000c,
        $57ff, $ffff, $ffff, $fffe, $3955, $1555, $5554, $801c,
        $6bff, $ffff, $ffff, $fffe, $3caa, $2eee, $fffc, $003c,
        $55ff, $ffff, $ffff, $fffe, $3f15, $1555, $55dc, $00fc,
        $2aff, $ffff, $ffff, $fffc, $1f8a, $2bbb, $fffc, $01f8,
        $351f, $ffff, $ffff, $fffc, $1ff1, $1555, $5574, $0ff8,
        $2eab, $ffff, $ffff, $fffc, $1ffc, $2fff, $fffc, $3ff8,
        $3555, $ffff, $ffff, $fffc, $0ffe, $0055, $dd00, $7ff0,
        $1aab, $ebff, $ffff, $fff8, $07fe, $3e00, $007c, $7fe0,
        $1755, $d555, $577f, $ffe8, $09fe, $3fff, $fffc, $7f90,
        $09ef, $efff, $ffff, $ff90, $00fe, $3fff, $fffc, $7f60,
        $08d5, $d555, $dfff, $ff10, $023e, $3fff, $fffc, $7ce0,
        $043b, $ffff, $ffff, $fc20, $0046, $3fff, $fffc, $63c0,
        $0407, $d557, $77ff, $e020, $02a8, $3fff, $fffc, $1740,
        $0200, $ffff, $ffff, $0040, $0000, $0fff, $fff0, $ff80,
        $0200, $cfdf, $fff3, $0040, $002a, $203f, $fc00, $ff80,
        $0100, $c01f, $f803, $0080, $0000, $0000, $0000, $ff00,
        $0080, $c000, $0003, $0100, $0008, $0888, $8888, $7600,
        $0040, $c000, $0003, $0200, $0000, $0000, $0000, $fc00,
        $0020, $6000, $0006, $0400, $0000, $0000, $0001, $f800,
        $0010, $6000, $0006, $0800, $0000, $0000, $0001, $f000,
        $0008, $6000, $0006, $1000, $0000, $8888, $8889, $6000,
        $0004, $2000, $0004, $2000, $0000, $0000, $0003, $c000,
        $0003, $3000, $000c, $c000, $0000, $0000, $0003, $0000,
        $0000, $d000, $000b, $0000, $0000, $0000, $0004, $0000,
        $0000, $3800, $001c, $0000, $0000, $0088, $8880, $0000,
        $0000, $0c00, $0030, $0000, $0000, $0000, $0000, $0000,
        $0000, $03c0, $03c0, $0000, $0000, $0000, $0000, $0000,
        $0000, $003f, $fc00, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
    ]
    uword[main.SPRITE_WORDS] sprite_odd_template = [
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $003f, $fc00, $0000, $0000, $003f, $fc00, $0000,
        $0000, $03ff, $ffc0, $0000, $0000, $03ff, $ffc0, $0000,
        $0000, $0fff, $fff0, $0000, $0000, $0fff, $fff0, $0000,
        $0000, $37ff, $fffc, $0000, $0000, $3fff, $ffec, $0000,
        $0000, $ffff, $ffff, $0000, $0000, $c7ff, $fff7, $0000,
        $0003, $dfff, $ffff, $c000, $0003, $2fff, $fffa, $c000,
        $0007, $e7ff, $ffe7, $e000, $0004, $27ff, $fff5, $6000,
        $000f, $8fff, $ffe1, $f000, $0008, $87ff, $ffeb, $f000,
        $001e, $03ff, $ffc0, $7800, $0012, $03ff, $ffd5, $5800,
        $003a, $22ff, $ff00, $1c00, $0028, $00ff, $ffaa, $bc00,
        $0070, $003f, $fc00, $0e00, $0050, $003f, $fc11, $5e00,
        $00e8, $88f5, $5600, $0700, $00a0, $004a, $aaaa, $ef00,
        $01c0, $00ff, $fb00, $0380, $0140, $0080, $0555, $5780,
        $03a2, $22dd, $5500, $01c0, $0280, $00a2, $abaa, $bfc0,
        $0300, $01ff, $fe80, $00c0, $0300, $0100, $0195, $55c0,
        $0688, $8b55, $5580, $0060, $0600, $01aa, $aaaa, $ffe0,
        $0400, $03ff, $bbc0, $0020, $0400, $0300, $44d5, $55e0,
        $0a22, $23d5, $5540, $0010, $0800, $022a, $aaea, $bff0,
        $0800, $07ff, $eee0, $0010, $0800, $0600, $1175, $5570,
        $1aaa, $ad55, $5560, $0008, $1000, $06aa, $aaae, $fff8,
        $1000, $0ffb, $bab0, $0008, $1000, $0c04, $4575, $55f8,
        $2aa2, $ad55, $5570, $0004, $2000, $0eaa, $aabb, $bffc,
        $2000, $1ffe, $eeb8, $0004, $2000, $1801, $115d, $577c,
        $2aaa, $bd55, $5518, $0004, $2000, $1aaa, $aaff, $fffc,
        $2400, $1bbb, $aab8, $0004, $2000, $1c44, $555d, $5dfc,
        $6aaa, $b555, $554c, $0002, $4000, $3aaa, $aabf, $fffe,
        $5000, $3eee, $aaac, $0002, $4000, $3111, $555d, $57fe,
        $6aaa, $b555, $510c, $0002, $4000, $3aaa, $aeff, $fffe,
        $5444, $7baa, $aaae, $0002, $4000, $7455, $555f, $dffe,
        $6aaa, $f555, $5446, $0002, $4000, $6aaa, $abbf, $fffe,
        $5111, $6eea, $aaae, $0002, $4000, $7115, $5557, $77fe,
        $6aaa, $f555, $1006, $0006, $6000, $6aaa, $efff, $fffe,
        $7555, $6aaa, $aaa6, $000e, $7000, $7555, $555f, $fffe,
        $7aaa, $f554, $4407, $001e, $7800, $eaab, $bbff, $fffe,
        $7d55, $eaaa, $aaab, $003e, $7c00, $d555, $5557, $7ffe,
        $7faa, $d111, $0003, $00fe, $7f00, $eeee, $ffff, $fffe,
        $7fd5, $eaaa, $aa23, $03fe, $7fc0, $d555, $55df, $fffe,
        $3ffa, $d444, $0003, $0ffc, $3ff0, $ebbb, $ffff, $fffc,
        $3fff, $eaaa, $aa8b, $7ffc, $3ffe, $d555, $5577, $fffc,
        $3fff, $d000, $0003, $fffc, $3fff, $efff, $ffff, $fffc,
        $3fff, $ffaa, $22ff, $fffc, $3fff, $ff55, $ddff, $fffc,
        $1fff, $ffff, $ffff, $fff8, $1fff, $ffff, $ffff, $fff8,
        $1fff, $ffff, $ffff, $ffe8, $17ff, $ffff, $ffff, $fff8,
        $0fff, $ffff, $ffff, $ff90, $0fff, $ffff, $ffff, $fff0,
        $0fff, $ffff, $ffff, $ff10, $0dff, $ffff, $ffff, $fff0,
        $07ff, $ffff, $ffff, $fc20, $07bf, $ffff, $ffff, $ffe0,
        $07ff, $ffff, $ffff, $e8a0, $0557, $ffff, $ffff, $f760,
        $03ff, $ffff, $ffff, $0040, $03ff, $ffff, $ffff, $ffc0,
        $03ff, $cfff, $fff3, $0040, $03d5, $dfff, $ffff, $ffc0,
        $01ff, $c01f, $f803, $0080, $01ff, $ffff, $ffff, $ff80,
        $00ff, $c000, $0003, $8900, $00f7, $f777, $7777, $7700,
        $007f, $c000, $0003, $0200, $007f, $ffff, $ffff, $fe00,
        $003f, $e000, $0006, $0400, $003f, $ffff, $ffff, $fc00,
        $001f, $e000, $0006, $0800, $001f, $ffff, $ffff, $f800,
        $000f, $e000, $0006, $9000, $000f, $7777, $7777, $7000,
        $0007, $e000, $0004, $2000, $0007, $ffff, $ffff, $e000,
        $0003, $f000, $000c, $c000, $0003, $ffff, $ffff, $c000,
        $0000, $f000, $000b, $0000, $0000, $ffff, $ffff, $0000,
        $0000, $3800, $001c, $0000, $0000, $3f77, $777c, $0000,
        $0000, $0c00, $0030, $0000, $0000, $0fff, $fff0, $0000,
        $0000, $03c0, $03c0, $0000, $0000, $03ff, $ffc0, $0000,
        $0000, $003f, $fc00, $0000, $0000, $003f, $fc00, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
        $0000, $0000, $0000, $0000, $0000, $0000, $0000, $0000,
    ]
    uword[main.SPRITE_WORDS*4] @align64 sprite_even_buffer
    uword[main.SPRITE_WORDS*4] @align64 sprite_odd_buffer
}
