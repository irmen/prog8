%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats


main {

    sub start() {
        ubyte i
        for i in 0 to 20 {
            c64scr.print_ub(i)
            c64.CHROUT('\n')
        }
     }
}
