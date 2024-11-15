%import syslib
%import textio
%import sprites
%import palette
%import math

; X16 version of a balloon sprites flying over a mountain landscape.
; There is also a C64 version of this in the examples.

main {

    sub start() {
        ubyte target_height = txt.DEFAULT_HEIGHT - 10
        ubyte active_height = txt.DEFAULT_HEIGHT
        bool upwards = true
        ubyte draw_column = txt.DEFAULT_WIDTH
        word moon_x = 640
        word balloon_y = 120

        ; clear the screen (including all the tiles outside of the visible area)
        cx16.vaddr(bnk(txt.VERA_TEXTMATRIX), txt.VERA_TEXTMATRIX & $ffff, 0, 1)
        repeat 128 * txt.DEFAULT_HEIGHT {
            cx16.VERA_DATA0 = sc:' '
            cx16.VERA_DATA0 = $00
        }

        ; activate balloon and moon sprites
        sprites.init(1, 0, $0000, sprites.SIZE_32, sprites.SIZE_64, sprites.COLORS_16, 1)
        sprites.init(2, 0, $0400, sprites.SIZE_32, sprites.SIZE_32, sprites.COLORS_16, 2)
        spritedata.copy_to_vram()

        ; Scroll!
        ; Unlike the C64 version, there is no need to copy the whole text matrix 1 character to the left
        ; every 8 pixels. The X16 has a much larger soft scroll register and the displayed tiles wrap around.

        repeat {
            sys.waitvsync()
            cx16.VERA_L1_HSCROLL ++

            if cx16.VERA_L1_HSCROLL & 7 == 0 {

                ; set balloon pos
                if math.rnd() & 1 != 0
                    balloon_y++
                else
                    balloon_y--
                sprites.pos(1, 100, balloon_y)

                ; set moon pos
                moon_x--
                if moon_x < -64
                    moon_x = 640
                sprites.pos(2, moon_x, 20)

                ; update slope height
                ubyte mountain = 223        ; slope upwards
                if active_height < target_height {
                    active_height++
                    upwards = true
                } else if active_height > target_height {
                    mountain = 233          ; slope downwards
                    active_height--
                    upwards = false
                } else {
                    ; determine new height for next mountain
                    ubyte old_height = target_height
                    if upwards {
                        mountain = 233
                        while target_height >= old_height
                            target_height = 28 + (math.rnd() & 31)
                    } else {
                        mountain = 223
                        while target_height <= old_height
                            target_height = 28 + (math.rnd() & 31)
                    }
                }

                ; draw new mountain etc.
                draw_column++
                ubyte yy
                for yy in 0 to active_height-1 {
                    txt.setcc(draw_column, yy, 32, 2)         ; clear top of screen
                }
                txt.setcc(draw_column, active_height, mountain, 8)    ; mountain edge
                for yy in active_height+1 to txt.DEFAULT_HEIGHT-1 {
                    txt.setcc(draw_column, yy, 160, 8)        ; draw filled mountain
                }

                ubyte clutter = math.rnd()
                if clutter > 100 {
                    ; draw a star
                    txt.setcc(draw_column, clutter % (active_height-1), sc:'.', math.rnd())
                }

                if clutter > 200 {
                    ; draw a tree
                    ubyte tree = sc:'↑'
                    ubyte treecolor = 5
                    if clutter & %00010000 != 0
                        tree = sc:'♣'
                    else if clutter & %00100000 != 0
                        tree = sc:'♠'
                    if math.rnd() > 130
                        treecolor = 13
                    txt.setcc(draw_column, active_height, tree, treecolor)
                }

                if clutter > 235 {
                    ; draw a camel
                    txt.setcc(draw_column, active_height, sc:'π', 9)
                }
            }
        }
    }
}

