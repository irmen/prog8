%import textio
%zeropage basicsafe

main {

    sub start() {
        ubyte ub = 123
        byte bb = -100
        uword uw = 12345
        word ww = -12345

        ub |= 63    ; vm/c64 ok (127)
        bb |= 63    ; vm/c64 ok (-65)
        uw |= 63    ; vm/c64 ok (12351)
        ww |= 63    ; vm/c64 ok (-12289)

        txt.print_ub(ub)
        txt.spc()
        txt.print_b(bb)
        txt.spc()
        txt.print_uw(uw)
        txt.spc()
        txt.print_w(ww)
        txt.nl()

        uw |= 16384  ; vm/c64 ok (28735)
        ww |= 8192   ; vm/c64 ok (-4097)

        txt.print_uw(uw)
        txt.spc()
        txt.print_w(ww)
        txt.nl()
    }
}

