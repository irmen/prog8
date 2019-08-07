%import c64utils
%import c64lib
%zeropage dontuse

main {

    sub start() {

        uword uw

        uw = c64utils.str2uword("12345")
        c64scr.print_uw(uw)
        c64.CHROUT('\n')

        uw = c64utils.str2uword("11")
        c64scr.print_uw(uw)
        c64.CHROUT('\n')
    }
}
