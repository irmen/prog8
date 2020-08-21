%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        ubyte A =$22
        uword addr = $c080

        c64scr.print_uwhex(addr, true)
        c64.CHROUT('\n')
        addr += $21
        c64scr.print_uwhex(addr, true)
        c64.CHROUT('\n')
        addr += $21
        c64scr.print_uwhex(addr, true)
        c64.CHROUT('\n')
        addr += $21
        c64scr.print_uwhex(addr, true)
        c64.CHROUT('\n')
        addr += $21
        c64scr.print_uwhex(addr, true)
        c64.CHROUT('\n')
        addr += $21
        c64scr.print_uwhex(addr, true)
        c64.CHROUT('\n')

        addr -= $21
        c64scr.print_uwhex(addr, true)
        c64.CHROUT('\n')
        addr -= $21
        c64scr.print_uwhex(addr, true)
        c64.CHROUT('\n')
        addr -= $21
        c64scr.print_uwhex(addr, true)
        c64.CHROUT('\n')
        addr -= $21
        c64scr.print_uwhex(addr, true)
        c64.CHROUT('\n')
    }
}
