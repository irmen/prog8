%import textio
;%import diskio
;%import floats
;%import graphics
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {

    ;romsub $ff14 = FB_set_8_pixels_opaque(ubyte pattern @R0, ubyte mask @A, ubyte color1 @X, ubyte color2 @Y)  clobbers(A,X,Y)
    ;romsub $ff14 = FB_set_8_pixels_opaque_OLD(ubyte mask @A, ubyte color1 @X, ubyte color2 @Y)  clobbers(A,X,Y)

    asmsub set_8_pixels_opaque(ubyte pattern @R0, ubyte mask @A, ubyte color1 @X, ubyte color2 @Y)  clobbers(A,X,Y) {

        %asm {{
            sta  _a
            stx  _x
            sty  _y

            lda  _a
            jsr  txt.print_ub
            lda  #13
            jsr  c64.CHROUT
            lda  _x
            jsr  txt.print_ub
            lda  #13
            jsr  c64.CHROUT
            lda  _y
            jsr  txt.print_ub
            lda  #13
            jsr  c64.CHROUT
            lda  cx16.r0
            ldy  cx16.r0+1
            jsr  txt.print_uw
            lda  #13
            jsr  c64.CHROUT
            rts

_a  .byte 0
_x  .byte 0
_y  .byte 0
        }}
    }

    asmsub set_8_pixels_opaque_OLD(ubyte mask @A, ubyte color1 @X, ubyte color2 @Y)  clobbers(A,X,Y) {
        %asm {{
            sta  _a
            stx  _x
            sty  _y

            lda  _a
            jsr  txt.print_ub
            lda  #13
            jsr  c64.CHROUT
            lda  _x
            jsr  txt.print_ub
            lda  #13
            jsr  c64.CHROUT
            lda  _y
            jsr  txt.print_ub
            lda  #13
            jsr  c64.CHROUT
            rts

_a  .byte 0
_x  .byte 0
_y  .byte 0
        }}
    }

    sub start () {
;        cx16.r0 = 65535
;        set_8_pixels_opaque_OLD(111,222,33)
;        txt.chrout('\n')
        ubyte bb = 44
        set_8_pixels_opaque(bb,111,222,33)
        txt.chrout('\n')
        set_8_pixels_opaque(c64.CHRIN(),111,222,33)
        txt.chrout('\n')

        test_stack.test()
    }
}
