; OCS/ECS hardware-sprite beachballs (16 colors via attached sprites).
; Compile with `-target amiga500` or `-target amiga1200`.
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
    const ubyte BALL_WIDTH = 16
    const ubyte BALL_HEIGHT = 16
    ; 2 control words + 2 bitplane words per line (attached 2-plane format).
    const uword SPRITE_WORDS = 2 + (BALL_HEIGHT as uword) * 2
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
        prepare_balls()
        copperPlaneBase = build_copperlist()
        playfield.patch_copper(&copperdata.copper_list, copperPlaneBase)

        gfxtext.init(ROW_BYTES, DEPTH, &bm.Planes)

        custom.grab_system(true)
        custom.waitvsync()
        for i in 0 to NUM_BALLS-1 {
            sprites.init_attached(&even_balls[i], &odd_balls[i])
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
        gfxtext.print(20, 20, iso:"Beachball sprites, OCS 16 colors", 1)
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
                sprites.pos_attached(&even_balls[i], &odd_balls[i])
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
        $0000, $0000,
        $07e0, $0000,
        $1818, $0000,
        $381c, $0800,
        $781e, $1fe0,
        $7c3e, $33f0,
        $fe7f, $61f8,
        $fc3f, $63f8,
        $f81f, $67f8,
        $8001, $1fe6,
        $8001, $1fc6,
        $8001, $1f86,
        $43c2, $0c0c,
        $67e6, $0018,
        $37ec, $07f0,
        $1ff8, $07e0,
        $07e0, $0000,
    ]
    uword[main.SPRITE_WORDS] sprite_odd_template = [
        $0000, $0000,
        $07e0, $07e0,
        $1818, $1ff8,
        $380c, $37fc,
        $7806, $601e,
        $7002, $400e,
        $e181, $8187,
        $e3c1, $83c7,
        $e7e1, $87e7,
        $fff9, $e7e7,
        $fff9, $e3c7,
        $fff9, $e187,
        $7ff2, $700e,
        $7fe6, $781e,
        $380c, $3ffc,
        $1818, $1ff8,
        $07e0, $07e0,
    ]
    uword[main.SPRITE_WORDS*4] sprite_even_buffer
    uword[main.SPRITE_WORDS*4] sprite_odd_buffer
}
