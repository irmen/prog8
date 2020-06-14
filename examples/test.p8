%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe



main {

    sub jumpsub() {

        ; goto jumpsub        ; TODO fix compiler loop
        goto blabla
blabla:
        A=99
        return

    }

    sub func(ubyte arg) -> ubyte {
        c64.CHROUT(arg)
        return sin8(arg)
    }

    sub func2(ubyte arg) -> ubyte {
        return sin8(arg)
    }

    sub start() {
        ubyte[] array = [1, 1+1, -33, -2.222]

        const ubyte q = 123
        byte bbb

        str sss = "zzz"
        str x = "zxcvzxcv"

        float ff = 1234.44 + 99.0
        float ff2 = -3.3333
        ff=ff2*2
        A = 123+22+11
        bbb = -99

        A = q
        A = func(1+q)
        func(1+q)

        Y=99
        A = func2(1+q)
        func2(1+q)

        x = @(&sss)
    }

}


