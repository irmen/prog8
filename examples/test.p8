%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        byte A
        byte B = +A
        byte C = -A
        uword W = 43210
        A = -A

        c64scr.print_uw(W)
        c64.CHROUT('\n')

        W = W as ubyte      ; TODO  cast(W as ubyte) as uword  ->  W and 255
        c64scr.print_uw(W)
        c64.CHROUT('\n')

    }
}
