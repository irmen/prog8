%target cx16

c64colors {
    uword[] colorpalette_dark = [   ; this is a darker palette with more contrast
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
        $999  ; 15 = light grey
    ]

    uword[] colorpalette_pepto = [  ; # this is Pepto's Commodore-64 palette  http://www.pepto.de/projects/colorvic/
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
        $bbb  ; 15 = light grey
    ]

    uword[] colorpalette_light = [  ; this is a lighter palette
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

    ubyte color

    sub set_palette_pepto() {
        for color in 0 to 15 {
            uword cc = colorpalette_pepto[color]
            cx16.vpoke(1, $fa00 + color*2, lsb(cc))     ; G, B
            cx16.vpoke(1, $fa01 + color*2, msb(cc))     ; R
        }
    }

    sub set_palette_light() {
        for color in 0 to 15 {
            uword cc = colorpalette_light[color]
            cx16.vpoke(1, $fa00 + color*2, lsb(cc))     ; G, B
            cx16.vpoke(1, $fa01 + color*2, msb(cc))     ; R
        }
    }

    sub set_palette_dark() {
        for color in 0 to 15 {
            uword cc = colorpalette_dark[color]
            cx16.vpoke(1, $fa00 + color*2, lsb(cc))     ; G, B
            cx16.vpoke(1, $fa01 + color*2, msb(cc))     ; R
        }
    }

}
