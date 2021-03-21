%target cx16
%import palette
%option no_sysinit

; Vertical rasterbars a.k.a. "Kefren bars"
; also see: rasterbars.p8


main {

    sub start() {
        uword[32] colors = [
            $000,
            $011,  $112,  $213,  $214,
            $315,  $316,  $417,  $418,
            $519,  $51a,  $62b,  $62c,
            $73d,  $73e,  $84f,  $94f,
            $93e,  $83d,  $82c,  $72b,
            $71a,  $619,  $618,  $517,
            $516,  $415,  $414,  $313,
            $312,  $211,  $100
        ]

        ; Not yet implemented in ROM:  cx16.FB_set_palette(&colors, 0, len(colors)*3)
        palette.set_rgb(&colors, len(colors))
        cx16.screen_set_mode(128)   ; low-res bitmap 256 colors
        cx16.FB_init()
        cx16.VERA_DC_VSCALE = 0   ; display trick spoiler.......: stretch display all the way to the bottom
        cx16.set_rasterirq(&irq.irq, 0)

        repeat {
            ; don't exit
        }
    }
}


irq {
    uword next_irq_line = 0
    ubyte anim1 = 0
    ubyte av1 = 0
    ubyte anim2 = 0
    ubyte av2 = 0

    ubyte[32] pixels = 0 to 31

    sub irq() {
        next_irq_line += 6
        anim1 += 4
        anim2 += 6
        if next_irq_line > 400 {
            av1++
            av2 += 2
            anim1 = av1
            anim2 = av2
            next_irq_line = 0
            ; erase the bars
            cx16.FB_cursor_position(0, 0)
            cx16.FB_fill_pixels(320, 1, 0)
        } else {
            ; add new bar
            cx16.FB_cursor_position(sin8u(anim1)/2 + cos8u(anim2)/2 + $0010, 0)
            cx16.FB_set_pixels(pixels, len(pixels))
        }

        cx16.set_rasterline(next_irq_line)
    }
}
