%import c64utils
;%import c64flt
;%option enable_floats
%zeropage dontuse

main {

    sub start() {
        A = shiftlb0()
        A = shiftlb1()
        A = shiftlb2()
        A = shiftlb3()
        A = shiftlb4()
        A = shiftlb5()
        A = shiftlb6()
        A = shiftlb7()
        A = shiftlb8()
        A = shiftlb9()

        A = shiftrb0()
        A = shiftrb1()
        A = shiftrb2()
        A = shiftrb3()
        A = shiftrb4()
        A = shiftrb5()
        A = shiftrb6()
        A = shiftrb7()
        A = shiftrb8()
        A = shiftrb9()

        uword uw
        uw = shiftluw0()
        uw = shiftluw1()
        uw = shiftluw2()
        uw = shiftluw3()
        uw = shiftluw4()
        uw = shiftluw5()
        uw = shiftluw6()
        uw = shiftluw7()
        uw = shiftluw8()
        uw = shiftluw9()

        uw = shiftruw0()
        uw = shiftruw1()
        uw = shiftruw2()
        uw = shiftruw3()
        uw = shiftruw4()
        uw = shiftruw5()
        uw = shiftruw6()
        uw = shiftruw7()
;        uw = shiftruw8()
;        uw = shiftruw9()
    }

    sub shiftruw0() -> uword {
        uword q = 12345
        return q >> 0
    }

    sub shiftruw1() -> uword {
        uword q = 12345
        return q >> 1
    }

    sub shiftruw2() -> uword {
        uword q = 12345
        return q >> 2
    }

    sub shiftruw3() -> uword {
        uword q = 12345
        return q >> 3
    }

    sub shiftruw4() -> uword {
        uword q = 12345
        return q >> 4
    }

    sub shiftruw5() -> uword {
        uword q = 12345
        return q >> 5
    }

    sub shiftruw6() -> uword {
        uword q = 12345
        return q >> 6
    }

    sub shiftruw7() -> uword {
        uword q = 12345
        return q >> 7
    }

;    sub shiftruw8() -> uword {
;        uword q = 12345
;        return (q >> 8) as uword          ; TODO auto cast return type
;    }
;
;    sub shiftruw9() -> uword {
;        uword q = 12345
;        return (q >> 9) as uword          ; TODO auto cast return type
;    }

    sub shiftluw0() -> uword {
        uword q = 12345
        return q << 0
    }

    sub shiftluw1() -> uword {
        uword q = 12345
        return q << 1
    }

    sub shiftluw2() -> uword {
        uword q = 12345
        return q << 2
    }

    sub shiftluw3() -> uword {
        uword q = 12345
        return q << 3
    }

    sub shiftluw4() -> uword {
        uword q = 12345
        return q << 4
    }

    sub shiftluw5() -> uword {
        uword q = 12345
        return q << 5
    }

    sub shiftluw6() -> uword {
        uword q = 12345
        return q << 6
    }

    sub shiftluw7() -> uword {
        uword q = 12345
        return q << 7
    }

    sub shiftluw8() -> uword {
        uword q = 12345
        return q << 8
    }

    sub shiftluw9() -> uword {
        uword q = 12345
        return q << 9
    }

    sub shiftlb0() -> ubyte {
        return Y << 0
    }
    sub shiftlb1() -> ubyte {
        return Y << 1
    }
    sub shiftlb2() -> ubyte {
        return Y << 2
    }
    sub shiftlb3() -> ubyte {
        return Y << 3
    }
    sub shiftlb4() -> ubyte {
        return Y << 4
    }
    sub shiftlb5() -> ubyte {
        return Y << 5
    }
    sub shiftlb6() -> ubyte {
        return Y << 6
    }
    sub shiftlb7() -> ubyte {
        return Y << 7
    }
    sub shiftlb8() -> ubyte {
        return Y << 8
    }
    sub shiftlb9() -> ubyte {
        return Y << 9
    }

    sub shiftrb0() -> ubyte {
        return Y >> 0
    }
    sub shiftrb1() -> ubyte {
        return Y >> 1
    }
    sub shiftrb2() -> ubyte {
        return Y >> 2
    }
    sub shiftrb3() -> ubyte {
        return Y >> 3
    }
    sub shiftrb4() -> ubyte {
        return Y >> 4
    }
    sub shiftrb5() -> ubyte {
        return Y >> 5
    }
    sub shiftrb6() -> ubyte {
        return Y >> 6
    }
    sub shiftrb7() -> ubyte {
        return Y >> 7
    }
    sub shiftrb8() -> ubyte {
        return Y >> 8
    }
    sub shiftrb9() -> ubyte {
        return Y >> 9
    }
}
