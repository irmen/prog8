%import graphics
%import floats
%import math
%option no_sysinit

; Bubble Universe
; see: https://stardot.org.uk/forums/viewtopic.php?f=54&t=25833

main {
    sub start() {
        graphics.enable_bitmap_mode()

        const ubyte n = 200
        const float r = floats.TWOPI/235
        const ubyte s = 60
        float t

        uword[] @nosplit palette = [$000, $000, $00f, $f0f, $0ff, $fff]
        cx16.FB_set_palette(palette, 0, len(palette))

        repeat {
            graphics.clear_screen(1,0)
            ubyte i
            for i in 0 to n {
                ubyte j
                float ang1_start = i as float + t
                float ang2_start = i as float * r + t
                float v=0
                float u=0
                for j in 0 to n {
                    float ang1 = ang1_start+v
                    float ang2 = ang2_start+u
                    u=floats.sin(ang1)+floats.sin(ang2)
                    v=floats.cos(ang1)+floats.cos(ang2)
                    ubyte c=2
                    if i>=100
                        c++
                    if j>=100
                        c+=2
                    graphics.colors(c,0)
                    ; TODO nice rgb color?:  GCOL i%/num_curves%*255,j%/iteration_length%*255,255-(i%/num_curves%+j%/iteration_length%)*128
                    uword a = 40 + ((2+u) * s) as uword
                    uword b = ((v+2)*s) as uword
                    graphics.plot(a,b)
                }
            }
            t+=0.025
        }
    }
}
