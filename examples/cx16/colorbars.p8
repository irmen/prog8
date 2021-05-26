%import gfx2
%import palette
%import textio


; TODO WORK IN PROGRESS...
; want to make Amiga 'copper' bars color cycling effects


main {
    sub start() {
        palette.set_all_black()
        gfx2.screen_mode(4)

        ubyte yy
        for yy in 0 to 239
            gfx2.horizontal_line(0, yy, 320, yy & 63)

        repeat {
            colors.random_bar()
            colors.set_palette()
            repeat 20
                sys.waitvsync()
        }

        repeat {
        }
    }

}

colors {
    ubyte cr
    ubyte cg
    ubyte cb
    ubyte[48+16] reds
    ubyte[48+16] greens
    ubyte[48+16] blues
    ubyte bar_size

    sub random_rgb12() {
        do {
            uword rr = rndw()
            cr = msb(rr) & 15
            cg = lsb(rr)
            cb = cg & 15
            cg >>= 4
        } until cr+cg+cb >= 12
    }

    sub random_bar() {
        ; fade black -> color then fade color -> white
        random_rgb12()
        ubyte r=0
        ubyte g=0
        ubyte b=0
        ubyte different
        bar_size = 0

        repeat {
            different = false
            if r != cr {
                different = true
                r++
            }
            if g != cg {
                different = true
                g++
            }
            if b != cb {
                different = true
                b++
            }
            if not different
                break
            reds[bar_size] = r
            greens[bar_size] = g
            blues[bar_size] = b
            bar_size++
        }
        repeat {
            different = false
            if r != 15 {
                different = true
                r++
            }
            if g != 15 {
                different = true
                g++
            }
            if b != 15 {
                different = true
                b++
            }
            if not different
                break
            reds[bar_size] = r
            greens[bar_size] = g
            blues[bar_size] = b
            bar_size++
        }
        ; mirror bottom half from top half
        ubyte mi = bar_size-1
        repeat mi {
            reds[bar_size] = reds[mi]
            greens[bar_size] = greens[mi]
            blues[bar_size] = blues[mi]
            bar_size++
            mi--
        }
        ; make rest of bar black (bars are not always the same length using the simplistic algorithm above...)
        while bar_size != 48+16 {
            reds[bar_size] = $0
            greens[bar_size] = $0
            blues[bar_size] = $0
            bar_size++
        }
    }

    sub set_palette() {
        ubyte ix
        for ix in 0 to 48+15 {
            uword color = mkword(reds[ix], (greens[ix] << 4) | blues[ix] )
            palette.set_color(ix, color)
        }
    }
}
