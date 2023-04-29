%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        byte v1s = 22
        byte v2s = -99
        word ww

        txt.print_w(minsb())        ; TODO WRONG RESULT!
        txt.spc()
        ww = minsb()
        txt.print_w(ww)
        txt.spc()
        txt.print_b(minsb())
        txt.spc()
        v2s = minsb()
        txt.print_w(v2s)

        sub minsb() -> byte {
            cx16.r0++
            return v2s
        }
    }

}

