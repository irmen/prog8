
; color fader that doesn't do palette fade but color attribute fade.
; NOTE: this needs ROM 49+

%import textio
%import math
%zeropage basicsafe
%option no_sysinit


main {
    ^^uword palette = memory("palette", 256*2, 0)
    ^^ubyte closest = memory("closest", 4096, 0)

    const ubyte WIDTH = 40
    const ubyte HEIGHT = 30

    sub start() {

        fill_default_palette()

        txt.print("\nprecalc color fade table, patience plz ")
        precalc_fade_table()

        cx16.set_screen_mode(128)
        cx16.GRAPH_set_colors(0,0,0)
        cx16.GRAPH_clear()
        txt.t256c(true)
        ubyte x,y
        for y in 0 to HEIGHT-1 {
            for x in 0 to WIDTH-1 {
                txt.setcc2(x,y, math.rnd(), math.rnd())
            }
        }

        sys.wait(60)

        repeat  {
            repeat 4 sys.waitvsync()

            for y in 0 to HEIGHT-1 {
                for x in 0 to WIDTH-1 {
                    ubyte @zp currentcolor = txt.getclr(x,y)
                    cx16.r0L = closest[darken(palette[currentcolor])]
                    if cx16.r0L>0 and cx16.r0L == currentcolor
                        cx16.r0L = 15       ; to avoid stuck colors, make it gray, this will be able to fade to black because the pallette contains all shades of gray.
                    txt.setclr(x,y, cx16.r0L)
                }

                txt.setcc2(math.rnd() % WIDTH, math.rnd() % HEIGHT, math.rnd(), math.rnd())     ; add new chars
            }
        }
    }

    sub precalc_fade_table() {
        uword colorindex
        ubyte r,g,b
        alias color = cx16.r2

        for r in 0 to 15 {
            txt.print_ub(16-r)
            txt.spc()
            for g in 0 to 15 {
                for b in 0 to 15 {
                    color = mkword(r, g<<4 | b)
                    closest[colorindex] = find_closest(color)
                    colorindex++
                }
            }
        }
    }

    sub darken(uword color @R0) -> uword {
        if cx16.r0H!=0
            cx16.r0H--
        if cx16.r0L & $0f != 0
            cx16.r0L--
        if cx16.r0L & $f0 != 0
            cx16.r0L -= $10
        return cx16.r0
    }

    sub find_closest(uword rgb @R0) -> ubyte {
        ubyte distance = 255
        ubyte current = 0
        alias index = cx16.r1L

        for index in 0 to 255 {
            uword @zp pc = palette[index]
            ubyte @zp d2 = abs(msb(pc) as byte - msb(rgb) as byte)                ; RED distance
            d2 += abs((lsb(pc) & $f0) as byte - (lsb(rgb) & $f0) as byte) >> 4    ; GREEN distance
            d2 += abs((lsb(pc) & $0f) as byte - (lsb(rgb) & $0f) as byte)         ; BLUE distance
            if d2==0
                return index
            if d2 < distance {
                distance = d2
                current = index
            }
        }

        return current
    }

    sub fill_default_palette() {
        ubyte pal_bank
        uword pal_addr

        pal_bank, pal_addr = cx16.get_default_palette()      ; needs ROM 49+
        cx16.push_rombank(pal_bank)
        sys.memcopy(pal_addr, palette, 256*2)
        cx16.pop_rombank()
    }
}
