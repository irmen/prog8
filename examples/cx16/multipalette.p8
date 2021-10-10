%import palette
%import gfx2
%option no_sysinit

main {

    sub start() {
        ; palette.set_rgb(&colors, len(colors))
        void cx16.screen_set_mode(128)   ; low-res bitmap 256 colors

        cx16.FB_init()

        cx16.VERA_DC_VIDEO = (cx16.VERA_DC_VIDEO & %11001111) | %00010000      ; enable only layer 0
        cx16.VERA_L0_CONFIG = %00000110     ; 4 bpp = 16 colors
        ;cx16.VERA_L0_MAPBASE = 0
        ;cx16.VERA_L0_TILEBASE = 0

        ubyte pix=0
        ubyte ypos
        cx16.FB_cursor_position(0, 0)
        for ypos in 0 to 199 {
            repeat 320/2 {
                cx16.FB_set_pixel((pix&15)<<4 | (pix&15))
                pix++
            }
            pix=0
        }

        ; color index 0 can't be swapped - set it to black in both ranges
        palette.set_color(0, 0)
        palette.set_color(16, 0)

        cx16.set_rasterirq(&irq.irqhandler, 0)

        repeat {
            ; don't exit
        }
    }
}


irq {
    ubyte phase = 0
    uword next_rasterline = 0
    const ubyte increment = 4           ; 4 scanlines = 2 lores pixels per color swap  (2 scanlines is too tight)

    sub irqhandler() {
        if phase & 1 == 0 {
            %asm {{
                lda  #0     ; activate palette #0  (first set of colors)
                sta  cx16.VERA_L0_HSCROLL_H

                stz  cx16.VERA_CTRL
                lda  #<$fa00+32+2
                sta  cx16.VERA_ADDR_L
                lda  #>$fa00+32+2
                sta  cx16.VERA_ADDR_M
                lda  #%00010001
                sta  cx16.VERA_ADDR_H

                ; change 15 palette entries 1..15  (0 is fixed)
                lda  #<$e000
                sta  $02
                lda  #>$e000
                sta  $02
                ldy  #0
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0



;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0
;                lda  #$0f
;                sta  cx16.VERA_DATA0
;                lda  #0
;                sta  cx16.VERA_DATA0

            }}
        }
        else {
            %asm {{
            lda  #1     ; activate palette #1  (second set of colors)
            sta  cx16.VERA_L0_HSCROLL_H

            stz  cx16.VERA_CTRL
            lda  #<$fa00+2
            sta  cx16.VERA_ADDR_L
            lda  #>$fa00+2
            sta  cx16.VERA_ADDR_M
            lda  #%00010001
            sta  cx16.VERA_ADDR_H

                ; change 15 palette entries 1..15  (0 is fixed)

                lda  #<$f000
                sta  $02
                lda  #>$f000
                sta  $02
                ldy  #0
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0
                iny
                lda  (2),y
                sta  cx16.VERA_DATA0


;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0
;            lda  #$ff
;            sta  cx16.VERA_DATA0
;            lda  #0
;            sta  cx16.VERA_DATA0

            }}
        }

        phase++
        next_rasterline += increment

        if next_rasterline > 400 {
            next_rasterline = 0
            phase = 0
        }

        cx16.set_rasterline(next_rasterline)

;
;        uword[16] colors1 = 0
;        uword[16] colors2 = 200
;
;        palette.set_rgb(colors1, len(colors1))
;        palette.set_rgb(colors2, len(colors2))
    }
}
