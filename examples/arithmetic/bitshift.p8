%import c64utils
;%import c64flt
;%option enable_floats
%zeropage dontuse

main {

    sub start() {
        c64scr.print("ubyte shift left\n")
        A = shiftlb0()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftlb1()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftlb2()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftlb3()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftlb4()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftlb5()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftlb6()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftlb7()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftlb8()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftlb9()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        c64scr.print("enter to continue:\n")
        void c64.CHRIN()

        c64scr.print("ubyte shift right\n")
        A = shiftrb0()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftrb1()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftrb2()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftrb3()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftrb4()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftrb5()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftrb6()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftrb7()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftrb8()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        A = shiftrb9()
        c64scr.print_ubbin(A, true)
        c64.CHROUT('\n')
        c64scr.print("enter to continue:\n")
        void c64.CHRIN()




        c64scr.print("signed byte shift left\n")
        byte signedb
        signedb = shiftlsb0()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftlsb1()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftlsb2()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftlsb3()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftlsb4()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftlsb5()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftlsb6()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftlsb7()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftlsb8()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftlsb9()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        c64scr.print("enter to continue:\n")
        void c64.CHRIN()

        c64scr.print("signed byte shift right\n")
        signedb = shiftrsb0()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftrsb1()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftrsb2()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftrsb3()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftrsb4()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftrsb5()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftrsb6()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftrsb7()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftrsb8()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        signedb = shiftrsb9()
        c64scr.print_ubbin(signedb as ubyte, true)
        c64.CHROUT('\n')
        c64scr.print("enter to continue:\n")
        void c64.CHRIN()




        c64scr.print("uword shift left\n")
        uword uw
        uw = shiftluw0()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw1()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw2()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw3()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw4()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw5()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw6()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw7()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw8()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw9()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw10()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw11()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw12()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw13()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw14()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw15()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw16()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftluw17()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        c64scr.print("enter to continue:\n")
        void c64.CHRIN()

        c64scr.print("uword shift right\n")
        uw = shiftruw0()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw1()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw2()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw3()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw4()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw5()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw6()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw7()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw8()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw9()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw10()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw11()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw12()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw13()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw14()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw15()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw16()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        uw = shiftruw17()
        c64scr.print_uwbin(uw, true)
        c64.CHROUT('\n')
        c64scr.print("enter to continue:\n")
        void c64.CHRIN()

        c64scr.print("signed word shift left\n")
        word sw
        sw = shiftlsw0()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw1()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw2()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw3()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw4()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw5()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw6()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw7()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw8()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw9()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw10()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw11()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw12()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw13()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw14()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw15()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw16()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftlsw17()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        c64scr.print("enter to continue:\n")
        void c64.CHRIN()

        c64scr.print("signed word shift right\n")
        sw = shiftrsw0()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw1()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw2()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw3()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw4()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw5()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw6()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw7()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw8()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw9()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw10()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw11()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw12()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw13()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw14()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw15()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw16()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')
        sw = shiftrsw17()
        c64scr.print_uwbin(sw as uword, true)
        c64.CHROUT('\n')

    }

    sub shiftruw0() -> uword {
        uword q = $a49f
        return q >> 0
    }

    sub shiftruw1() -> uword {
        uword q = $a49f
        return q >> 1
    }

    sub shiftruw2() -> uword {
        uword q = $a49f
        return q >> 2
    }

    sub shiftruw3() -> uword {
        uword q = $a49f
        return q >> 3
    }

    sub shiftruw4() -> uword {
        uword q = $a49f
        return q >> 4
    }

    sub shiftruw5() -> uword {
        uword q = $a49f
        return q >> 5
    }

    sub shiftruw6() -> uword {
        uword q = $a49f
        return q >> 6
    }

    sub shiftruw7() -> uword {
        uword q = $a49f
        return q >> 7
    }

    sub shiftruw8() -> uword {
        uword q = $a49f
        return (q >> 8)
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

    sub shiftruw16() -> uword {
        uword q = $a49f
        return (q >> 16)
    }

    sub shiftruw17() -> uword {
        uword q = $a49f
        return (q >> 17)
    }





    sub shiftrsw0() -> word {
        word q = -12345
        return q >> 0
    }

    sub shiftrsw1() -> word {
        word q = -12345
        return q >> 1
    }

    sub shiftrsw2() -> word {
        word q = -12345
        return q >> 2
    }

    sub shiftrsw3() -> word {
        word q = -12345
        return q >> 3
    }

    sub shiftrsw4() -> word {
        word q = -12345
        return q >> 4
    }

    sub shiftrsw5() -> word {
        word q = -12345
        return q >> 5
    }

    sub shiftrsw6() -> word {
        word q = -12345
        return q >> 6
    }

    sub shiftrsw7() -> word {
        word q = -12345
        return q >> 7
    }

    sub shiftrsw8() -> word {
        word q = -12345
        return (q >> 8)
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




    sub shiftluw0() -> uword {
        uword q = $a49f
        return q << 0
    }

    sub shiftluw1() -> uword {
        uword q = $a49f
        return q << 1
    }

    sub shiftluw2() -> uword {
        uword q = $a49f
        return q << 2
    }

    sub shiftluw3() -> uword {
        uword q = $a49f
        return q << 3
    }

    sub shiftluw4() -> uword {
        uword q = $a49f
        return q << 4
    }

    sub shiftluw5() -> uword {
        uword q = $a49f
        return q << 5
    }

    sub shiftluw6() -> uword {
        uword q = $a49f
        return q << 6
    }

    sub shiftluw7() -> uword {
        uword q = $a49f
        return q << 7
    }

    sub shiftluw8() -> uword {
        uword q = $a49f
        return q << 8
    }

    sub shiftluw9() -> uword {
        uword q = $a49f
        return q << 9
    }

    sub shiftluw10() -> uword {
        uword q = $a49f
        return q << 10
    }

    sub shiftluw11() -> uword {
        uword q = $a49f
        return q << 11
    }

    sub shiftluw12() -> uword {
        uword q = $a49f
        return q << 12
    }

    sub shiftluw13() -> uword {
        uword q = $a49f
        return q << 13
    }

    sub shiftluw14() -> uword {
        uword q = $a49f
        return q << 14
    }

    sub shiftluw15() -> uword {
        uword q = $a49f
        return q << 15
    }

    sub shiftluw16() -> uword {
        uword q = $a49f
        return q << 16
    }

    sub shiftluw17() -> uword {
        uword q = $a49f
        return q << 17
    }



    sub shiftlsw0() -> word {
        word q = -12345
        return q << 0
    }

    sub shiftlsw1() -> word {
        word q = -12345
        return q << 1
    }

    sub shiftlsw2() -> word {
        word q = -12345
        return q << 2
    }

    sub shiftlsw3() -> word {
        word q = -12345
        return q << 3
    }

    sub shiftlsw4() -> word {
        word q = -12345
        return q << 4
    }

    sub shiftlsw5() -> word {
        word q = -12345
        return q << 5
    }

    sub shiftlsw6() -> word {
        word q = -12345
        return q << 6
    }

    sub shiftlsw7() -> word {
        word q = -12345
        return q << 7
    }

    sub shiftlsw8() -> word {
        word q = -12345
        return q << 8
    }

    sub shiftlsw9() -> word {
        word q = -12345
        return q << 9
    }

    sub shiftlsw10() -> word {
        word q = -12345
        return q << 10
    }

    sub shiftlsw11() -> word {
        word q = -12345
        return q << 11
    }

    sub shiftlsw12() -> word {
        word q = -12345
        return q << 12
    }

    sub shiftlsw13() -> word {
        word q = -12345
        return q << 13
    }

    sub shiftlsw14() -> word {
        word q = -12345
        return q << 14
    }

    sub shiftlsw15() -> word {
        word q = -12345
        return q << 15
    }

    sub shiftlsw16() -> word {
        word q = -12345
        return q << 16
    }

    sub shiftlsw17() -> word {
        word q = -12345
        return q << 17
    }



    sub shiftlb0() -> ubyte {
        ubyte yy=$ed
        return yy << 0
    }
    sub shiftlb1() -> ubyte {
        ubyte yy=$ed
        return yy << 1
    }
    sub shiftlb2() -> ubyte {
        ubyte yy=$ed
        return yy << 2
    }
    sub shiftlb3() -> ubyte {
        ubyte yy=$ed
        return yy << 3
    }
    sub shiftlb4() -> ubyte {
        ubyte yy=$ed
        return yy << 4
    }
    sub shiftlb5() -> ubyte {
        ubyte yy=$ed
        return yy << 5
    }
    sub shiftlb6() -> ubyte {
        ubyte yy=$ed
        return yy << 6
    }
    sub shiftlb7() -> ubyte {
        ubyte yy=$ed
        return yy << 7
    }
    sub shiftlb8() -> ubyte {
        ubyte yy=$ed
        return yy << 8
    }
    sub shiftlb9() -> ubyte {
        ubyte yy=$ed
        return yy << 9
    }

    sub shiftrb0() -> ubyte {
        ubyte yy=$ed
        return yy >> 0
    }
    sub shiftrb1() -> ubyte {
        ubyte yy=$ed
        return yy >> 1
    }
    sub shiftrb2() -> ubyte {
        ubyte yy=$ed
        return yy >> 2
    }
    sub shiftrb3() -> ubyte {
        ubyte yy=$ed
        return yy >> 3
    }
    sub shiftrb4() -> ubyte {
        ubyte yy=$ed
        return yy >> 4
    }
    sub shiftrb5() -> ubyte {
        ubyte yy=$ed
        return yy >> 5
    }
    sub shiftrb6() -> ubyte {
        ubyte yy=$ed
        return yy >> 6
    }
    sub shiftrb7() -> ubyte {
        ubyte yy=$ed
        return yy >> 7
    }
    sub shiftrb8() -> ubyte {
        ubyte yy=$ed
        return yy >> 8
    }
    sub shiftrb9() -> ubyte {
        ubyte yy=$ed
        return yy >> 9
    }



    sub shiftlsb0() -> byte {
        byte yy=-123
        return yy << 0
    }
    sub shiftlsb1() -> byte {
        byte yy=-123
        return yy << 1
    }
    sub shiftlsb2() -> byte {
        byte yy=-123
        return yy << 2
    }
    sub shiftlsb3() -> byte {
        byte yy=-123
        return yy << 3
    }
    sub shiftlsb4() -> byte {
        byte yy=-123
        return yy << 4
    }
    sub shiftlsb5() -> byte {
        byte yy=-123
        return yy << 5
    }
    sub shiftlsb6() -> byte {
        byte yy=-123
        return yy << 6
    }
    sub shiftlsb7() -> byte {
        byte yy=-123
        return yy << 7
    }
    sub shiftlsb8() -> byte {
        byte yy=-123
        return yy << 8
    }
    sub shiftlsb9() -> byte {
        byte yy=-123
        return yy << 9
    }

    sub shiftrsb0() -> byte {
        byte yy=-123
        return yy >> 0
    }
    sub shiftrsb1() -> byte {
        byte yy=-123
        return yy >> 1
    }
    sub shiftrsb2() -> byte {
        byte yy=-123
        return yy >> 2
    }
    sub shiftrsb3() -> byte {
        byte yy=-123
        return yy >> 3
    }
    sub shiftrsb4() -> byte {
        byte yy=-123
        return yy >> 4
    }
    sub shiftrsb5() -> byte {
        byte yy=-123
        return yy >> 5
    }
    sub shiftrsb6() -> byte {
        byte yy=-123
        return yy >> 6
    }
    sub shiftrsb7() -> byte {
        byte yy=-123
        return yy >> 7
    }
    sub shiftrsb8() -> byte {
        byte yy=-123
        return yy >> 8
    }
    sub shiftrsb9() -> byte {
        byte yy=-123
        return yy >> 9
    }
}
