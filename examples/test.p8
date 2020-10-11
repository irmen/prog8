%import textio
%import syslib
%import floats
%zeropage basicsafe


main {

    sub start() {

        str name = "irmen de jong"
        uword strptr = &name

        txt.print("ubyte? ")
        void txt.input_chars(name)
        ubyte ub = conv.str2ubyte(name)
        txt.print_ub(ub)

        txt.print("\nbyte? ")
        void txt.input_chars(name)
        byte bb = conv.str2byte(name)
        txt.print_b(bb)

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



