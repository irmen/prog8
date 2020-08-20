%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        byte A = 99
        ubyte U = $18
        word B = 9999
        uword W = $18f0

        c64scr.print_b(A)
        c64.CHROUT('\n')
        A = -A
        c64scr.print_b(A)
        c64.CHROUT('\n')

        U = ~U
        c64scr.print_ubhex(U, true)
        c64.CHROUT('\n')
        U = not U
        c64scr.print_ubhex(U, true)
        c64.CHROUT('\n')
        U = not U
        c64scr.print_ubhex(U, true)
        c64.CHROUT('\n')

        c64scr.print_w(B)
        c64.CHROUT('\n')
        B = -B
        c64scr.print_w(B)
        c64.CHROUT('\n')

        W = ~W
        c64scr.print_uwhex(W, true)
        c64.CHROUT('\n')
        W = not W
        c64scr.print_uwhex(W, true)
        c64.CHROUT('\n')
        W = not W
        c64scr.print_uwhex(W, true)
        c64.CHROUT('\n')

;
;        byte B = +A
;        byte C = -A
;        uword W = 43210
;        A = -A
;
;        c64scr.print_uw(W)
;        c64.CHROUT('\n')
;
;        W = W as ubyte      ; TODO  cast(W as ubyte) as uword  ->  W and 255
;        c64scr.print_uw(W)
;        c64.CHROUT('\n')

    }
}
