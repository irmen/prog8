%import textio
%import diskio
%zeropage basicsafe

main {

    sub start() {

        dinges.travel_to(5)
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


dinges {

        sub foo(ubyte x) {
        }

        sub travel_to(ubyte d2) {
            ubyte travel_to=d2
            foo(travel_to)
        }
    }
