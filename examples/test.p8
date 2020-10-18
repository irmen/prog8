%import textio
%import conv
%import floats
%zeropage basicsafe

main {

    sub start() {

        uword zc

        zc = 99
        scolor2=scolor

        ; TODO WHy does this compile with stack eval:
        ubyte scolor = (zc>>13) as ubyte + 4

        ; TODO this is more optimized:
        ubyte scolor2
        scolor2 = (zc>>13) as ubyte + 4

        scolor2=scolor

        testX()
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
