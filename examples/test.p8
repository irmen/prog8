%import textio
%zeropage basicsafe

main {
    ; Test the routine
    sub start() {
        uword @shared u1, u2, u3, u4
        long @shared long1, long2
        u2 = lsw(long1 - long2)
        u1 = msw(long1 - long2)


        u1 = 40000
        u2 = 165

        u3, u4 = multi()
        u3, u4 = divmod(u1, u2)
        txt.print_uw(u3)
        txt.spc()
        txt.print_uw(u4)
        txt.nl()
    }

    sub multi() -> uword, uword {
        return 99, 100
    }
}
