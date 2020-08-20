%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        @($d020) += @($d020)        ; TODO fix compiler hang

        ubyte A

        A = 44+A

;        ubyte A = 10
;        @($c00a) = $4a
;        @($c000+A) ++       ; TODO implement this
;
;        c64scr.print_ubhex(@($c00a), true)
;        c64.CHROUT('\n')
;        @($c000+A) --      ; TODO implement this
;
;        c64scr.print_ubhex(@($c00a), true)
;        c64.CHROUT('\n')

    }
}
