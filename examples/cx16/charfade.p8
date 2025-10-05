
; color fader that doesn't do palette fade but color attribute fade.
; NOTE: this needs ROM 49+

%import textio
%import math
%zeropage basicsafe
%option no_sysinit


main {
    ^^uword palette = memory("palette", 256*2, 0)
    ubyte[256] fadeLUT      ; for each of the 256 colors in the palette, give the color index of the closest 1 step darker color.

    const ubyte WIDTH = 40
    const ubyte HEIGHT = 30

    sub start() {

        precalc_fade_table()

        cx16.set_screen_mode(128)
        cx16.GRAPH_set_colors(0,0,0)
        cx16.GRAPH_clear()
        txt.t256c(true)         ; to allow characters to use all 256 colors in the palette instead of just the first 16

        ubyte x,y
        for y in 0 to HEIGHT-1 {
            for x in 0 to WIDTH-1 {
                txt.setcc2(x,y, math.rnd(), math.rnd())
            }
        }

        sys.wait(30)

        repeat  {
            repeat 4 sys.waitvsync()

            for y in 0 to HEIGHT-1 {
                for x in 0 to WIDTH-1 {
                    ; this can be done much faster using Vera's auto increment mode, but for the sake of clarity, we'll do it like this
                    txt.setclr(x,y, fadeLUT[txt.getclr(x,y)])
                }

                txt.setcc2(math.rnd() % WIDTH, math.rnd() % HEIGHT, math.rnd(), math.rnd())     ; add new chars
            }
        }
    }


    sub precalc_fade_table() {
        txt.print("\nprecalc color fade table, patience plz")

        fill_default_palette()

        ubyte index
        for index in 0 to 255 {
            fadeLUT[index] = find_darker(index)
        }

        sub fill_default_palette() {
            ubyte pal_bank
            uword pal_addr

            pal_bank, pal_addr = cx16.get_default_palette()      ; needs ROM 49+
            cx16.push_rombank(pal_bank)
            sys.memcopy(pal_addr, palette, 256*2)
            cx16.pop_rombank()
        }

        sub find_darker(ubyte color_index) -> ubyte {
            uword darker_rgb = darken(palette[index])
            if darker_rgb == 0
                return 0
            ubyte closest, second
            closest, second = find_2_closest_colorindexes(darker_rgb)
            if closest == color_index
                return second       ; to avoid stuck colors
            return closest
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

        sub find_2_closest_colorindexes(uword rgb @R0) -> ubyte, ubyte {
            ubyte distance = 255
            ubyte current
            ubyte second = 15
            alias index = cx16.r1L

            for index in 0 to 255 {
                uword @zp pc = palette[index]
                ubyte @zp d2 = abs(msb(pc) as byte - msb(rgb) as byte)                ; RED distance
                d2 += abs((lsb(pc) & $f0) as byte - (lsb(rgb) & $f0) as byte) >> 4    ; GREEN distance
                d2 += abs((lsb(pc) & $0f) as byte - (lsb(rgb) & $0f) as byte)         ; BLUE distance
                if d2==0
                    return index, second
                if d2 < distance {
                    distance = d2
                    second = current
                    current = index
                }
            }

            return current, second
        }
    }
}
