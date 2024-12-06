%import textio
%import palette
%zeropage basicsafe
%option no_sysinit

main {
    uword[] @shared colors = [
        $000,   ; 0 = black
        $fff,   ; 1 = white
        $800,   ; 2 = red
        $afe,   ; 3 = cyan
        $c4c,   ; 4 = purple
        $0c5,   ; 5 = green
        $00a,   ; 6 = blue
        $ee7,   ; 7 = yellow
        $d85,   ; 8 = orange
        $640,   ; 9 = brown
        $f77,   ; 10 = light red
        $333,   ; 11 = dark grey
        $777,   ; 12 = medium grey
        $af6,   ; 13 = light green
        $08f,   ; 14 = light blue
        $bbb    ; 15 = light grey
    ]

    ubyte[] @shared colors_b = [
        $00, $00,    ; 0 = black
        $0f, $ff,    ; 1 = white
        $08, $00,    ; 2 = red
        $0a, $fe,    ; 3 = cyan
        $0c, $4c,    ; 4 = purple
        $00, $c5,    ; 5 = green
        $00, $0a,    ; 6 = blue
        $0e, $e7,    ; 7 = yellow
        $0d, $85,    ; 8 = orange
        $06, $40,    ; 9 = brown
        $0f, $77,    ; 10 = light red
        $03, $33,    ; 11 = dark grey
        $07, $77,    ; 12 = medium grey
        $0a, $f6,    ; 13 = light green
        $00, $8f,    ; 14 = light blue
        $0b, $bb    ; 15 = light grey
    ]


    sub start() {
        palette.set_grayscale(0)
        sys.wait(60)
        palette.set_rgb_be(colors_b, 16, 0)
    }
}
