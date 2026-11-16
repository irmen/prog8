; Amiga 4-bitplane / 16-colour BOB demo. Runs on OCS/ECS and AGA; compile with
; `-target amiga500` or `-target amiga1200`.
; The BOB is a 32x32 six-gore beach ball (white/red/yellow/green/blue/magenta
; with sphere shading, highlight and black outline, 14 pens).
; Ten 32x32 beach balls bounce diagonally around the screen, one draw_bob()
; call per ball per frame (four bitplane blits per call). Double-buffered with
; per-buffer background save/restore, so no trails and no flicker. Positions
; stay within the 320x256 screen. Press ESC to exit.

%import graphics
%import custom
%import playfield
%import copper
%import blitter
%import mobs
%import math
%import textio

main {
    const word WIDTH = 320
    const word HEIGHT = 256
    const ubyte DEPTH = 4
    const ubyte NUM_COLORS = 16
    const uword ROW_BYTES = 40

    const uword BOB_WIDTH = 32
    const uword BOB_HEIGHT = 32

    const word MAX_X = WIDTH-SAVE_W*16 as word   ; 3 words always fit: 272
    const word MAX_Y = HEIGHT-BOB_HEIGHT as word
    const ubyte NUM_BALLS = 12
    const word STAR_RADIUS = 120

    ; Save/restore covers the Jones max width (3 words), so shifted and
    ; word-aligned draws match the same buffers.
    const uword SAVE_W = 3
    const uword SAVE_MOD = ROW_BYTES-SAVE_W*2
    const uword PLANE_WORDS = SAVE_W*BOB_HEIGHT

    word[NUM_BALLS] vx
    word[NUM_BALLS] vy
    word[NUM_BALLS*2] ox   ; old x per screen buffer: [buf*10+i]
    word[NUM_BALLS*2] oy

    uword copperPlaneBase
    ^^graphics.BitMap drawBm   ; hidden buffer currently drawn into

    mobs.Mob[NUM_BALLS] metas
    mobs.Bob[NUM_BALLS] bobs

    long[NUM_COLORS] palette = [
        $000000, $ffffff, $c4c4c4, $585858, $7a0000, $ff4040, $7a7a00, $ffe040,
        $006000, $30ff60, $00007a, $6060ff, $600060, $ff50ff, $252525, $ffb37c
    ]

    ^^graphics.BitMap bm
    ^^custom.DisplayRegs regs

    sub start() {
        if not playfield.create(WIDTH, HEIGHT, WIDTH, HEIGHT, DEPTH, custom.DISPLAY_DEFAULT, 0) {
            txt.print("Could not create playfield.\n")
            return
        }
        if not playfield.create_backplane() {
            txt.print("Could not create back buffer.\n")
            return
        }
        bm = playfield.bitmap
        regs = playfield.regs
        prepare_bob()
        copperPlaneBase = build_copperlist()
        playfield.patch_copper(&copperdata.copper_list, copperPlaneBase)

        custom.grab_system(true)
        custom.waitvsync()
        custom.COP1LC = &copperdata.copper_list
        custom.DMACON = custom.DMAF_SETCLR | custom.DMAF_MASTER | custom.DMAF_COPPER | custom.DMAF_BLITTER
        custom.COPJMP1 = 0
        custom.load_colors(&palette, NUM_COLORS)
        if custom.detect_chipset() == custom.CHIPSET::AGA {
            custom.BPLCON3 = 0
            custom.BPLCON4 = 0
        }

        draw_starburst()
        copy_front_to_back()
        bounce_balls()
        blitter.wait()

        if custom.detect_chipset() == custom.CHIPSET::AGA {
            custom.FMODE = $0000
        }
        custom.restore_system()
        playfield.destroy()
    }

    sub prepare_bob() {
        ; Seed from mouse counters + beam position so each run scatters differently.
        math.rndseed(custom.JOY0DAT, custom.VHPOSR)
        for i in 0 to NUM_BALLS-1 {
            metas[i].width = BOB_WIDTH
            metas[i].height = BOB_HEIGHT
            metas[i].origin_x = 0
            metas[i].origin_y = 0
            metas[i].x_pos = math.randrangew((MAX_X+1 as uword)) as word   ; 0..272
            metas[i].y_pos = math.randrangew((MAX_Y+1 as uword)) as word   ; 0..224
            vx[i] = (math.randrangew(5) as word) - 2        ; -2..2
            if vx[i]==0 {
                vx[i] = 1
            }
            vy[i] = (math.randrangew(5) as word) - 2
            if vy[i]==0 {
                vy[i] = -1
            }
            ox[i] = metas[i].x_pos
            oy[i] = metas[i].y_pos
            ox[i+NUM_BALLS] = metas[i].x_pos
            oy[i+NUM_BALLS] = metas[i].y_pos
            bobs[i].mob = &metas[i]
            bobs[i].depth = DEPTH
            bobs[i].data = &ballbob.ball_planes
            bobs[i].mask = &ballbob.ball_mask
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
        ; 128 radial lines, pens i/8 (0..15, pen 0 draws nothing visible).
        blitter.line_init($ffff, $8000, $ffff, ROW_BYTES)
        for i in 0 to 127 {
            word dx = math.cos8(i*2)
            word dy = math.sin8(i*2)
            word x2 = (WIDTH/2 as word) + dx*STAR_RADIUS/127
            word y2 = (HEIGHT/2 as word) + dy*STAR_RADIUS/127
            blitter.line_color(WIDTH/2, HEIGHT/2, x2 as uword, y2 as uword, i/8, DEPTH, &bm.Planes)
        }
    }

    sub copy_front_to_back() {
        ; Replicate the starburst background into the back buffer once, so both
        ; buffers start identical before the per-buffer saves are primed.
        ^^graphics.BitMap backBm = playfield.back
        for p in 0 to DEPTH-1 {
            blitter.copy_rect(bm.Planes[p], backBm.Planes[p],
                              ROW_BYTES/2, HEIGHT, 0, 0,
                              blitter.MINTERM_A_ONLY, false)
        }
        blitter.wait()
    }

    sub bounce_balls() {
        ; Double-buffered bounce: restore old, move, save new, draw - all
        ; against the hidden buffer. Flip + re-patch each vsync, restores run
        ; in reverse draw order. ox/oy and save areas are per screen buffer.
        drawBm = playfield.bitmap
        save_all(0)
        save_all(1)
        ubyte buf = 0
        repeat {
            custom.waitvsync()
            playfield.flip_copper(&copperdata.copper_list, copperPlaneBase)
            drawBm = playfield.back
            for i in NUM_BALLS-1 downto 0 {
                restore_one(i, buf)
            }
            for i in 0 to NUM_BALLS-1 {
                metas[i].x_pos += vx[i]
                if metas[i].x_pos < 0 {
                    metas[i].x_pos = 0
                    vx[i] = -vx[i]
                }
                else if metas[i].x_pos > MAX_X {
                    metas[i].x_pos = MAX_X
                    vx[i] = -vx[i]
                }
                metas[i].y_pos += vy[i]
                if metas[i].y_pos < 0 {
                    metas[i].y_pos = 0
                    vy[i] = -vy[i]
                }
                else if metas[i].y_pos > MAX_Y {
                    metas[i].y_pos = MAX_Y
                    vy[i] = -vy[i]
                }
                uword s = oxslot(buf, i)
                ox[s] = metas[i].x_pos
                oy[s] = metas[i].y_pos
            }
            save_all(buf)
            for i in 0 to NUM_BALLS-1 {
                blitter.draw_bob(&bobs[i], &drawBm.Planes, ROW_BYTES)
            }
            ; No wait here: the next blit call waits on entry, so the last
            ; draw overlaps the key poll and vsync below.
            bool pressed
            ubyte keycode
            pressed, keycode = custom.raw_keyboard_poll()
            if pressed and (keycode & $80)==0 and keycode==custom.KeyCode::ESC
                break
            buf ^= 1
        }
    }

    inline sub oxslot(ubyte buf, ubyte i) -> uword {
        ; Index into the per-buffer ox/oy arrays.
        return (buf as uword)*NUM_BALLS + (i as uword)
    }

    sub plane_dst(ubyte i, ubyte p, ubyte buf) -> pointer {
        ; Destination word for ball i on plane p in the hidden buffer,
        ; at that buffer's saved (ox, oy).
        uword s = oxslot(buf, i)
        uword dstoff = (oy[s] as uword)*ROW_BYTES + ((ox[s]/16) as uword)*2
        return drawBm.Planes[p] + dstoff
    }

    sub save_slot(ubyte i, ubyte p, ubyte buf) -> uword {
        ; Word offset of ball i, plane p, buffer buf inside the save area.
        return (((buf as uword)*NUM_BALLS + (i as uword))*4 + (p as uword))*PLANE_WORDS
    }

    sub restore_one(ubyte i, ubyte buf) {
        for p in 0 to DEPTH-1 {
            blitter.copy_rect(&bobbuffers.save[save_slot(i, p, buf)], plane_dst(i, p, buf),
                              SAVE_W, BOB_HEIGHT, 0, SAVE_MOD,
                              blitter.MINTERM_A_ONLY, false)
        }
    }

    sub save_all(ubyte buf) {
        for i in 0 to NUM_BALLS-1 {
            for p in 0 to DEPTH-1 {
                blitter.copy_rect(plane_dst(i, p, buf), &bobbuffers.save[save_slot(i, p, buf)],
                                  SAVE_W, BOB_HEIGHT, SAVE_MOD, 0,
                                  blitter.MINTERM_A_ONLY, false)
            }
        }
    }
}

ballbob {
    %option amiga_chipram

    ; 32x32 beach ball: four planes + mask, two words per row.
    ; Converted from freesvg.org #13665 (CC0).
    %align 4
ball_mask:
    %asmbinary "ball_mask.bin"

    %align 4
ball_planes:
    %asmbinary "ball.bin"
}

bobbuffers {
    %option amiga_chipram

    ; 2 screen buffers x 10 balls x 4 planes x (3 words x 32 rows).
    uword[2 * main.NUM_BALLS * main.DEPTH * main.SAVE_W * main.BOB_HEIGHT] save
}

copperdata {
    %option amiga_chipram

    uword[96] copper_list
}
