%import math
%import textio
%import palette

; use the mouse to move the cursor around the screen
; it uses the fast direction routine to spotlight your mouse position.

; you can use the atan2() routine as well for more precision laser beams,
; but it will use a lot more memory due to the required lookup tables.

main  {
    sub start() {
        const uword WIDTH=320
        const ubyte HEIGHT=240

        uword xx
        ubyte yy
        cx16.set_screen_mode(128)
        cx16.mouse_config2(1)
        txt.print("move the mouse for the spotlight.")

        ; prefill
        for yy in 0 to HEIGHT-1 {
            cx16.FB_cursor_position(0, yy)
            for xx in 0 to WIDTH-1 {
                cx16.FB_set_pixel(math.direction(128, HEIGHT/2, clampx(xx), yy) + 128)
            }
        }


        ubyte previous_direction
        ubyte new_direction

        ; dim the screen
        for new_direction in 128 to 128+23
            palette.set_color(new_direction, $024)

        ; spotlight
        repeat {
            void, cx16.r0, cx16.r1, void = cx16.mouse_pos()
            new_direction = math.direction(128, HEIGHT/2, clampx(cx16.r0), cx16.r1L)
            if new_direction != previous_direction {
                sys.waitvsync()
                palette.set_color(new_direction+128, math.rndw())
                palette.set_color(previous_direction+128, $024)
                previous_direction = new_direction
            }
        }
    }

    sub clampx(uword screenx) -> ubyte {
        ;; return clamp(screenx, 32, 255+32)-32 as ubyte
        if screenx<32
            return 0
        else if screenx>255+32
            return 255
        return lsb(screenx)-32
    }
}
