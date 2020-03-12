%import c64flt
%zeropage basicsafe
%option enable_floats

main {

    sub start() {

        ; these are all floating point constants defined in the ROM so no allocation required

        c64flt.print_f(3.141592653589793)
        c64.CHROUT('\n')

        c64flt.print_f(-32768.0)
        c64.CHROUT('\n')

        c64flt.print_f( 1.0)
        c64.CHROUT('\n')

        c64flt.print_f(0.7071067811865476)
        c64.CHROUT('\n')

        c64flt.print_f(1.4142135623730951)
        c64.CHROUT('\n')

        c64flt.print_f( -0.5)
        c64.CHROUT('\n')

        c64flt.print_f(0.6931471805599453)
        c64.CHROUT('\n')

        c64flt.print_f(10.0)
        c64.CHROUT('\n')

        c64flt.print_f(1.0e9)
        c64.CHROUT('\n')

        c64flt.print_f(0.5)
        c64.CHROUT('\n')

        c64flt.print_f(1.4426950408889634)
        c64.CHROUT('\n')

        c64flt.print_f(1.5707963267948966)
        c64.CHROUT('\n')

        c64flt.print_f(6.283185307179586)
        c64.CHROUT('\n')

        c64flt.print_f(0.25)
        c64.CHROUT('\n')

        c64flt.print_f(0.0)
        c64.CHROUT('\n')

        check_eval_stack()
    }


    sub check_eval_stack() {
        if X!=255 {
            c64scr.print("stack x=")
            c64scr.print_ub(X)
            c64scr.print(" error!\n")
        }
    }

}
