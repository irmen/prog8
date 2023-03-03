%import palette
%import math
%import gfx2
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

        palette.set_rgb(&colors, len(colors))
        gfx2.screen_mode(4)       ; lores 256 colors
        cx16.VERA_DC_VSCALE = 0   ; display trick spoiler.......: stretch 1 line of display all the way to the bottom
        cx16.set_rasterirq(&irq.irqhandler, 0)

        repeat {
            ; don't exit
        }
    }
}


irq {
    const ubyte BAR_Y_OFFSET = 6
    uword next_irq_line = 0
    ubyte anim1 = 0
    ubyte av1 = 0
    ubyte anim2 = 0
    ubyte av2 = 0
    ubyte[32] pixels = 0 to 31

    sub irqhandler() {
        next_irq_line += BAR_Y_OFFSET
        anim1 += 7
        anim2 += 4
        if next_irq_line > 480-BAR_Y_OFFSET {
            av1++
            av2 += 2
            anim1 = av1
            anim2 = av2
            next_irq_line = 0
            ; erase the bars
            gfx2.horizontal_line(0, 0, 320, 3)
        } else {
            ; add new bar on top
            gfx2.position(math.sin8u(anim1)/2 + math.cos8u(anim2)/2 + $0010, 0)
            gfx2.next_pixels(pixels, len(pixels))
        }

        cx16.set_rasterline(next_irq_line)
    }
}
