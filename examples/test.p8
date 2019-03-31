%zeropage basicsafe
%option enable_floats

%import c64flt

~ main {

    sub start() {
        ubyte ub=2
        ubyte ub2=7
        uword uw=2
        uword uw2=5
        float fl=2.3
        float fl2=20


        fl = (ub as float) ** 4
        fl = 2.3
        fl = fl ** 20.0
        c64flt.print_f(fl)
        c64.CHROUT('\n')

        fl = 2.3
        fl = fl ** fl2
        c64flt.print_f(fl)
        c64.CHROUT('\n')

        fl = 2.3
        fl **=20.0
        c64flt.print_f(fl)
        c64.CHROUT('\n')

    }

}
