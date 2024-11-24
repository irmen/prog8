%import textio
%zeropage basicsafe

main {
    sub start() {
        const long foo2  = $123456
        cx16.r0 = $ffff
        cx16.r1 = $ea31
        txt.print_uwbin(cx16.r1 << 7, true)
        txt.nl()
        txt.print_uwhex(cx16.r0 ^ cx16.r1, true)
        txt.nl()
        txt.print_ubhex(^cx16.r0, true)
        txt.spc()
        txt.print_uwhex(<<cx16.r0, false)
        txt.nl()
        txt.print_ubhex(^foo2, true)
        txt.spc()
        txt.print_uwhex(<<foo2, false)
        txt.nl()
        txt.print_ubhex(bankof(foo2), true)
        txt.spc()
        txt.print_uwhex(foo2 &$ffff, false)

    }
}
