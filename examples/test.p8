%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte uw = 97
        txt.print_ub( 10 * (uw/10) )    ; 90
        txt.nl()
        txt.print_ub( (uw/10) * 10 )    ; 90
        txt.nl()

        float fl = 999.876
        floats.print_f( 10 * (fl/10) )    ; 999.876
        txt.nl()
        floats.print_f( (fl/10) * 10 )    ; 999.876
        txt.nl()

    }
}
