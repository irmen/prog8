; Animate a zooming star field.
; Rather than doing complicated 3d stuff, this simply uses polar coordinates.
; Every star lies along the circle and has a radius.
; Movement is dictated by a random accelleration, which increases the speed, which increases the radius.

; TODO extend the radius to a larger maximum (0-319 or perhaps 0-511, instead of 0-255) so that we can fill the whole screen.

%import math
%import palette
%import gfx_lores
%option no_sysinit

main {
    %option verafxmuls

    sub start() {
        const ubyte NUM_STARS = 128                 ; maximum is 128.
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
            radius[star] = mkword(math.rnd() & %11111000, 0)
            speed[star] = 0   ; radius[star] >> 6           ;  a slow buildup of movement at the start is kinda nice I think, so we start with speed zero
        }

        const uword angle = 0       ; if you make this a variable and increase it, the stars rotate.

        repeat {
            sys.waitvsync()
            ;;palette.set_color(0, $400)
            for star in NUM_STARS-1 downto 0 {

                gfx_lores.plot(prev_x[star], prev_y[star], 0)

                cx16.r2L = star*CIRCLE_SKIP + msb(angle)
                uword sx = (msb(math.sin8(cx16.r2L) as word * msb(radius[star])) + 128) as uword + 32
                ubyte sy = (msb(math.cos8(cx16.r2L) as word * msb(radius[star])) + 120)

                prev_x[star] = sx
                if sy<240 {
                    prev_y[star] = sy
                    gfx_lores.plot(sx, sy, color[star])
                } else
                    prev_y[star] = 0

                if radius[star] > $fffe - speed[star] {
                    ; reset star to center
                    accel[star] = (math.rnd() & 15) | 1
                    radius[star] = $0400 + accel[star] * 128
                    speed[star] = 0  ; radius[star] >> 6
                    color[star] = math.rnd()
                } else {
                    ; can still move more.
                    radius[star] += speed[star]
                    speed[star] += accel[star]
                }
            }
            ;; angle += $0040
            ;;palette.set_color(0, $000)
        }
    }
}
