%import textio
%zeropage basicsafe


main {
    sub start()  {

        ubyte ub1 = 100
        ubyte ub2 = 13
        ubyte ubd
        ubyte ubr
        divmod(ub1, ub2, ubd, ubr)
        txt.print_ub(ubd)
        txt.spc()
        txt.print_ub(ubr)
        txt.nl()

        uword uw1 = 10000
        uword uw2 = 900
        uword uwd
        uword uwr
        divmodw(uw1, uw2, uwd, uwr)
        txt.print_uw(uwd)
        txt.spc()
        txt.print_uw(uwr)
        txt.nl()

    }
}