spritedata {
    sub copy_to_vram() {
        cx16.vaddr(0, $0000, 0, 1)
        for cx16.r0 in 0 to 32*64/2-1
            cx16.VERA_DATA0 = @(&balloonsprite + cx16.r0)

        cx16.vaddr(0, $0400, 0, 1)
        for cx16.r0 in 0 to 32*32/2-1
            cx16.VERA_DATA0 = @(&moonsprite + cx16.r0)

        for cx16.r1L in 0 to 15 {
            palette.set_color(cx16.r1L + 16, balloon_pallette[cx16.r1L])
            palette.set_color(cx16.r1L + 32, moon_pallette[cx16.r1L])
        }
    }


    uword[] balloon_pallette = [
        $f0f, $312, $603, $125,
        $717, $721, $332, $a22,
        $268, $d31, $764, $488,
        $d71, $997, $ba7, $eb3
    ]

    uword[] moon_pallette = [
        $f0f, $444, $444, $555,
        $555, $555, $555, $666,
        $777, $777, $888, $888,
        $999, $aaa, $bbb, $ccc
    ]

    balloonsprite:
        %asm {{
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $60, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $05, $99, $92, $55, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $05, $ac, $fc, $cf, $c9, $95, $e3, $25, $11, $40, $00, $00, $00
            .byte  $00, $00, $08, $3c, $ff, $cf, $ff, $c9, $9c, $c8, $37, $21, $33, $00, $00, $00
            .byte  $00, $00, $6b, $af, $ec, $ff, $ff, $9c, $9c, $fa, $33, $97, $23, $30, $00, $00
            .byte  $00, $08, $bd, $ef, $fc, $ff, $fc, $99, $99, $fc, $33, $27, $72, $33, $30, $00
            .byte  $00, $0b, $be, $ff, $cf, $ef, $fc, $9c, $99, $cf, $23, $32, $77, $13, $30, $00
            .byte  $00, $6b, $ee, $ff, $9f, $ef, $fc, $c9, $97, $cf, $d3, $32, $29, $73, $31, $00
            .byte  $00, $f8, $fe, $fc, $ff, $ef, $f9, $9c, $99, $ff, $d8, $31, $27, $72, $33, $00
            .byte  $3b, $fd, $ee, $fc, $ff, $ef, $f9, $9c, $99, $fe, $d3, $32, $24, $72, $33, $40
            .byte  $8b, $bd, $fe, $fc, $ff, $ef, $c7, $c7, $97, $ce, $d8, $33, $22, $72, $33, $30
            .byte  $b8, $bd, $ee, $d7, $db, $b8, $2d, $88, $32, $2b, $83, $33, $33, $33, $33, $10
            .byte  $18, $b8, $bb, $b8, $be, $bb, $3b, $b8, $38, $bb, $b8, $83, $33, $33, $33, $34
            .byte  $1b, $8b, $bb, $8b, $eb, $83, $3b, $b8, $33, $bb, $88, $33, $33, $33, $33, $33
            .byte  $8b, $b8, $8b, $88, $be, $88, $38, $88, $38, $8b, $b8, $33, $33, $33, $33, $33
            .byte  $8b, $88, $88, $8b, $b8, $83, $38, $88, $38, $8b, $88, $33, $33, $33, $33, $33
            .byte  $88, $83, $88, $88, $88, $83, $38, $83, $38, $8b, $83, $33, $33, $33, $33, $33
            .byte  $3b, $b8, $88, $88, $bb, $83, $38, $83, $38, $bb, $83, $33, $33, $33, $33, $33
            .byte  $6b, $bb, $88, $33, $bb, $b3, $38, $33, $33, $8b, $83, $33, $33, $33, $33, $30
            .byte  $1b, $b3, $73, $37, $78, $83, $27, $92, $42, $75, $83, $11, $33, $33, $33, $30
            .byte  $0c, $ff, $79, $97, $9f, $f9, $79, $92, $42, $79, $72, $12, $22, $11, $13, $10
            .byte  $04, $9c, $c9, $97, $9f, $fc, $79, $97, $44, $c7, $72, $12, $12, $12, $22, $00
            .byte  $00, $9c, $c9, $99, $59, $c9, $97, $92, $42, $7c, $21, $21, $22, $12, $21, $00
            .byte  $00, $59, $cc, $99, $59, $9c, $79, $97, $22, $75, $21, $41, $22, $14, $10, $00
            .byte  $00, $19, $cc, $99, $99, $9c, $97, $94, $22, $97, $21, $12, $11, $22, $10, $00
            .byte  $00, $01, $cc, $c9, $95, $99, $97, $77, $47, $92, $11, $22, $12, $21, $00, $00
            .byte  $00, $00, $79, $cc, $97, $7c, $c7, $99, $47, $72, $22, $11, $12, $14, $00, $00
            .byte  $00, $00, $07, $cc, $99, $7c, $fc, $99, $42, $91, $14, $21, $22, $00, $00, $00
            .byte  $00, $00, $01, $7c, $c9, $97, $cc, $79, $27, $72, $21, $12, $10, $00, $00, $00
            .byte  $00, $00, $00, $1a, $c5, $95, $fc, $77, $44, $22, $21, $21, $00, $00, $00, $00
            .byte  $00, $00, $00, $04, $a7, $5c, $fe, $f7, $9c, $55, $52, $20, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $01, $57, $fe, $f9, $9c, $c5, $21, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $01, $5e, $c9, $95, $11, $10, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $12, $ff, $f2, $10, $00, $60, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $01, $ff, $f1, $10, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $01, $12, $11, $00, $0a, $40, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $06, $11, $11, $00, $06, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $15, $15, $10, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $5e, $ee, $ee, $e5, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $01, $7d, $de, $dd, $d2, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $05, $cc, $55, $55, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $05, $cc, $cc, $55, $51, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $05, $cc, $cc, $55, $55, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $05, $5c, $cc, $51, $51, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $05, $cc, $c5, $56, $54, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $05, $55, $55, $15, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00

            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00, $00
        }}

    moonsprite:
        %asm {{
            .byte  $00, $00, $00, $00, $00, $00, $78, $88, $87, $50, $00, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $ba, $99, $9a, $a9, $88, $76, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $b9, $99, $77, $67, $79, $78, $89, $93, $00, $00, $00, $00
            .byte  $00, $00, $00, $76, $69, $ca, $bc, $3b, $99, $78, $66, $99, $60, $00, $00, $00
            .byte  $00, $00, $04, $46, $cb, $64, $67, $77, $cb, $bb, $97, $99, $96, $00, $00, $00
            .byte  $00, $00, $62, $39, $93, $33, $36, $67, $ac, $cc, $79, $99, $98, $60, $00, $00
            .byte  $00, $06, $23, $47, $63, $43, $46, $79, $9b, $c6, $97, $96, $aa, $86, $00, $00
            .byte  $00, $06, $34, $36, $63, $43, $67, $37, $a9, $64, $46, $87, $ba, $98, $30, $00
            .byte  $00, $62, $46, $47, $76, $63, $66, $b7, $c9, $73, $46, $3a, $bc, $c9, $60, $00
            .byte  $00, $13, $39, $73, $33, $74, $36, $99, $b9, $34, $43, $67, $cb, $87, $44, $00
            .byte  $06, $43, $36, $37, $96, $76, $76, $3b, $d7, $34, $63, $48, $9a, $b9, $22, $00
            .byte  $03, $22, $63, $83, $79, $78, $99, $9a, $77, $77, $96, $33, $62, $9c, $22, $10
            .byte  $a3, $32, $36, $77, $bb, $ca, $a7, $63, $63, $97, $92, $33, $22, $9c, $22, $20
            .byte  $a6, $32, $37, $a7, $7b, $eb, $a7, $79, $b7, $7c, $93, $23, $22, $38, $86, $10
            .byte  $cb, $33, $37, $a7, $9b, $99, $33, $7a, $9d, $dc, $93, $33, $23, $36, $67, $10
            .byte  $cb, $23, $33, $77, $79, $99, $67, $76, $8e, $ee, $b7, $33, $63, $43, $22, $10
            .byte  $99, $33, $23, $36, $37, $39, $79, $ca, $bd, $dd, $dd, $a4, $a9, $62, $22, $20
            .byte  $b6, $72, $73, $37, $a7, $99, $7d, $ce, $de, $dd, $df, $97, $ca, $42, $22, $20
            .byte  $bb, $93, $33, $33, $b3, $6b, $37, $d9, $dd, $ed, $df, $c9, $bc, $92, $26, $30
            .byte  $0d, $d7, $39, $33, $73, $77, $77, $9b, $cd, $de, $ed, $ec, $96, $aa, $44, $10
            .byte  $0c, $cb, $9a, $b3, $39, $67, $76, $bc, $dd, $ee, $dd, $fb, $76, $77, $32, $00
            .byte  $0b, $dc, $cb, $33, $ca, $a7, $83, $dd, $cd, $dd, $dd, $dd, $a8, $a7, $a3, $00
            .byte  $00, $de, $dc, $73, $76, $73, $37, $ac, $ed, $ed, $dd, $ec, $bc, $b9, $63, $00
            .byte  $00, $bd, $ed, $b7, $b9, $c7, $99, $ee, $fd, $ec, $de, $ed, $cc, $cb, $70, $00
            .byte  $00, $0c, $ce, $d9, $c7, $9e, $ef, $ff, $ff, $ed, $ed, $db, $ca, $db, $30, $00
            .byte  $00, $0b, $9c, $c7, $cd, $de, $ef, $ff, $fe, $ed, $cc, $bc, $ba, $a7, $00, $00
            .byte  $00, $00, $bd, $9d, $dd, $de, $ff, $ff, $fe, $ee, $ec, $cc, $c9, $60, $00, $00
            .byte  $00, $00, $0b, $ca, $ac, $ee, $ff, $ff, $fe, $cc, $cd, $cd, $96, $00, $00, $00
            .byte  $00, $00, $00, $ba, $9d, $cc, $fe, $ff, $fd, $cc, $cc, $97, $60, $00, $00, $00
            .byte  $00, $00, $00, $00, $9c, $da, $dd, $dc, $cd, $db, $a9, $83, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $08, $aa, $bd, $dc, $ac, $a9, $96, $00, $00, $00, $00, $00
            .byte  $00, $00, $00, $00, $00, $00, $59, $7b, $99, $60, $00, $00, $00, $00, $00, $00
        }}
}
