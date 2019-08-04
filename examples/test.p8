%import c64utils
%import c64flt
%option enable_floats
%zeropage basicsafe     ; @todo dontuse

main {


    sub start()  {

        ubyte ub=200
        byte bb=-100
        uword uw = 2000
        word ww = -1000
        float fl = 99.99

        c64scr.print("++\n")
        ub++
        bb++
        uw++
        ww++
        fl++

        check_ub(ub, 201)
        Y=100
        Y++
        check_ub(Y, 101)
        check_fl(fl, 100.99)
        check_b(bb, -99)
        check_uw(uw, 2001)
        check_w(ww, -999)

        c64scr.print("--\n")
        ub--
        bb--
        uw--
        ww--
        fl--
        check_ub(ub, 200)
        Y=100
        Y--
        check_ub(Y, 99)
        check_fl(fl, 99.99)
        check_b(bb, -100)
        check_uw(uw, 2000)
        check_w(ww, -1000)

        @($0400+39) = X
    }

    sub check_ub(ubyte value, ubyte expected) {
        if value==expected
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print(" ubyte ")
        c64scr.print_ub(value)
        c64.CHROUT(',')
        c64scr.print_ub(expected)
        c64.CHROUT('\n')
    }

    sub check_b(byte value, byte expected) {
        if value==expected
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print(" byte ")
        c64scr.print_b(value)
        c64.CHROUT(',')
        c64scr.print_b(expected)
        c64.CHROUT('\n')
    }

    sub check_uw(uword value, uword expected) {
        if value==expected
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print(" uword ")
        c64scr.print_uw(value)
        c64.CHROUT(',')
        c64scr.print_uw(expected)
        c64.CHROUT('\n')
    }

    sub check_w(word value, word expected) {
        if value==expected
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print(" word ")
        c64scr.print_w(value)
        c64.CHROUT(',')
        c64scr.print_w(expected)
        c64.CHROUT('\n')
    }

    sub check_fl(float value, float expected) {
        if value==expected
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print(" float ")
        c64flt.print_f(value)
        c64.CHROUT(',')
        c64flt.print_f(expected)
        c64.CHROUT('\n')
    }
}
