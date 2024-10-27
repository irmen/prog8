%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        alias  prn = txt.print_ub
        alias  spc = txt.spc
        alias  nl = txt.nl

        prn(10)
        spc()
        prn(20)
        spc()
        prn(30)
        nl()
    }
}
