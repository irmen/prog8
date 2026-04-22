; Animate a zooming star field.
; Rather than doing complicated 3d stuff, this simply uses polar coordinates.
; Every star lies along the circle and has a radius.
; Movement is dictated by a random acceleration, which increases the speed, which increases the radius.

%import math
%import palette
%import gfx_lores
%option no_sysinit

main {
    %option verafxmuls

    sub start() {
        const ubyte NUM_STARS = 128
        const ubyte CIRCLE_SKIP = 256/NUM_STARS

        ubyte[NUM_STARS] color
        uword[NUM_STARS] radius
        uword[NUM_STARS] accel
        uword[NUM_STARS] speed
        uword[NUM_STARS] prev_x
        ubyte[NUM_STARS] prev_y

        gfx_lores.graphics_mode()

        ; init the star positions
        ubyte star
        for star in 0 to NUM_STARS-1 {
            color[star] = star
            accel[star] = (math.rnd() & 15) | 1
            radius[star] = mkword(math.randrange(210), 0)
            speed[star] = 0
            prev_y[star] = 255   ; mark as off-screen
        }

        ubyte rotation

        repeat {
            sys.waitvsync()
            rotation += 1

            for star in NUM_STARS-1 downto 0 {

                if prev_y[star] < 240 {
                    gfx_lores.plot(prev_x[star], prev_y[star], 0)
                }

                ; use scaling / 128 so that msb(radius) is approximately the pixel radius
                ; added a rotation and radius-based angle offset for a tunnel/spiral effect
                ubyte a = (star * CIRCLE_SKIP) + rotation + (msb(radius[star]) / 2)
                word r_px = msb(radius[star])
                word dx = (math.cos8(a) as word * r_px) / 128
                word dy = (math.sin8(a) as word * r_px) / 128

                word sx_w = dx + 160
                word sy_w = dy + 120

                ; clipping so that we don't draw outside the screen.
                if (sx_w as uword) < 320 and (sy_w as uword) < 240 {
                    prev_x[star] = sx_w as uword
                    prev_y[star] = sy_w as ubyte
                    gfx_lores.plot(prev_x[star], prev_y[star], color[star])
                } else {
                    prev_y[star] = 255
                }

                if msb(radius[star]) > 210 {
                    ; reset star to center
                    accel[star] = (math.rnd() & 15) | 1
                    radius[star] = $0400 + accel[star] * 128
                    speed[star] = 0
                    color[star] = math.rnd()
                } else {
                    ; can still move more.
                    radius[star] += speed[star]
                    speed[star] += accel[star]
                }
            }
        }
    }
}
