%import textio
%import math

; Amiga 'copper' bars color cycling effect


main {
    sub start() {
        cx16.set_screen_mode(3)

        ; make palette color 1 black so we can print black letters over the background color 0
        txt.color2(1,0)
        txt.clear_screen()

        cx16.vpoke(1, $fa02, $0)
        cx16.vpoke(1, $fa03, $0)
        txt.plot(13,12)
        txt.print("amiga-inspired")
        txt.plot(10,14)
        txt.print("raster blinds effect")
        txt.plot(12,16)
        txt.print("random gradients")

        irq.make_new_gradient()
        cx16.enable_irq_handlers(true)
        cx16.set_line_irq_handler(irq.top_scanline, &irq.irqhandler)

        repeat {
        }
    }

}

irq {
    const ubyte top_scanline = 0
    ubyte blinds_start_ix = 0
    ubyte color_ix = 0
    uword next_irq_line = top_scanline
    ubyte shift_counter = 0

    ubyte[32+32+16] blinds_lines_reds
    ubyte[32+32+16] blinds_lines_greens
    ubyte[32+32+16] blinds_lines_blues


    sub irqhandler() -> bool {
        set_scanline_color(color_ix)
        color_ix++

        next_irq_line += 2      ; code needs 2 scanlines per color transition

        if next_irq_line == 480 {
            ; start over at top
            next_irq_line = top_scanline
            blinds_start_ix = 0
            color_ix = 0
            shift_counter++
            if shift_counter == 32+32+32 {
                make_new_gradient()
                shift_counter = 0
            } else if shift_counter & 1 !=0 {
                shift_gradient()
            }
        } else if next_irq_line & 15 == 0  {
            ; start next blinds
            blinds_start_ix++
            color_ix = blinds_start_ix
        }

        sys.set_rasterline(next_irq_line)
        return false
    }

    sub make_new_gradient() {
        colors.random_half_bar()
        colors.mirror_bar()
        ; can't use sys.memcopy due to overlapping buffer
        cx16.memory_copy(colors.reds, &blinds_lines_reds+32+16, len(colors.reds))
        cx16.memory_copy(colors.greens, &blinds_lines_greens+32+16, len(colors.greens))
        cx16.memory_copy(colors.blues, &blinds_lines_blues+32+16, len(colors.blues))
    }

    sub shift_gradient() {
        ; can't use sys.memcopy due to overlapping buffer
        cx16.memory_copy(&blinds_lines_reds+1, blinds_lines_reds, len(blinds_lines_reds)-1)
        cx16.memory_copy(&blinds_lines_greens+1, blinds_lines_greens, len(blinds_lines_greens)-1)
        cx16.memory_copy(&blinds_lines_blues+1, blinds_lines_blues, len(blinds_lines_blues)-1)
    }

    asmsub set_scanline_color(ubyte color_ix @Y) {
        ; uword color = mkword(reds[ix], (greens[ix] << 4) | blues[ix] )
        %asm {{
            lda  p8v_blinds_lines_reds,y
            pha
            lda  p8v_blinds_lines_greens,y
            asl  a
            asl  a
            asl  a
            asl  a
            ora  p8v_blinds_lines_blues,y
            tay

            stz  cx16.VERA_CTRL
            lda  #%00010001
            sta  cx16.VERA_ADDR_H
            lda  #$fa
            sta  cx16.VERA_ADDR_M
            ; lda  #$02
            ; sta  cx16.VERA_ADDR_L
            stz  cx16.VERA_ADDR_L
            sty  cx16.VERA_DATA0        ; gb
            pla
            sta  cx16.VERA_DATA0        ; r
            stz  cx16.VERA_ADDR_H
            rts
        }}
    }
}


colors {
    ubyte target_red
    ubyte target_green
    ubyte target_blue
    ubyte[32] reds
    ubyte[32] greens
    ubyte[32] blues

    sub random_rgb12() {
        do {
            uword rr = math.rndw()
            target_red = msb(rr) & 15
            target_green = lsb(rr)
            target_blue = target_green & 15
            target_green >>= 4
        } until target_red+target_green+target_blue >= 12
    }

    sub mirror_bar() {
        ; mirror the top half bar into the bottom half
        ubyte ix=14
        ubyte mix=16
        do {
            reds[mix] = reds[ix]
            greens[mix] = greens[ix]
            blues[mix] = blues[ix]
            mix++
            ix--
        } until ix==255
        reds[mix] = 0
        greens[mix] = 0
        blues[mix] = 0
    }

    sub random_half_bar() {
        ; fade black -> color then fade color -> white
        ; gradient calculations in 8.8 bits fixed-point
        ; could theoretically be 4.12 bits for even more fractional accuracy
        random_rgb12()
        uword r = $000
        uword g = $000
        uword b = $000
        uword dr = target_red
        uword dg = target_green
        uword db = target_blue
        ubyte ix = 1

        ; gradient from black to halfway color
        reds[0] = 0
        greens[0] = 0
        blues[0] = 0
        dr <<= 5
        dg <<= 5
        db <<= 5
        continue_gradient()

        ; gradient from halfway color to white
        dr = (($f00 - r) >> 3) - 1
        dg = (($f00 - g) >> 3) - 1
        db = (($f00 - b) >> 3) - 1
        continue_gradient()
        return

        sub continue_gradient() {
            repeat 8 {
                reds[ix] = msb(r)
                greens[ix] = msb(g)
                blues[ix] = msb(b)
                r += dr
                g += dg
                b += db
                ix++
            }
        }
    }
}
