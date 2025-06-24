%import graphics
%import floats
%import palette
%import textio
%option no_sysinit

; Bubble Universe
; see: https://stardot.org.uk/forums/viewtopic.php?f=54&t=25833

main {
    sub start() {
        init_palette()
        graphics.enable_bitmap_mode()
        txt.color(15)
        txt.print("\n\n\n   bubble universe.")
        txt.print("\n\n\n   calculation is quite slow\n   (floating point),\n\n   using the emulator's\n   warp mode is advised.")
        sys.wait(200)
        txt.cls()

        const ubyte n = 200
        const float r = floats.TWOPI/235
        const ubyte s = 60
        float t

        repeat {
            graphics.clear_screen(1, 0)
            ubyte i
            for i in 0 to n {
                float ang1_start = i as float + t
                float ang2_start = i as float * r + t
                ubyte j
                for j in 0 to n {
                    float @zp ang1 = ang1_start+v
                    float @zp ang2 = ang2_start+u
                    float @zp u = floats.sin(ang1)+floats.sin(ang2)
                    float @zp v = floats.cos(ang1)+floats.cos(ang2)
                    graphics.colors(j/16+3+((i/16+3)<<4), 0)
                    uword a = 40 + ((2+u) * s) as uword
                    uword b = ((v+2)*s) as uword
                    graphics.plot(a, b)
                }
            }
            t+=0.025
        }
    }

    sub init_palette() {
        ubyte idx
        ubyte i
        uword j
        i, void, void = cx16.entropy_get()

        if i & 1 == 0 {
            ; 'neon nebula'
            for j in 0 to $f00 step $100 {
                for i in 0 to 15 {
                    when i {
                        15 -> palette.set_color(idx, $faf)
                        14 ->  palette.set_color(idx, $f48)
                        else -> palette.set_color(idx, j+i)
                    }
                    idx++
                }
            }
        } else {
            ; 'slime on fire'
            for j in 0 to $f00 step $100 {
                for i in $00 to $f0 step $10 {
                    when i {
                        $f0 -> palette.set_color(idx, $aff)
                        $e0 ->  palette.set_color(idx, $4f8)
                        else -> palette.set_color(idx, j+i)
                    }
                    idx++
                }
            }
        }
    }
}
