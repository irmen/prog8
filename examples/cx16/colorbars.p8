%import palette
%import textio


; TODO WORK IN PROGRESS...
; want to make Amiga 'copper' bars color cycling effects


main {
    sub start() {
        txt.plot(5,5)
        txt.print("amiga-like raster blinds effect (work in progress)")

        irq.make_new_gradient()
        cx16.set_rasterirq(&irq.irqhandler, 100)

        repeat {
        }
    }

}

irq {
    ubyte color_ix = 0
    uword next_irq_line = 100
    ubyte gradient_counter = 0

    sub irqhandler() {
        palette.set_color(0, colors.get_colorword(color_ix))
        color_ix++

        if color_ix==32 {
            next_irq_line = 100
            color_ix = 0

            ; just arbitrary mechanism for now, to change to a new gradient after a short while
            gradient_counter++
            if gradient_counter & 16 {
                make_new_gradient()
                gradient_counter = 0
            }

        } else {
            next_irq_line += 2      ; code needs 2 scanlines per color transition
        }

        cx16.set_rasterline(next_irq_line)
    }

    sub make_new_gradient() {
        colors.random_half_bar()
        colors.mirror_bar()
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

    asmsub get_colorword(ubyte color_ix @Y) -> uword @AY {
        ; uword color = mkword(reds[ix], (greens[ix] << 4) | blues[ix] )
        %asm {{
            lda  colors.reds,y
            pha
            lda  colors.greens,y
            asl  a
            asl  a
            asl  a
            asl  a
            ora  colors.blues,y
            ply
            rts
        }}
    }
}
