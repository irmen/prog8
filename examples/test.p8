%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats


main {

    sub start() {

        repeat 10 {
            c64.CHROUT('*')
        }
        c64.CHROUT('\n')

        ubyte ub = 9
        repeat ub {
            c64.CHROUT('*')
        }
        c64.CHROUT('\n')

        repeat 320 {
            c64.CHROUT('+')
        }
        c64.CHROUT('\n')

        uword uw = 320
        repeat uw {
            c64.CHROUT('-')
        }
        c64.CHROUT('\n')

        ub = 7
        repeat ub+2 {
            c64.CHROUT('*')
        }
        c64.CHROUT('\n')

        uw = 318
        repeat uw+2 {
            c64.CHROUT('*')
        }
        c64.CHROUT('\n')
    }
}
