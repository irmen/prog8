%import textio
%zeropage basicsafe

main {

    sub start() {

        uword ptr = $4000
        uword ww

        pokew($4000, $98cf)
        ww = peekw($4000)
        txt.print_uwhex(ww,1)
        txt.nl()

        pokew(ptr, $98cf)
        ww = peekw(ptr)
        txt.print_uwhex(ww,1)
        txt.nl()

        pokew(ptr+2, $1234)
        ww = peekw(ptr+2)
        txt.print_uwhex(ww,1)
        txt.nl()
    }
}
