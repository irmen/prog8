%import textio
%import string
%zeropage basicsafe

main {
    sub derp(word num, ubyte a1, ubyte a2, ubyte a3, ubyte a4) {
        txt.print_w(num)
        txt.nl()
    }

    ; TODO test with new optimized division routines.

    sub start() {
        byte qq = 1
        byte bb = -51
        derp((bb*qq)/-4, 1,2,3,4)
        bb /= -4
        txt.print_b(bb)
        txt.nl()
        bb = 51
        bb /= -4
        txt.print_b(bb)
        txt.nl()
        ubyte ubb = 51
        ubb /= 4
        txt.print_ub(ubb)
        txt.nl()
    }
}
