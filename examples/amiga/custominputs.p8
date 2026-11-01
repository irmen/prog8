; Single 320x256 lores bitplane display, following the setup in 3d.p8.
; The plane is filled with an alternating-bit test pattern.
; Press ESC to restore the system and exit.

%import custom
%import exec
%import blitter

main {
    const uword SCREEN_WIDTH = 320
    const uword SCREEN_HEIGHT = 256
    const uword SPRITE_SIZE = 16
    const ubyte MOUSE_SCALE_DIVISOR = 2
    const ubyte SPRITE_RED = 0
    const ubyte SPRITE_MOUSE_LEFT = 1
    const ubyte SPRITE_MOUSE_MIDDLE = 2
    const ubyte SPRITE_MOUSE_RIGHT = 3
    const ubyte SPRITE_JOY_FIRE = 4
    const ubyte SPRITE_JOY_FIRE2 = 5

    ; Keyboard routines are provided by shared_amiga_custom (custom.raw_keyboard_poll, custom.key_pressed, custom.KeyCode).

    sub set_sprite_position(word x, word y) {
        ; Convert visible-screen coordinates to the sprite's beam coordinates.
        uword hstart = (x as uword) + 128
        uword vstart = (y as uword) + 44
        uword vstop = vstart + SPRITE_SIZE
        spritedata.sprite[0] = ((vstart & $00ff) << 8) | ((hstart >> 1) & $00ff)
        spritedata.sprite[1] = ((vstop & $00ff) << 8) | ((vstart & $0100) >> 6) | ((vstop & $0100) >> 7) | ((hstart & 1) << 7)
    }

    sub set_sprite_pointers() {
        ; These hardware pointers advance during sprite DMA, so restore all eight each field.
        custom.SPR0PT = &spritedata.sprite
        custom.SPR1PT = &spritedata.inactive_sprite
        custom.SPR2PT = &spritedata.inactive_sprite
        custom.SPR3PT = &spritedata.inactive_sprite
        custom.SPR4PT = &spritedata.inactive_sprite
        custom.SPR5PT = &spritedata.inactive_sprite
        custom.SPR6PT = &spritedata.inactive_sprite
        custom.SPR7PT = &spritedata.inactive_sprite
    }

    sub set_sprite_color(ubyte color) {
        ; Sprite colors are written directly by the CPU.
        uword shade1 = $0600
        uword shade2 = $0a00
        uword shade3 = $0f00
        when color {
            SPRITE_MOUSE_LEFT -> {
                shade1 = $0804
                shade2 = $0c06
                shade3 = $0f0a
            }
            SPRITE_MOUSE_MIDDLE -> {
                shade1 = $0004
                shade2 = $000b
                shade3 = $000f
            }
            SPRITE_MOUSE_RIGHT -> {
                shade1 = $0440
                shade2 = $0aa0
                shade3 = $0ff0
            }
            SPRITE_JOY_FIRE -> {
                shade1 = $0404
                shade2 = $0a0a
                shade3 = $0f0f
            }
            SPRITE_JOY_FIRE2 -> {
                shade1 = $0044
                shade2 = $00aa
                shade3 = $00ff
            }
        }
        custom.COLOR17 = shade1
        custom.COLOR18 = shade2
        custom.COLOR19 = shade3
    }

    sub start() {
        ; One 320x256, one-bitplane image: 40 bytes per row.
        pointer bitplane = exec.AllocMem(10240, exec.MEMF_CHIP | exec.MEMF_CLEAR)
        if bitplane==0
            return
        ubyte saved_ciaa_cra = custom.CIAA_CRA

        ^^custom.DisplayRegs regs = custom.compute_display(SCREEN_WIDTH as word, SCREEN_HEIGHT as word, 1, custom.DISPLAY_DEFAULT, 0)
        custom.grab_system(true)
        custom.CIAA_CRA = saved_ciaa_cra & $bf

        ; Enable DMA master and blitter DMA while preparing the plane.
        custom.DMACON = $8240
        blitter.fill_plane(bitplane, 20, 256, %1000000010000000)
        blitter.wait()

        word sprite_x = (SCREEN_WIDTH - SPRITE_SIZE) / 2
        word sprite_y = (SCREEN_HEIGHT - SPRITE_SIZE) / 2
        set_sprite_position(sprite_x, sprite_y)
        set_sprite_color(SPRITE_RED)
        set_sprite_pointers()

        ; Copper reapplies the bitmap configuration each frame; CPU restores sprite pointers.
        copperdata.copper_list[1] = msw(bitplane)
        copperdata.copper_list[3] = lsw(bitplane)
        copperdata.copper_list[5] = regs.fmode
        copperdata.copper_list[7] = regs.bplcon0
        copperdata.copper_list[9] = regs.diwstrt
        copperdata.copper_list[11] = regs.diwstop
        copperdata.copper_list[13] = regs.ddfstrt
        copperdata.copper_list[15] = regs.ddfstop
        copperdata.copper_list[17] = regs.bpl1mod
        custom.COP1LC = &copperdata.copper_list
        custom.DMACON = $83e0
        custom.COPJMP1 = 0
        custom.mouse_init(0, MOUSE_SCALE_DIVISOR)

        ubyte current_color = SPRITE_RED
        repeat {
            byte joystick_x
            byte joystick_y
            byte mouse_dx
            byte mouse_dy
            custom.waitvsync()
            set_sprite_pointers()
            joystick_x, joystick_y = custom.joystick_axes(1)
            mouse_dx, mouse_dy = custom.mouse_delta(0)
            sprite_x += (joystick_x as word) * 2 + (mouse_dx as word)
            sprite_y += (joystick_y as word) * 2 + (mouse_dy as word)
            sprite_x = clamp(sprite_x, 0, (SCREEN_WIDTH - SPRITE_SIZE) as word)
            sprite_y = clamp(sprite_y, 0, (SCREEN_HEIGHT - SPRITE_SIZE) as word)
            set_sprite_position(sprite_x, sprite_y)

            bool mouse_left = custom.left_button()
            bool mouse_middle = custom.middle_button()
            bool mouse_right = custom.right_button()
            bool joy_fire = custom.joystick_fire(1)
            bool joy_fire2 = custom.joystick_fire2(1)
            ubyte color = SPRITE_RED
            if mouse_left
                color = SPRITE_MOUSE_LEFT
            else if mouse_middle
                color = SPRITE_MOUSE_MIDDLE
            else if mouse_right
                color = SPRITE_MOUSE_RIGHT
            else if joy_fire
                color = SPRITE_JOY_FIRE
            else if joy_fire2
                color = SPRITE_JOY_FIRE2
            if color != current_color {
                set_sprite_color(color)
                current_color = color
            }
            bool key_received
            ubyte keycode
            key_received, keycode = custom.raw_keyboard_poll()
            if key_received and (keycode & $80)==0 and keycode<=custom.KeyCode::RAMIGA {
                if keycode==custom.KeyCode::ESC
                    break
                sprite_x = clamp(((keycode & $7f) as word) * 2, 0, (SCREEN_WIDTH - SPRITE_SIZE) as word)
                set_sprite_position(sprite_x, sprite_y)
            }
        }

        custom.CIAA_CRA = saved_ciaa_cra
        custom.FMODE = $0000
        custom.restore_system()
        exec.FreeMem(bitplane, 10240)
    }
}

