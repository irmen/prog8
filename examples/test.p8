%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte[] ubarray = [11,22,33]
        uword[] uwarray = [1111,2222,3333]
        uword[] @split suwarray = [1111,2222,3333]

        ubarray[1] *= 10
        uwarray[1] *= 10
        suwarray[1] *= 10

        txt.print_ub(ubarray[1])
        txt.nl()
        txt.print_uw(uwarray[1])
        txt.nl()
        txt.print_uw(suwarray[1])
        txt.nl()
    }
}
