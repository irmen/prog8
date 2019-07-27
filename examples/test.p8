%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {

        float f1 =  1.1
        float f2 = 2.2

        @(1024) = f1==f2

        c64.CHROUT('\n')
        c64scr.print_ub(f1==f2)
        c64.CHROUT('\n')

        if f1 ==0.0
            c64scr.print("error\n")
        else
            c64scr.print("ok\n")
    }
}
