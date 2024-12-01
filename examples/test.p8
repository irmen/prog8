%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        txt.print_ubhex($123456>>16, true)
        txt.spc()
        txt.print_ubhex(msw($123456), true)
        txt.spc()
        txt.print_ubhex(bankof($123456), true)
        txt.nl()

        txt.print_uwhex($123456 & $ffff, true)
        txt.spc()
        txt.print_uwhex(lsw($123456), true)
        txt.spc()
        txt.print_uwhex($123456 & $ffff, true)
        txt.nl()
    }
}
