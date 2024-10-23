%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        byte @shared ww = -99
        ww++

        if ww&1 ==0
            txt.print("x ")

        if ww&64 ==0
            txt.print("a ")

        if ww&128!=0
            txt.print("neg ")

        txt.print_ub(if ww & 64==0  111 else 222)
        txt.spc()
        txt.print_ub(if ww & 128!=0  111 else 222)
        txt.spc()
    }
}
