; OCS/ECS hardware-sprite beachballs over the blitter starburst bitmap.
; Compile with `-target amiga500` or `-target amiga1200`.

%import graphics
%import custom
%import playfield
%import sprites
%import mobs
%import copper
%import blitter
%import math
%import textio

main {
    const word WIDTH = 320
    const word HEIGHT = 256
    const ubyte DEPTH = 4
    const ubyte NUM_BALLS = 8
    const ubyte BALL_WIDTH = 16
    const ubyte BALL_HEIGHT = 16
    const word MAX_X = WIDTH-BALL_WIDTH
    const word MAX_Y = HEIGHT-BALL_HEIGHT
    const uword ROW_BYTES = 40
    const word STAR_RADIUS = 120

    mobs.Mob[NUM_BALLS] metas
    mobs.Sprite[NUM_BALLS] balls
    word[NUM_BALLS] vx
    word[NUM_BALLS] vy
    long[16] palette = [
        $000000, $ffffff, $c4c4c4, $585858, $7a0000, $ff4040, $7a7a00, $ffe040,
        $006000, $30ff60, $00007a, $6060ff, $600060, $ff50ff, $252525, $ffb37c
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
        prepare_balls()
        copperPlaneBase = build_copperlist()
        playfield.patch_copper(&copperdata.copper_list, copperPlaneBase)

        custom.grab_system(true)
        custom.waitvsync()
        for i in 0 to NUM_BALLS-1 {
            sprites.init(&balls[i])
            sprites.pos(&balls[i])
        }
        custom.COP1LC = &copperdata.copper_list
        custom.DMACON = custom.DMAF_SETCLR | custom.DMAF_MASTER | custom.DMAF_COPPER
        custom.COPJMP1 = 0
        sprites.set_priority(sprites.Priority::PAIRS_4_IN_FRONT)
        custom.load_colors(&palette, 16)
        if custom.detect_chipset() == custom.CHIPSET::AGA {
            ; Restore AGA's default sprite palette banks for ECS-compatible colors.
            custom.BPLCON4 = $0011
        }
        ; OCS/ECS sprite colors are shared by each channel pair.
        custom.COLOR17 = $0f00
        custom.COLOR18 = $0ff0
        custom.COLOR19 = $00f0
        custom.COLOR21 = $0f00
        custom.COLOR22 = $0ff0
        custom.COLOR23 = $00f0
        custom.COLOR25 = $0f00
        custom.COLOR26 = $0ff0
        custom.COLOR27 = $00f0
        custom.COLOR29 = $0f00
        custom.COLOR30 = $0ff0
        custom.COLOR31 = $00f0
        draw_starburst()
        blitter.wait()
        sprites.enable()

        repeat {
            custom.waitvsync()
            for i in 0 to NUM_BALLS-1 {
                metas[i].x_pos += vx[i]
                if metas[i].x_pos < 0 {
                    metas[i].x_pos = 0
                    vx[i] = -vx[i]
                } else if metas[i].x_pos > MAX_X {
                    metas[i].x_pos = MAX_X
                    vx[i] = -vx[i]
                }
                metas[i].y_pos += vy[i]
                if metas[i].y_pos < 0 {
                    metas[i].y_pos = 0
                    vy[i] = -vy[i]
                } else if metas[i].y_pos > MAX_Y {
                    metas[i].y_pos = MAX_Y
                    vy[i] = -vy[i]
                }
                sprites.pos(&balls[i])
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
            metas[i].width = BALL_WIDTH
            metas[i].height = BALL_HEIGHT
            metas[i].origin_x = 0
            metas[i].origin_y = 0
            metas[i].x_pos = math.randrangew((MAX_X+1 as uword)) as word
            metas[i].y_pos = math.randrangew((MAX_Y+1 as uword)) as word
            vx[i] = (math.randrangew(5) as word)-2
            if vx[i] == 0 { vx[i] = 1 }
            vy[i] = (math.randrangew(5) as word)-2
            if vy[i] == 0 { vy[i] = -1 }
            balls[i].mob = &metas[i]
            balls[i].slot = i
        }
        balls[0].data = &spritedata.sprite0
        balls[1].data = &spritedata.sprite1
        balls[2].data = &spritedata.sprite2
        balls[3].data = &spritedata.sprite3
        balls[4].data = &spritedata.sprite4
        balls[5].data = &spritedata.sprite5
        balls[6].data = &spritedata.sprite6
        balls[7].data = &spritedata.sprite7
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
    uword[36] sprite0 = [
        $0000, $0000, $0000, $0000,
        $0ff0, $0ff0, $07f8, $1ff8,
        $07fc, $3ffc, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7f00,
        $01fe, $7e00, $01fe, $7e00,
        $03fe, $7c00, $07fc, $3800,
        $07f8, $1800, $0ff0, $0000,
        $0000, $0000, $0000, $0000
    ]
    uword[36] sprite1 = [
        $0000, $0000, $0000, $0000,
        $0ff0, $0ff0, $07f8, $1ff8,
        $07fc, $3ffc, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7f00,
        $01fe, $7e00, $01fe, $7e00,
        $03fe, $7c00, $07fc, $3800,
        $07f8, $1800, $0ff0, $0000,
        $0000, $0000, $0000, $0000
    ]
    uword[36] sprite2 = [
        $0000, $0000, $0000, $0000,
        $0ff0, $0ff0, $07f8, $1ff8,
        $07fc, $3ffc, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7f00,
        $01fe, $7e00, $01fe, $7e00,
        $03fe, $7c00, $07fc, $3800,
        $07f8, $1800, $0ff0, $0000,
        $0000, $0000, $0000, $0000
    ]
    uword[36] sprite3 = [
        $0000, $0000, $0000, $0000,
        $0ff0, $0ff0, $07f8, $1ff8,
        $07fc, $3ffc, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7f00,
        $01fe, $7e00, $01fe, $7e00,
        $03fe, $7c00, $07fc, $3800,
        $07f8, $1800, $0ff0, $0000,
        $0000, $0000, $0000, $0000
    ]
    uword[36] sprite4 = [
        $0000, $0000, $0000, $0000,
        $0ff0, $0ff0, $07f8, $1ff8,
        $07fc, $3ffc, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7f00,
        $01fe, $7e00, $01fe, $7e00,
        $03fe, $7c00, $07fc, $3800,
        $07f8, $1800, $0ff0, $0000,
        $0000, $0000, $0000, $0000
    ]
    uword[36] sprite5 = [
        $0000, $0000, $0000, $0000,
        $0ff0, $0ff0, $07f8, $1ff8,
        $07fc, $3ffc, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7f00,
        $01fe, $7e00, $01fe, $7e00,
        $03fe, $7c00, $07fc, $3800,
        $07f8, $1800, $0ff0, $0000,
        $0000, $0000, $0000, $0000
    ]
    uword[36] sprite6 = [
        $0000, $0000, $0000, $0000,
        $0ff0, $0ff0, $07f8, $1ff8,
        $07fc, $3ffc, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7f00,
        $01fe, $7e00, $01fe, $7e00,
        $03fe, $7c00, $07fc, $3800,
        $07f8, $1800, $0ff0, $0000,
        $0000, $0000, $0000, $0000
    ]
    uword[36] sprite7 = [
        $0000, $0000, $0000, $0000,
        $0ff0, $0ff0, $07f8, $1ff8,
        $07fc, $3ffc, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7ffe,
        $00fe, $7ffe, $00fe, $7f00,
        $01fe, $7e00, $01fe, $7e00,
        $03fe, $7c00, $07fc, $3800,
        $07f8, $1800, $0ff0, $0000,
        $0000, $0000, $0000, $0000
    ]
}
