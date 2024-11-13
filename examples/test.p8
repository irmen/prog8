%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        txt.print_uwhex(sys.progstart(), true)
        txt.spc()
        txt.print_uwhex(sys.progend(), true)
        txt.nl()
    }
}
