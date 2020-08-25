%import c64utils
%zeropage basicsafe

main {

    sub start() {

        uword xx = $ef34

        xx &= $00f0
        c64scr.print_uwhex(xx, 1)
        c64.CHROUT('\n')
        xx |= $000f
        c64scr.print_uwhex(xx, 1)
        c64.CHROUT('\n')
        xx ^= $0011
        c64scr.print_uwhex(xx, 1)
        c64.CHROUT('\n')

        xx = $ef34
        xx &= $f000
        c64scr.print_uwhex(xx, 1)
        c64.CHROUT('\n')
        xx |= $0f00
        c64scr.print_uwhex(xx, 1)
        c64.CHROUT('\n')
        xx ^= $1100
        c64scr.print_uwhex(xx, 1)
        c64.CHROUT('\n')
    }
}

