%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {
        uword uw
        uword xx
        word ww
        word yy

        uw = shiftruw7()
        xx+=uw
        uw = shiftruw8()
        xx+=uw
        uw = shiftruw9()
        xx+=uw
        uw = shiftruw10()
        xx+=uw
        uw = shiftruw11()
        xx+=uw
        uw = shiftruw12()
        xx+=uw
        uw = shiftruw13()
        xx+=uw
        uw = shiftruw14()
        xx+=uw
        uw = shiftruw15()
        xx+=uw

        ww = shiftrsw7()
        yy+=ww
        ww = shiftrsw8()
        yy+=ww
        ww = shiftrsw9()
        yy+=ww
        ww = shiftrsw10()
        yy+=ww
        ww = shiftrsw11()
        yy+=ww
        ww = shiftrsw12()
        yy+=ww
        ww = shiftrsw13()
        yy+=ww
        ww = shiftrsw14()
        yy+=ww
        ww = shiftrsw15()
        yy+=ww
        ww = shiftrsw16()       ; TODO why sub not replaced by const?
        yy+=ww
        ww = shiftrsw17()       ; TODO why sub not replaced by const?
        yy+=ww

    }


        sub shiftruw7() -> uword {
            uword q = $a49f
            return q >> 7
        }

        sub shiftruw8() -> uword {
            uword q = $a49f
            return (q >> 8)         ; TODO fix slow?  (and for all below)
        }

        sub shiftruw9() -> uword {
            uword q = $a49f
            return (q >> 9)
        }

        sub shiftruw10() -> uword {
            uword q = $a49f
            return (q >> 10)
        }

        sub shiftruw11() -> uword {
            uword q = $a49f
            return (q >> 11)
        }

        sub shiftruw12() -> uword {
            uword q = $a49f
            return (q >> 12)
        }

        sub shiftruw13() -> uword {
            uword q = $a49f
            return (q >> 13)
        }

        sub shiftruw14() -> uword {
            uword q = $a49f
            return (q >> 14)
        }

        sub shiftruw15() -> uword {
            uword q = $a49f
            return (q >> 15)
        }

    sub shiftrsw7() -> word {
        word q = -12345
        return q >> 7
    }

    sub shiftrsw8() -> word {
        word q = -12345
        return (q >> 8)         ; TODO why not marked slow?  What code is generated?  Also for all below.
    }

    sub shiftrsw9() -> word {
        word q = -12345
        return (q >> 9)
    }

    sub shiftrsw10() -> word {
        word q = -12345
        return (q >> 10)
    }

    sub shiftrsw11() -> word {
        word q = -12345
        return (q >> 11)
    }

    sub shiftrsw12() -> word {
        word q = -12345
        return (q >> 12)
    }

    sub shiftrsw13() -> word {
        word q = -12345
        return (q >> 13)
    }

    sub shiftrsw14() -> word {
        word q = -12345
        return (q >> 14)
    }

    sub shiftrsw15() -> word {
        word q = -12345
        return (q >> 15)
    }

    sub shiftrsw16() -> word {
        word q = -12345
        return (q >> 16)
    }

    sub shiftrsw17() -> word {
        word q = -12345
        return (q >> 17)
    }

}