copperdata {
    %option amiga_chipram

    ; Replayed every frame to configure the one-plane lores display and bitmap palette.
    uword[] copper_list = [
        $00e0, $0000,    ; MOVE BPL1PTH (patched at runtime)
        $00e2, $0000,    ; MOVE BPL1PTL (patched at runtime)
        $01fc, $0000,    ; MOVE FMODE (patched for AGA)
        $0100, $1200,    ; MOVE BPLCON0: one bitplane, color burst, lores
        $008e, $2c81,    ; MOVE DIWSTRT
        $0090, $2cc1,    ; MOVE DIWSTOP
        $0092, $0038,    ; MOVE DDFSTRT
        $0094, $00d0,    ; MOVE DDFSTOP (patched for AGA)
        $0108, $0000,    ; MOVE BPL1MOD
        $0180, $0000,    ; MOVE COLOR00 = black
        $0182, $069f,    ; MOVE COLOR01 = light blue
        $ffff, $fffe     ; END
    ]
}

spritedata {
    %option amiga_chipram

    ; Sprite 0: position/control, 16 rows of two data planes, then terminator.
    uword[36] sprite = [
        $a48c, $b400,
        $0180, $0180,
        $07e0, $07e0,
        $1ff0, $1ff0,
        $7ffc, $7ffc,
        $fffc, $7ffe,
        $fff0, $1ffe,
        $ffe0, $07fe,
        $ff80, $01fe,
        $ff00, $00fe,
        $ff00, $00fe,
        $ff00, $00fe,
        $7f00, $00fc,
        $1f00, $00f0,
        $0700, $00e0,
        $0100, $0080,
        $0000, $0000,
        $0000, $0000
    ]

    ; Zero-height terminated sprite data used to keep sprite channels 1-7 inactive.
    uword[4] inactive_sprite = [$0000, $0000, $0000, $0000]
}
