%import textio
%zeropage basicsafe

main {

    sub start()  {
        const long longconst = $12345678
        long @shared longvar = $abcdef99

        txt.print_uwhex(msw(longconst), true)
        txt.spc()
        txt.print_ubhex(lsb(msw(longconst)), true)
        txt.nl()

        txt.print_uwhex(msw(longvar), true)
        txt.spc()
        txt.print_ubhex(lsb(msw(longvar)), true)
        txt.spc()
        txt.print_ubhex(@(&longvar+2), true)
        txt.nl()
    }
}
