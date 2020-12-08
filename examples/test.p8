%import textio
%import diskio
%import floats
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {
    sub start() {
        ubyte[] barr = [0,0,0]
        uword[] warr = [0,0,0]

        ubyte xx = 0
        barr[1] = xx+9
        warr[1] = &warr

        txt.print_ub(barr[1])
        txt.chrout('\n')
        txt.print_uwhex(warr[1],1 )
        txt.chrout('\n')

        test_stack.test()
    }
}
