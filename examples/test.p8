%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte uw = 97
        txt.print_ub( 10 * (uw/10) )    ; 90
        txt.nl()
        txt.print_ub( (uw/10) * 10 )    ; 90
        txt.nl()
    }
}
