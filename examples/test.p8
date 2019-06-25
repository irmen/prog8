%import c64utils
%zeropage basicsafe

~ main {

    sub start() {
        Y=99
        A=200
        Y=A
        ubyte r = subt()
        c64scr.print_ub(r)
        c64.CHROUT('\n')
    }

    sub subt() -> ubyte {

        for Y in 20 to 50 step 5 {
            c64scr.print_ub(Y)
            c64.CHROUT(',')
            if Y>40
                return 99
        }
        c64.CHROUT('\n')
        return 10
    }
}
