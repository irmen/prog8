%import textio
%import palette

; horizontal raster bars
; also see: kefrenbars.p8


main {

    sub start() {
        cx16.screen_set_mode(0)
        txt.plot(14,14)
        txt.print("raster bars!")

        cx16.set_rasterirq(&irq.irqhandler, 0)

        repeat {
            ; don't exit
        }
    }
}


irq {
    uword[32] colors = [
        $011,  $112,  $213,  $214,
        $315,  $316,  $417,  $418,
        $519,  $51a,  $62b,  $62c,
        $73d,  $73e,  $84f,  $94f,
        $93e,  $83d,  $82c,  $72b,
        $71a,  $619,  $618,  $517,
        $516,  $415,  $414,  $313,
        $312,  $211,  $100,  $000
    ]

    uword next_irq_line = 0
    ubyte color_idx = 0
    ubyte yanim = 0
    const ubyte barheight = 4

    sub irqhandler() {
        uword c = colors[color_idx]
        color_idx++
        color_idx &= 31

        if color_idx==0 {
            yanim++
            next_irq_line = $0030 + sin8u(yanim)
        } else {
            next_irq_line += barheight
        }

        palette.set_color(0, c)

        cx16.set_rasterline(next_irq_line)
    }
}
