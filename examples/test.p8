%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        ;ubyte @(addr)=0
        uword addr = $02
        uword addr2 = $03
        ubyte B = 22
        ; all optimized:
        @(addr) = B
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')
        @(addr2-1) = @(addr2-1) +33
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')

        @(addr) = 11
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')
        @(addr) = 33 + @(addr)
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')

        @(addr) = 11
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')
        @(addr) = (@(addr) + 33) + B
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')

        @(addr) = 11
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')
        @(addr) = (33 + @(addr)) + B
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')

        @(addr) = 11
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')
        @(addr) = (@(addr) + B) + 33
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')

        @(addr) = 11
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')
        @(addr) = (B + @(addr)) + 33
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')

        @(addr) = 11
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')
        @(addr) = B+ (@(addr) + 33)
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')

        @(addr) = 11
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')
        @(addr) = B+(33 + @(addr))
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')

        @(addr) = 11
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')
        @(addr) = 33+(@(addr) + B)
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')

        @(addr) = 11
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')
        @(addr) = 33+(B + @(addr))
        c64scr.print_ub(@(addr))
        c64.CHROUT('\n')
    }
}
