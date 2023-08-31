%import textio
%import math
%zeropage basicsafe

main {
    sub start() {
        uword w1
        uword w2

        ubyte b1
        ubyte b2

        b1 = 255
        b2 = 1
        txt.print_ub(b1&b2!=0)  ; true
        txt.spc()
        txt.print_ub(b1&b2==0)  ; false
        txt.nl()

        b1 = 255
        b2 = 0
        txt.print_ub(b1&b2!=0)  ; false
        txt.spc()
        txt.print_ub(b1&b2==0)  ; true
        txt.nl()

        w1 = $ff0f
        w2 = $0101
        txt.print_ub(w1&w2!=0)  ; true
        txt.spc()
        txt.print_ub(w1&w2==0)  ; false
        txt.nl()

        w1 = $ff0f
        w2 = $00f0
        txt.print_ub(w1&w2!=0)  ; false
        txt.spc()
        txt.print_ub(w1&w2==0)  ; true
        txt.nl()


;        if w1 & w2
;            cx16.r0++
    }
}
