%import c64utils
%import c64lib
%import c64flt
%zeropage dontuse


main {

    sub start() {

        ubyte ub1 = 123
        ubyte ub2 = 222
        uword uw = 1111
        uword uw2 = 2222
        word[] warr = [1111, 2222]
        float[] farr = [1.111, 2.222]

        c64scr.print_ub(ub1)
        c64.CHROUT(',')
        c64scr.print_ub(ub2)
        c64.CHROUT('\n')
        c64scr.print_uw(uw)
        c64.CHROUT(',')
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        c64scr.print_w(warr[0])
        c64.CHROUT(',')
        c64scr.print_w(warr[1])
        c64.CHROUT('\n')
        c64flt.print_f(farr[0])
        c64.CHROUT(',')
        c64flt.print_f(farr[1])
        c64.CHROUT('\n')

        swap(ub1, ub2)
        swap(uw,uw2)
        swap(warr[0], warr[1])
        swap(farr[0], farr[1])      ; TODO CRASHES

        c64scr.print_ub(ub1)
        c64.CHROUT(',')
        c64scr.print_ub(ub2)
        c64.CHROUT('\n')
        c64scr.print_uw(uw)
        c64.CHROUT(',')
        c64scr.print_uw(uw2)
        c64.CHROUT('\n')
        c64scr.print_w(warr[0])
        c64.CHROUT(',')
        c64scr.print_w(warr[1])
        c64.CHROUT('\n')
        c64flt.print_f(farr[0])
        c64.CHROUT(',')
        c64flt.print_f(farr[1])
        c64.CHROUT('\n')
    }
}
