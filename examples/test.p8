%import textio
%import string
%zeropage basicsafe

main {
    sub derp(word num, ubyte a1, ubyte a2, ubyte a3, ubyte a4) {
        txt.print_w(num)
        txt.nl()
    }

    sub start() {
        word qq = 1
        word bb = -5051
        derp((bb*qq)/-2, 1,2,3,4)
        bb /= -2
        txt.print_w(bb)
        txt.nl()
        bb  = -5051
        bb = -bb/2
        txt.print_w(bb)
        txt.nl()
        bb = 5051
        bb /= -2
        txt.print_w(bb)
        txt.nl()
        uword ubb = 5051
        ubb /= 2
        txt.print_uw(ubb)
        txt.nl()
    }
}
