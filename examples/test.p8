;%import c64lib
;%import c64graphics
;%import c64textio
;%import c64flt
;%option enable_floats
%target cx16
%import cx16textio
%zeropage basicsafe


main {

    sub start()  {

        const uword cvalue = 155
        const uword cvalue2 = 5555
        uword wvalue = 155
        uword wvalue2 = 5555

        ; TODO ALL multiplications below should yield a word result
        uword x
        ubyte bb = 9
        x = bb * cvalue     ; TODO wrong result, must be word
        txt.print_uw(x)
        txt.chrout('\n')
        x = bb * cvalue2
        txt.print_uw(x)
        txt.chrout('\n')
        x = bb * wvalue
        txt.print_uw(x)
        txt.chrout('\n')
        x = bb * wvalue2
        txt.print_uw(x)
        txt.chrout('\n')

        x = cvalue * bb     ; TODO wrong result, must be word
        txt.print_uw(x)
        txt.chrout('\n')
        x = cvalue2 * bb
        txt.print_uw(x)
        txt.chrout('\n')
        x = wvalue * bb
        txt.print_uw(x)
        txt.chrout('\n')
        x = wvalue2 * bb
        txt.print_uw(x)
        txt.chrout('\n')
    }
}
