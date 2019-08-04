%import c64utils
%import c64flt
%option enable_floats
%zeropage basicsafe

main {


    sub start()  {

        c64scr.plot(0,24)

        ubyte ub=200
        byte bb=-100
        uword uw = 2000
        word ww = -1000
        float fl = 999.99
        ubyte[3] ubarr = 200
        byte[3] barr = -100
        uword[3] uwarr = 2000
        word[3] warr = -1000
        float[3] flarr = 999.99

        c64scr.print("++\n")
        ub++
        bb++
        uw++
        ww++
        fl++
        ubarr[1]++
        barr[1]++
        uwarr[1]++
        warr[1]++
        flarr[1] ++

        check_ub(ub, 201)
        Y=100
        Y++
        check_ub(Y, 101)
        check_fl(fl, 1000.99)
        check_b(bb, -99)
        check_uw(uw, 2001)
        check_w(ww, -999)
        check_ub(ubarr[0], 200)
        check_fl(flarr[0], 999.99)
        check_b(barr[0], -100)
        check_uw(uwarr[0], 2000)
        check_w(warr[0], -1000)
        check_ub(ubarr[1], 201)
        check_fl(flarr[1], 1000.99)
        check_b(barr[1], -99)
        check_uw(uwarr[1], 2001)
        check_w(warr[1], -999)

        c64scr.print("--\n")
        ub--
        bb--
        uw--
        ww--
        fl--
        ubarr[1]--
        barr[1]--
        uwarr[1]--
        warr[1]--
        flarr[1] --
        check_ub(ub, 200)
        Y=100
        Y--
        check_ub(Y, 99)
        check_fl(fl, 999.99)
        check_b(bb, -100)
        check_uw(uw, 2000)
        check_w(ww, -1000)
        check_ub(ubarr[1], 200)
        check_fl(flarr[1], 999.99)
        check_b(barr[1], -100)
        check_uw(uwarr[1], 2000)
        check_w(warr[1], -1000)

        @($0400+400-1) = X
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
