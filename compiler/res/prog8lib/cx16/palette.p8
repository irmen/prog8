; Manipulate the Commander X16's display color palette.
; Should you want to restore the default palette, you have to reinitialize the Vera yourself.

palette {
    %option no_symbol_prefixing

    uword vera_palette_ptr
    ubyte cc

    sub set_color(ubyte index, uword color) {
        vera_palette_ptr = $fa00+(index as uword * 2)
        cx16.vpoke(1, vera_palette_ptr, lsb(color))
        vera_palette_ptr++
        cx16.vpoke(1, vera_palette_ptr, msb(color))
    }

    sub set_rgb_be(uword palette_ptr, uword num_colors) {
        ; 1 word per color entry, $0rgb in big endian format
        vera_palette_ptr = $fa00
        repeat num_colors {
            cx16.vpoke(1, vera_palette_ptr+1, @(palette_ptr))
            palette_ptr++
            cx16.vpoke(1, vera_palette_ptr, @(palette_ptr))
            palette_ptr++
            vera_palette_ptr+=2
        }
    }

    sub set_rgb(uword palette_words_ptr, uword num_colors) {
        ; 1 word per color entry (in little endian format as layed out in video memory, so $gb;$0r)
        vera_palette_ptr = $fa00
        repeat num_colors*2 {
            cx16.vpoke(1, vera_palette_ptr, @(palette_words_ptr))
            palette_words_ptr++
            vera_palette_ptr++
        }
    }

    sub set_rgb8(uword palette_bytes_ptr, uword num_colors) {
        ; 3 bytes per color entry, adjust color depth from 8 to 4 bits per channel.
        vera_palette_ptr = $fa00
        ubyte red
        ubyte greenblue
        repeat num_colors {
            red = @(palette_bytes_ptr) >> 4
            palette_bytes_ptr++
            greenblue = @(palette_bytes_ptr) & %11110000
            palette_bytes_ptr++
            greenblue |= @(palette_bytes_ptr) >> 4    ; add Blue
            palette_bytes_ptr++
            cx16.vpoke(1, vera_palette_ptr, greenblue)
            vera_palette_ptr++
            cx16.vpoke(1, vera_palette_ptr, red)
            vera_palette_ptr++
        }
    }

    sub set_monochrome(uword screencolorRGB, uword drawcolorRGB) {
        vera_palette_ptr = $fa00
        cx16.vpoke(1, vera_palette_ptr, lsb(screencolorRGB))   ; G,B
        vera_palette_ptr++
        cx16.vpoke(1, vera_palette_ptr, msb(screencolorRGB))   ; R
        vera_palette_ptr++
        repeat 255 {
            cx16.vpoke(1, vera_palette_ptr, lsb(drawcolorRGB)) ; G,B
            vera_palette_ptr++
            cx16.vpoke(1, vera_palette_ptr, msb(drawcolorRGB)) ; R
            vera_palette_ptr++
        }
    }

    sub set_all_black() {
        set_monochrome($000, $000)
    }

    sub set_all_white() {
        set_monochrome($fff, $fff)
    }

    sub set_grayscale() {
        vera_palette_ptr = $fa00
        repeat 16 {
            cc=0
            repeat 16 {
                cx16.vpoke(1, vera_palette_ptr, cc)
                vera_palette_ptr++
                cx16.vpoke(1, vera_palette_ptr, cc)
                vera_palette_ptr++
                cc += $11
            }
        }
    }

    uword[] C64_colorpalette_dark = [   ; this is a darker palette with more contrast
        $000,  ; 0 = black
        $FFF,  ; 1 = white
        $632,  ; 2 = red
        $7AB,  ; 3 = cyan
        $638,  ; 4 = purple
        $584,  ; 5 = green
        $327,  ; 6 = blue
        $BC6,  ; 7 = yellow
        $642,  ; 8 = orange
        $430,  ; 9 = brown
        $965,  ; 10 = light red
        $444,  ; 11 = dark grey
        $666,  ; 12 = medium grey
        $9D8,  ; 13 = light green
        $65B,  ; 14 = light blue
        $999   ; 15 = light grey
    ]

    uword[] C64_colorpalette_pepto = [  ; # this is Pepto's Commodore-64 palette  http://www.pepto.de/projects/colorvic/
        $000,  ; 0 = black
        $FFF,  ; 1 = white
        $833,  ; 2 = red
        $7cc,  ; 3 = cyan
        $839,  ; 4 = purple
        $5a4,  ; 5 = green
        $229,  ; 6 = blue
        $ef7,  ; 7 = yellow
        $852,  ; 8 = orange
        $530,  ; 9 = brown
        $c67,  ; 10 = light red
        $444,  ; 11 = dark grey
        $777,  ; 12 = medium grey
        $af9,  ; 13 = light green
        $76e,  ; 14 = light blue
        $bbb   ; 15 = light grey
    ]

    uword[] C64_colorpalette_light = [  ; this is a lighter palette
        $000,  ; 0 = black
        $FFF,  ; 1 = white
        $944,  ; 2 = red
        $7CC,  ; 3 = cyan
        $95A,  ; 4 = purple
        $6A5,  ; 5 = green
        $549,  ; 6 = blue
        $CD8,  ; 7 = yellow
        $963,  ; 8 = orange
        $650,  ; 9 = brown
        $C77,  ; 10 = light red
        $666,  ; 11 = dark grey
        $888,  ; 12 = medium grey
        $AE9,  ; 13 = light green
        $87C,  ; 14 = light blue
        $AAA   ; 15 = light grey
    ]

    sub set_c64pepto() {
        vera_palette_ptr = $fa00
        repeat 16 {
            for cc in 0 to 15 {
                uword ccp = C64_colorpalette_pepto[cc]
                cx16.vpoke(1, vera_palette_ptr, lsb(ccp))     ; G, B
                vera_palette_ptr++
                cx16.vpoke(1, vera_palette_ptr, msb(ccp))     ; R
                vera_palette_ptr++
            }
        }
    }

    sub set_c64light() {
        vera_palette_ptr = $fa00
        repeat 16 {
            for cc in 0 to 15 {
                uword ccp = C64_colorpalette_light[cc]
                cx16.vpoke(1, vera_palette_ptr, lsb(ccp))     ; G, B
                vera_palette_ptr++
                cx16.vpoke(1, vera_palette_ptr, msb(ccp))     ; R
                vera_palette_ptr++
            }
        }
    }

    sub set_c64dark() {
        vera_palette_ptr = $fa00
        repeat 16 {
            for cc in 0 to 15 {
                uword ccp = C64_colorpalette_dark[cc]
                cx16.vpoke(1, vera_palette_ptr, lsb(ccp))     ; G, B
                vera_palette_ptr++
                cx16.vpoke(1, vera_palette_ptr, msb(ccp))     ; R
                vera_palette_ptr++
            }
        }
    }

}
