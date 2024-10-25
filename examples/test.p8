%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        word @shared ww = 11111
        txt.print_ub(if ww==11111  111 else 222)
        txt.spc()
        txt.print_ub(if ww!=11111  111 else 222)
        txt.nl()

        if ww==11111
            txt.print("one\n")
        else
            txt.print("two\n")
        if ww!=11111
            txt.print("one\n")
        else
            txt.print("two\n")
    }
}
