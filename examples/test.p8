%zeropage basicsafe

main {

    sub start() {

        uword xx = $ef34

        xx &= $00f0
        screen.print_uwhex(xx, 1)
        cx16.CHROUT('\n')
        xx |= $000f
        screen.print_uwhex(xx, 1)
        cx16.CHROUT('\n')
        xx ^= $0011
        screen.print_uwhex(xx, 1)
        cx16.CHROUT('\n')

        xx = $ef34
        xx &= $f000
        screen.print_uwhex(xx, 1)
        cx16.CHROUT('\n')
        xx |= $0f00
        screen.print_uwhex(xx, 1)
        cx16.CHROUT('\n')
        xx ^= $1100
        screen.print_uwhex(xx, 1)
        cx16.CHROUT('\n')
    }
}

