%import math
%import textio
%import sprites
%import floats

; shows the use of the LERP (Linear intERPolation) routines that prog8 provides.
; math.lerp / math.lerpw / floats.lerp / floats.lerp_fast

; LERP explained here: https://en.wikipedia.org/wiki/Linear_interpolation
; Here are a lot of easing functions you can use:
; https://blog.febucci.com/2018/08/easing-functions/     (links at the bottom)


main {
    uword mx, my

    sub start() {
        cx16.set_screen_mode(3)
        cx16.mouse_config2(1)
        sprites.set_mousepointer_hand()

        repeat {
            txt.cls()
            txt.print("\n\n  floats.lerp()")
            sys.wait(60)
            repeat 2  lerp_float()

            txt.cls()
            txt.print("\n\n  floats.lerp() with easing")
            sys.wait(60)
            repeat 2  lerp_float_easing()

            txt.cls()
            txt.print("\n\n  integer math.lerp()")
            sys.wait(60)
            repeat 2  lerp_normal()

            txt.cls()
            txt.print("\n\n  integer math.lerp() with easing")
            sys.wait(60)
            repeat 2  lerp_easing()

            txt.cls()
            txt.print("\n\n  floats.interpolate()")
            sys.wait(60)
            repeat 2  interpolate_float()

            txt.cls()
            txt.print("\n\n  integer math.interpolate()")
            sys.wait(60)
            repeat 2  interpolate_int()
        }
    }

    sub interpolate_float() {
        ubyte tt
        for tt in 0 to 255 step 3 {
            sys.waitvsync()
            mx = floats.interpolate(tt as float, 0, 255, 50, 250) as uword
            my = floats.interpolate(tt as float, 0, 255, 30, 150) as uword
            cx16.mouse_set_pos(mx, my)
        }

        for tt in 255 downto 0 step -3 {
            sys.waitvsync()
            mx = floats.interpolate(tt as float, 0, 255, 50, 250) as uword
            my = floats.interpolate(tt as float, 0, 255, 30, 150) as uword
            cx16.mouse_set_pos(mx, my)
        }
    }

    sub interpolate_int() {
        ubyte tt
        for tt in 50 to 250 step 3 {
            sys.waitvsync()
            mx = math.interpolate(tt, 50, 250, 50, 250)
            my = math.interpolate(tt, 50, 250, 30, 150)
            cx16.mouse_set_pos(mx, my)
        }

        for tt in 250 downto 50 step -3 {
            sys.waitvsync()
            mx = math.interpolate(tt, 50, 250, 50, 250)
            my = math.interpolate(tt, 50, 250, 30, 150)
            cx16.mouse_set_pos(mx, my)
        }
    }

    sub lerp_float() {
        ubyte tt
        for tt in 0 to 255 step 3 {
            sys.waitvsync()
            mx = floats.lerp(50, 250, tt as float/256) as uword
            my = floats.lerp(30, 150, tt as float/256) as uword
            cx16.mouse_set_pos(mx, my)
        }

        for tt in 255 downto 0 step -3 {
            sys.waitvsync()
            mx = floats.lerp(50, 250, tt as float/256) as uword
            my = floats.lerp(30, 150, tt as float/256) as uword
            cx16.mouse_set_pos(mx, my)
        }
    }

    sub lerp_float_easing() {
        float e
        ubyte tt

        for tt in 0 to 255 step 2 {
            sys.waitvsync()
            e = ease(tt)
            mx = floats.lerp(50, 250, e) as uword
            my = floats.lerp(30, 150, e) as uword
            cx16.mouse_set_pos(mx, my)
        }

        for tt in 255 downto 0 step -2 {
            sys.waitvsync()
            e = 1 - ease(255-tt)
            mx = floats.lerp(50, 250, e) as uword
            my = floats.lerp(30, 150, e) as uword
            cx16.mouse_set_pos(mx, my)
        }

        sub ease(ubyte t) -> float {
            ; bounce
            const float n1 = 7.5625
            const float d1 = 2.75
            float x = t as float/256
            if (x < 1 / d1) {
                return n1 * x * x;
            } else if (x < 2 / d1) {
                x = x - 1.5 / d1;
                return n1 * x * x + 0.75;
            } else if (x < 2.5 / d1) {
                x = x - 2.25 / d1;
                return n1 * x * x + 0.9375;
            } else {
                x = x - 2.625 / d1;
                return n1 * x * x + 0.984375;
            }
        }

        sub ease2(ubyte t) -> float {
            ; 'smootherstep'
            float x = t as float/256
            return x * x * x * (x * (x * 6 - 15) + 10)
        }
    }

    sub lerp_normal() {
        ubyte tt
        for tt in 0 to 255 step 3 {
            sys.waitvsync()
            mx = math.lerp(50, 250, tt)
            my = math.lerp(30, 150, tt)
            cx16.mouse_set_pos(mx, my)
        }

        for tt in 255 downto 0 step -3 {
            sys.waitvsync()
            mx = math.lerp(50, 250, tt)
            my = math.lerp(30, 150, tt)
            cx16.mouse_set_pos(mx, my)
        }
    }

    sub lerp_easing() {
        ubyte e
        ubyte tt
        for tt in 0 to 255 step 3 {
            sys.waitvsync()
            e = ease(tt)
            mx = math.lerp(50, 250, e)
            my = math.lerp(30, 150, e)
            cx16.mouse_set_pos(mx, my)
        }

        for tt in 255 downto 0 step -3 {
            sys.waitvsync()
            e = ease(tt)
            mx = math.lerp(50, 250, e)
            my = math.lerp(30, 150, e)
            cx16.mouse_set_pos(mx, my)
        }

        sub ease(ubyte t) -> ubyte {
            return msb(t as uword*t)        ; 'squaring'
        }
    }
}
