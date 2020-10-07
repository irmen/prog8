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
        ubyte wmap

        ii = 2

        wmap = %11110000
        wmap >>= 3
        txt.print_ubbin(wmap, 1)
        txt.chrout('\n')
        wmap <<= 3
        txt.print_ubbin(wmap, 1)
        txt.chrout('\n')

        wmap = 9
        wmap *= 17
        txt.print_ub(wmap)
        txt.chrout('\n')
        wmap /= 17
        txt.print_ub(wmap)
        txt.chrout('\n')
        wmap = 211
        wmap %= 40
        txt.print_ub(wmap)
        txt.chrout('\n')
        txt.chrout('\n')

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



