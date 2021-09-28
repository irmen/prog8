%import gfx2
%import palette


; TODO WORK IN PROGRESS...
; want to make Amiga 'copper' bars color cycling effects


main {
    sub start() {
        ;palette.set_all_black()
        gfx2.screen_mode(4)

        ubyte yy
        for yy in 0 to 239
            gfx2.horizontal_line(0, yy, 320, yy)

        repeat {
            yy = 0
            repeat 8 {
                colors.random_half_bar()
                colors.mirror_bar()
                colors.set_palette(yy)
                yy+=32
            }

            repeat 10
                sys.waitvsync()
        }
    }

}

colors {
    ubyte target_red
    ubyte target_green
    ubyte target_blue
    ubyte[32] reds
    ubyte[32] greens
    ubyte[32] blues

    sub random_rgb12() {
        do {
            uword rr = rndw()
            target_red = msb(rr) & 15
            target_green = lsb(rr)
            target_blue = target_green & 15
            target_green >>= 4
        } until target_red+target_green+target_blue >= 12
    }

    sub mirror_bar() {
        ; mirror the top half bar into the bottom half
        ubyte ix=14
        ubyte mix=16
        do {
            reds[mix] = reds[ix]
            greens[mix] = greens[ix]
            blues[mix] = blues[ix]
            mix++
            ix--
        } until ix==255
        reds[mix] = 0
        greens[mix] = 0
        blues[mix] = 0
    }

    sub random_half_bar() {
        ; fade black -> color then fade color -> white
        ; gradient calculations in 8.8 bits fixed-point
        ; could theoretically be 4.12 bits for even more fractional accuracy
        random_rgb12()
        uword r = $000
        uword g = $000
        uword b = $000
        uword dr = target_red
        uword dg = target_green
        uword db = target_blue
        ubyte ix = 1

        ; gradient from black to halfway color
        reds[0] = 0
        greens[0] = 0
        blues[0] = 0
        dr <<= 5
        dg <<= 5
        db <<= 5
        continue_gradient()

        ; gradient from halfway color to white
        dr = (($f00 - r) >> 3) - 1
        dg = (($f00 - g) >> 3) - 1
        db = (($f00 - b) >> 3) - 1
        continue_gradient()
        return

        sub continue_gradient() {
            repeat 8 {
                reds[ix] = msb(r)
                greens[ix] = msb(g)
                blues[ix] = msb(b)
                r += dr
                g += dg
                b += db
                ix++
            }
        }
    }

    sub set_palette(ubyte offset) {
        ubyte ix
        for ix in 0 to 31 {
            uword color = mkword(reds[ix], (greens[ix] << 4) | blues[ix] )
            palette.set_color(ix+offset, color)
        }
    }
}
