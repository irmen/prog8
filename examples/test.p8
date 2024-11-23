%import textio
%zeropage basicsafe

main {
    sub start() {
        const long foo2  = $123456
        txt.print_ubhex(bankof(foo2), true)
        txt.spc()
        txt.print_uwhex(foo2 &$ffff, false)

    }
}
