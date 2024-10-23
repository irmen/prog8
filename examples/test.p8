%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        word @shared ww = 1234

        txt.print_ub(if ww==0  111 else 222)
        txt.spc()
        txt.print_ub(if ww!=0  111 else 222)
        txt.spc()
        txt.print_ub(if ww==1000  111 else 222)
        txt.spc()
        txt.print_ub(if ww!=1000  111 else 222)
        txt.nl()
    }
}
