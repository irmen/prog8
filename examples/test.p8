%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        ubyte A =$22
        ubyte V
        uword addr = $c0f0

        @($c000) = 123

        A = @($c022-A)

        c64scr.print_ub(A)
        c64.CHROUT('\n')
    }
}
