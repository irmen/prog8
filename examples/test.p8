%import textio
%import math
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte ub1 = 12
        ubyte ub2 = 233

        txt.print_ub(math.diff(ub1, ub2))
        txt.nl()
        ub1 = 200
        ub2 = 90
        txt.print_ub(math.diff(ub1, ub2))
        txt.nl()
        ub1 = 144
        ub2 = 144
        txt.print_ub(math.diff(ub1, ub2))
        txt.nl()
        txt.nl()


        uword uw1 = 1200
        uword uw2 = 40000
        txt.print_uw(math.diffw(uw1, uw2))
        txt.nl()
        uw1 = 40000
        uw2 = 21000
        txt.print_uw(math.diffw(uw1, uw2))
        txt.nl()
        uw1 = 30000
        uw2 = 30000
        txt.print_uw(math.diffw(uw1, uw2))
        txt.nl()
    }
}
