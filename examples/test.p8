%import textio
%zeropage basicsafe

main {
    sub start() {
        bool aa = true
        ubyte[] bb = [%0000, %1111]
        uword w1 = %1000000000000001
        uword w2 = %0000000000000010

        if aa and w1 | w2
            txt.print("ok")
        else
            txt.print("fail")
        txt.spc()

        if aa and w1 & w2
            txt.print("fail")
        else
            txt.print("ok")
        txt.spc()

        if aa and bb[0] | %0100
            txt.print("ok")
        else
            txt.print("fail")
        txt.spc()

        if aa and bb[0] & %0100
            txt.print("fail")
        else
            txt.print("ok")
        txt.spc()

        aa = aa and bb[0] | %0100
        txt.print_ub(aa)
        txt.spc()
        aa = aa and bb[0] & %0100
        txt.print_ub(aa)
    }
}
