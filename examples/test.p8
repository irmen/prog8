%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        ;@($d020) += @($d020)        ; TODO fix compiler hang
        ;@($c000+A) ++       ; TODO implement this

        uword addr = $c000
        @(addr) = $f1
        c64scr.print_ubhex(@(addr), true)
        c64.CHROUT('\n')
        @(addr) = - @(addr)
        c64scr.print_ubhex(@(addr), true)
        c64.CHROUT('\n')
        @(addr) = - @(addr)
        c64scr.print_ubhex(@(addr), true)
        c64.CHROUT('\n')

        ;@($c000) = ! @($c000)
        ;@($c000) = ~ @($c000)
    }
}
