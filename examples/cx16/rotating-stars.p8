; Animate a rotating and zooming sprite field.
; Rather than doing complicated 3d stuff, this simply uses polar coordinates.
; Every star lies along the circle and just has a radius R.

%import math
%import palette
%import gfx_lores
%option no_sysinit

main {
    %option verafxmuls

    sub start() {
        const ubyte NUM_STARS = 128         ; must be 128 to evenly distribute (angle goes from 0..255 in steps of 2)

        ubyte[NUM_STARS] r
        ubyte[NUM_STARS] speeds
        uword[NUM_STARS]  prev_x
        ubyte[NUM_STARS]  prev_y
        uword angle

        gfx_lores.set_screen_mode()

        ; init the star positions
        ubyte star
        for star in 0 to NUM_STARS-1 {
            r[star] = 4 + math.rnd()/4
            speeds[star] = (math.rnd() & 3) + 1
        }

        repeat {
            sys.waitvsync()
            ;; palette.set_color(0, $400)
            for star in NUM_STARS-1 downto 0 {
                gfx_lores.plot(prev_x[star], prev_y[star], 0)
                cx16.r2L = star*2 + msb(angle)
                uword sx = (msb(math.sin8(cx16.r2L) as word * r[star]) + 128) as uword + 32
                ubyte sy = (msb(math.cos8(cx16.r2L) as word * r[star]) + 120)
                prev_x[star] = sx
                if sy<240 {
                    prev_y[star] = sy
                    gfx_lores.plot(sx, sy, star)
                } else
                    prev_y[star] = 0

                r[star] += speeds[star]
                if r[star] >= 255-3 {
                    r[star] = 4
                    speeds[star] = (math.rnd() & 3) + 1
                }
            }
            angle += $0040
            ;; palette.set_color(0, $000)
        }
    }
}

