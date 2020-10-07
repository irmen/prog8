%import textio
%import syslib
%zeropage basicsafe


main {

    str planet_name = "12345678"
    sub start() {

        uword[] warray = [1,2,3,4,5]

        uword sums
        ubyte ii
        uword ww
        uword wptr = &warray
        &uword wmap = $c000

        wmap += ii
        wmap <<= ii
        wmap >>= ii

        txt.print(planet_name)
        txt.chrout('\n')

        planet_name = "saturn"

        txt.print(planet_name)
        txt.chrout('\n')
        txt.print_ub(len(planet_name))
        txt.chrout('\n')
        txt.print_ub(strlen(planet_name))
        txt.chrout('\n')
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



