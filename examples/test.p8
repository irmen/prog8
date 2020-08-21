%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        uword addr = $c002

        @(addr-2) = $f8
        @(addr-2) = not @(addr-2)
        c64scr.print_ubhex(@($c000), true)
        c64.CHROUT('\n')
        @(addr-2) = not @(addr-2)
        c64scr.print_ubhex(@($c000), true)
        c64.CHROUT('\n')
    }
}
