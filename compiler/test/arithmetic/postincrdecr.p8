%import floats
%import textio
%zeropage basicsafe

main {

    sub start()  {

        txt.plot(0,24)

        ubyte y
        ubyte ub=200
        byte bb=-100
        uword uw = 2000
        word ww = -1000
        float fl = 999.99
        ubyte[3] ubarr = [200]*3
        byte[3] barr = [-100]*3
        uword[3] uwarr = [2000]*3
        word[3] warr = [-1000]*3
        float[3] flarr = [999.99]*3

        txt.print("++\n")
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
        y=100
        y++
        check_ub(y, 101)
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

        txt.print("--\n")
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

        y=100
        y--
        check_ub(y, 99)
        check_fl(fl, 999.99)
        check_b(bb, -100)
        check_uw(uw, 2000)
        check_w(ww, -1000)
        check_ub(ubarr[1], 200)
        check_fl(flarr[1], 999.99)
        check_b(barr[1], -100)
        check_uw(uwarr[1], 2000)
        check_w(warr[1], -1000)
    }

    sub check_ub(ubyte value, ubyte expected) {
        if value==expected
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print(" ubyte ")
        txt.print_ub(value)
        cbm.CHROUT(',')
        txt.print_ub(expected)
        cbm.CHROUT('\n')
    }

    sub check_b(byte value, byte expected) {
        if value==expected
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print(" byte ")
        txt.print_b(value)
        cbm.CHROUT(',')
        txt.print_b(expected)
        cbm.CHROUT('\n')
    }

    sub check_uw(uword value, uword expected) {
        if value==expected
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print(" uword ")
        txt.print_uw(value)
        cbm.CHROUT(',')
        txt.print_uw(expected)
        cbm.CHROUT('\n')
    }

    sub check_w(word value, word expected) {
        if value==expected
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print(" word ")
        txt.print_w(value)
        cbm.CHROUT(',')
        txt.print_w(expected)
        cbm.CHROUT('\n')
    }

    sub check_fl(float value, float expected) {
        if value==expected
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print(" float ")
        floats.print(value)
        cbm.CHROUT(',')
        floats.print(expected)
        cbm.CHROUT('\n')
    }
}
