%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {

        float f1

        ; these are all floating point constants defined in the ROM so no allocation required
        ; TODO actually read these from ROM

        f1 = 3.141592653589793
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = -32768.0
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = 1.0
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = 0.7071067811865476
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = 1.4142135623730951
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = -0.5
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = 0.6931471805599453
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = 10.0
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = 1.0e9
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = 0.5
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = 1.4426950408889634
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = 1.5707963267948966
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = 6.283185307179586
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = 0.25
        c64flt.print_f(f1)
        c64.CHROUT('\n')

        f1 = 0.0
        c64flt.print_f(f1)
        c64.CHROUT('\n')
    }
}
