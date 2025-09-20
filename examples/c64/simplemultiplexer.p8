; Simple sprite multiplexer.
; This is just a sprite duplicator routine that displays the 8 hardware sprites multiple times in rows,
; it does not do any complicated sprite Y manipulation/multiplexing.
; No assembly code is used.


%import syslib
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        ubyte i
        for i in 0 to 7 {
            c64.set_sprite_ptr(i, &sprites.balloonsprite)           ; alternatively, set directly:  c64.SPRPTR[i] = $0a00 / 64
        }
        sprites.set_sprites_X(sprites.sprites_X_start)
        sprites.set_sprites_Y(sprites.sprites_Y_start)
        c64.SPCOL[5] = 8   ; change blue balloon to different color

        c64.SPENA = 255       ; enable all sprites
        irq.sprites_X = sprites.sprites_X_start
        irq.sprites_Y = sprites.sprites_Y_start
        sys.set_rasterirq(&irq.multiplexer, irq.sprites_Y+1)

        ; exit program back to basic, balloons will keep flying :-)
    }

}

irq {
    ubyte sprites_X, sprites_Y

    ; Here is the actual multiplexing routine.
    ; it's a raster irq just after the start of the sprite,
    ; that updates the Y position of all the sprits,
    ; and registers a new rater irq for that next row of sprites.
    ; If the bottom of the screen is reached, it resets the X position of the sprites as well,
    ; and moves the sprites back to the top of the screen.
    sub multiplexer() -> bool {
        c64.EXTCOL++
        sprites_Y += 24
        bool system_irq = false
        if sprites_Y > (255-24-1) {
            cx16.r2 = c64.RASTER + 23
            sprites_Y = sprites.sprites_Y_start
            sprites_X++
            sprites.set_sprites_Y(sprites_Y)
            while c64.RASTER != cx16.r2 {
                ; wait until raster line after sprite has been fully drawn (at least 24 lines down)
            }
            sprites.set_sprites_X(sprites_X)        ; we can now update the X positions without risk of sprite tearing
            system_irq = true
        } else {
            sprites.set_sprites_Y(sprites_Y)
        }

        sys.set_rasterline(sprites_Y+1)
        c64.EXTCOL--
        return system_irq
    }

}

sprites {
    const ubyte sprites_X_start = 60
    const ubyte sprites_Y_start = 55

    sub set_sprites_Y(ubyte y) {
        for cx16.r0L in 1 to 15 step 2 {
            c64.SPXY[cx16.r0L] = y
        }
    }

    sub set_sprites_X(ubyte x) {
        for cx16.r0L in 0 to 14 step 2 {
            c64.SPXY[cx16.r0L] = x
            x += 20
        }
    }

    ubyte[] @align64 balloonsprite = [
        %00000000,%01111111,%00000000,
        %00000001,%11111111,%11000000,
        %00000011,%11111111,%11100000,
        %00000011,%11100011,%11100000,
        %00000111,%11011100,%11110000,
        %00000111,%11011101,%11110000,
        %00000111,%11011100,%11110000,
        %00000011,%11100011,%11100000,
        %00000011,%11111111,%11100000,
        %00000011,%11111111,%11100000,
        %00000010,%11111111,%10100000,
        %00000001,%01111111,%01000000,
        %00000001,%00111110,%01000000,
        %00000000,%10011100,%10000000,
        %00000000,%10011100,%10000000,
        %00000000,%01001001,%00000000,
        %00000000,%01001001,%00000000,
        %00000000,%00111110,%00000000,
        %00000000,%00111110,%00000000,
        %00000000,%00111110,%00000000,
        %00000000,%00011100,%00000000
    ]
}

