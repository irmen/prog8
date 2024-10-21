%import textio
%option no_sysinit
%zeropage dontuse

main {
    sub start() {
        uword w0
        uword @initonce w1
        uword @initonce w2
        uword @initonce w3
        uword @initonce w4 = 12345
        uword[4] wa
        uword[] @shared wb = [1111,2222,3333,4444]

        dump()
        txt.nl()
        w0++
        w1++
        w2++
        w3++
        w4++
        wa[1]++
        wa[2]++
        wa[3]++
        wb[0]++
        dump()
        txt.nl()

        sub dump() {
            txt.print_uw(w0)
            txt.spc()
            txt.print_uw(w1)
            txt.spc()
            txt.print_uw(w2)
            txt.spc()
            txt.print_uw(w3)
            txt.spc()
            txt.print_uw(w4)
            txt.spc()
            txt.print_uw(wa[1])
            txt.spc()
            txt.print_uw(wa[2])
            txt.spc()
            txt.print_uw(wa[3])
            txt.spc()
            txt.print_uw(wb[0])
            txt.nl()
        }
    }
}
