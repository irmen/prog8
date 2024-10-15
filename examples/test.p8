%import diskio
%import textio
%option no_sysinit
%zeropage basicsafe

main {

    sub start() {
        txt.print_ub(txt.width())
        txt.nl()
        txt.print_ub(txt.height())
        txt.nl()
    }
}
