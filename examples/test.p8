%import textio
%import conv
%import floats
%zeropage basicsafe

main {

    sub start() {

        word zz
        uword uu
        ubyte ub
        byte bb

        bb = -111
        bb >>= 0
        txt.print_ubbin(bb as ubyte, true)
        txt.chrout('\n')
        bb = -111
        bb >>= 1
        txt.print_ubbin(bb as ubyte, true)
        txt.chrout('\n')
        bb = -111
        bb >>= 2
        txt.print_ubbin(bb as ubyte, true)
        txt.chrout('\n')
        bb = -111
        bb >>= 3
        txt.print_ubbin(bb as ubyte, true)
        txt.chrout('\n')
        bb = -111
        bb >>= 4
        txt.print_ubbin(bb as ubyte, true)
        txt.chrout('\n')
        bb = -111
        bb >>= 5
        txt.print_ubbin(bb as ubyte, true)
        txt.chrout('\n')
        bb = -111
        bb >>= 6
        txt.print_ubbin(bb as ubyte, true)
        txt.chrout('\n')
        bb = -111
        bb >>= 7
        txt.print_ubbin(bb as ubyte, true)
        txt.chrout('\n')
        bb = -111
        bb >>= 8
        txt.print_ubbin(bb as ubyte, true)
        txt.chrout('\n')
        bb = -111
        bb >>= 9
        txt.print_ubbin(bb as ubyte, true)
        txt.chrout('\n')

;        for ub in 0 to 8 {
;            bb = 111
;            bb >>= ub
;            txt.print_ubbin(bb as ubyte, true)
;            txt.chrout('\n')
;        }
;        txt.chrout('\n')
;
;        for ub in 0 to 8 {
;            bb = -111
;            bb >>= ub
;            txt.print_ubbin(bb as ubyte, true)
;            txt.chrout('\n')
;        }
;        txt.chrout('\n')

;        ub >>= 7
;        ub <<= 7
;
;        uu >>=7
;        uu <<= 7
;
;        zz >>=7
;        zz <<=7
;
;        bb >>=7
;        bb <<=7

        ; testX()
    }

    asmsub testX() {
        %asm {{
            stx  _saveX
            lda  #13
            jsr  txt.chrout
            lda  _saveX
            jsr  txt.print_ub
            lda  #13
            jsr  txt.chrout
            ldx  _saveX
            rts
_saveX   .byte 0
        }}
    }
}
