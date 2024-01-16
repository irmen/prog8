%import textio
%zeropage basicsafe


main {

    str inputbuffer = "?" * 20

    sub start() {
        ubyte a
        txt.print("ubyte shift left\n")
        a = shiftlb0()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftlb1()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftlb2()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftlb3()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftlb4()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftlb5()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftlb6()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftlb7()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftlb8()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftlb9()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        txt.print("enter to continue:\n")
        void txt.input_chars(inputbuffer)

        txt.print("ubyte shift right\n")
        a = shiftrb0()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftrb1()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftrb2()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftrb3()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftrb4()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftrb5()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftrb6()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftrb7()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftrb8()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        a = shiftrb9()
        txt.print_ubbin(a, true)
        txt.chrout('\n')
        txt.print("enter to continue:\n")
        void txt.input_chars(inputbuffer)




        txt.print("signed byte shift left\n")
        byte signedb
        signedb = shiftlsb0()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftlsb1()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftlsb2()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftlsb3()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftlsb4()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftlsb5()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftlsb6()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftlsb7()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftlsb8()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftlsb9()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        txt.print("enter to continue:\n")
        void txt.input_chars(inputbuffer)

        txt.print("signed byte shift right\n")
        signedb = shiftrsb0()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftrsb1()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftrsb2()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftrsb3()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftrsb4()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftrsb5()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftrsb6()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftrsb7()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftrsb8()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        signedb = shiftrsb9()
        txt.print_ubbin(signedb as ubyte, true)
        txt.chrout('\n')
        txt.print("enter to continue:\n")
        void txt.input_chars(inputbuffer)




        txt.print("uword shift left\n")
        uword uw
        uw = shiftluw0()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw1()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw2()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw3()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw4()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw5()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw6()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw7()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw8()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw9()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw10()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw11()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw12()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw13()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw14()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw15()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw16()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftluw17()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        txt.print("enter to continue:\n")
        void txt.input_chars(inputbuffer)

        txt.print("uword shift right\n")
        uw = shiftruw0()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw1()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw2()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw3()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw4()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw5()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw6()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw7()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw8()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw9()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw10()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw11()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw12()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw13()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw14()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw15()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw16()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        uw = shiftruw17()
        txt.print_uwbin(uw, true)
        txt.chrout('\n')
        txt.print("enter to continue:\n")
        void txt.input_chars(inputbuffer)

        txt.print("signed word shift left\n")
        word sw
        sw = shiftlsw0()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw1()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw2()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw3()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw4()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw5()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw6()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw7()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw8()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw9()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw10()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw11()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw12()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw13()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw14()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw15()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw16()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftlsw17()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        txt.print("enter to continue:\n")
        void txt.input_chars(inputbuffer)

        txt.print("signed word shift right\n")
        sw = shiftrsw0()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw1()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw2()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw3()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw4()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw5()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw6()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw7()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw8()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw9()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw10()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw11()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw12()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw13()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw14()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')
        sw = shiftrsw15()
        txt.print_uwbin(sw as uword, true)
        txt.chrout('\n')

    }

    sub shiftruw0() -> uword {
        uword @shared q = $a49f
        return q >> 0
    }

    sub shiftruw1() -> uword {
        uword @shared q = $a49f
        return q >> 1
    }

    sub shiftruw2() -> uword {
        uword @shared q = $a49f
        return q >> 2
    }

    sub shiftruw3() -> uword {
        uword @shared q = $a49f
        return q >> 3
    }

    sub shiftruw4() -> uword {
        uword @shared q = $a49f
        return q >> 4
    }

    sub shiftruw5() -> uword {
        uword @shared q = $a49f
        return q >> 5
    }

    sub shiftruw6() -> uword {
        uword @shared q = $a49f
        return q >> 6
    }

    sub shiftruw7() -> uword {
        uword @shared q = $a49f
        return q >> 7
    }

    sub shiftruw8() -> uword {
        uword @shared q = $a49f
        return (q >> 8)
    }

    sub shiftruw9() -> uword {
        uword @shared q = $a49f
        return (q >> 9)
    }

    sub shiftruw10() -> uword {
        uword @shared q = $a49f
        return (q >> 10)
    }

    sub shiftruw11() -> uword {
        uword @shared q = $a49f
        return (q >> 11)
    }

    sub shiftruw12() -> uword {
        uword @shared q = $a49f
        return (q >> 12)
    }

    sub shiftruw13() -> uword {
        uword @shared q = $a49f
        return (q >> 13)
    }

    sub shiftruw14() -> uword {
        uword @shared q = $a49f
        return (q >> 14)
    }

    sub shiftruw15() -> uword {
        uword @shared q = $a49f
        return (q >> 15)
    }

    sub shiftruw16() -> uword {
        uword @shared q = $a49f
        return (q >> 16)
    }

    sub shiftruw17() -> uword {
        uword @shared q = $a49f
        return (q >> 17)
    }

    sub shiftrsw0() -> word {
        word @shared q = -12345
        return q >> 0
    }

    sub shiftrsw1() -> word {
        word @shared q = -12345
        return q >> 1
    }

    sub shiftrsw2() -> word {
        word @shared q = -12345
        return q >> 2
    }

    sub shiftrsw3() -> word {
        word @shared q = -12345
        return q >> 3
    }

    sub shiftrsw4() -> word {
        word @shared q = -12345
        return q >> 4
    }

    sub shiftrsw5() -> word {
        word @shared q = -12345
        return q >> 5
    }

    sub shiftrsw6() -> word {
        word @shared q = -12345
        return q >> 6
    }

    sub shiftrsw7() -> word {
        word @shared q = -12345
        return q >> 7
    }

    sub shiftrsw8() -> word {
        word @shared q = -12345
        return (q >> 8)
    }

    sub shiftrsw9() -> word {
        word @shared q = -12345
        return (q >> 9)
    }

    sub shiftrsw10() -> word {
        word @shared q = -12345
        return (q >> 10)
    }

    sub shiftrsw11() -> word {
        word @shared q = -12345
        return (q >> 11)
    }

    sub shiftrsw12() -> word {
        word @shared q = -12345
        return (q >> 12)
    }

    sub shiftrsw13() -> word {
        word @shared q = -12345
        return (q >> 13)
    }

    sub shiftrsw14() -> word {
        word @shared q = -12345
        return (q >> 14)
    }

    sub shiftrsw15() -> word {
        word @shared q = -12345
        return (q >> 15)
    }




    sub shiftluw0() -> uword {
        uword @shared q = $a49f
        return q << 0
    }

    sub shiftluw1() -> uword {
        uword @shared q = $a49f
        return q << 1
    }

    sub shiftluw2() -> uword {
        uword @shared q = $a49f
        return q << 2
    }

    sub shiftluw3() -> uword {
        uword @shared q = $a49f
        return q << 3
    }

    sub shiftluw4() -> uword {
        uword @shared q = $a49f
        return q << 4
    }

    sub shiftluw5() -> uword {
        uword @shared q = $a49f
        return q << 5
    }

    sub shiftluw6() -> uword {
        uword @shared q = $a49f
        return q << 6
    }

    sub shiftluw7() -> uword {
        uword @shared q = $a49f
        return q << 7
    }

    sub shiftluw8() -> uword {
        uword @shared q = $a49f
        return q << 8
    }

    sub shiftluw9() -> uword {
        uword @shared q = $a49f
        return q << 9
    }

    sub shiftluw10() -> uword {
        uword @shared q = $a49f
        return q << 10
    }

    sub shiftluw11() -> uword {
        uword @shared q = $a49f
        return q << 11
    }

    sub shiftluw12() -> uword {
        uword @shared q = $a49f
        return q << 12
    }

    sub shiftluw13() -> uword {
        uword @shared q = $a49f
        return q << 13
    }

    sub shiftluw14() -> uword {
        uword @shared q = $a49f
        return q << 14
    }

    sub shiftluw15() -> uword {
        uword @shared q = $a49f
        return q << 15
    }

    sub shiftluw16() -> uword {
        uword @shared q = $a49f
        return q << 16
    }

    sub shiftluw17() -> uword {
        uword @shared q = $a49f
        return q << 17
    }



    sub shiftlsw0() -> word {
        word @shared q = -12345
        return q << 0
    }

    sub shiftlsw1() -> word {
        word @shared q = -12345
        return q << 1
    }

    sub shiftlsw2() -> word {
        word @shared q = -12345
        return q << 2
    }

    sub shiftlsw3() -> word {
        word @shared q = -12345
        return q << 3
    }

    sub shiftlsw4() -> word {
        word @shared q = -12345
        return q << 4
    }

    sub shiftlsw5() -> word {
        word @shared q = -12345
        return q << 5
    }

    sub shiftlsw6() -> word {
        word @shared q = -12345
        return q << 6
    }

    sub shiftlsw7() -> word {
        word @shared q = -12345
        return q << 7
    }

    sub shiftlsw8() -> word {
        word @shared q = -12345
        return q << 8
    }

    sub shiftlsw9() -> word {
        word @shared q = -12345
        return q << 9
    }

    sub shiftlsw10() -> word {
        word @shared q = -12345
        return q << 10
    }

    sub shiftlsw11() -> word {
        word @shared q = -12345
        return q << 11
    }

    sub shiftlsw12() -> word {
        word @shared q = -12345
        return q << 12
    }

    sub shiftlsw13() -> word {
        word @shared q = -12345
        return q << 13
    }

    sub shiftlsw14() -> word {
        word @shared q = -12345
        return q << 14
    }

    sub shiftlsw15() -> word {
        word @shared q = -12345
        return q << 15
    }

    sub shiftlsw16() -> word {
        word @shared q = -12345
        return q << 16
    }

    sub shiftlsw17() -> word {
        word @shared q = -12345
        return q << 17
    }



    sub shiftlb0() -> ubyte {
        ubyte @shared yy=$ed
        return yy << 0
    }
    sub shiftlb1() -> ubyte {
        ubyte @shared yy=$ed
        return yy << 1
    }
    sub shiftlb2() -> ubyte {
        ubyte @shared yy=$ed
        return yy << 2
    }
    sub shiftlb3() -> ubyte {
        ubyte @shared yy=$ed
        return yy << 3
    }
    sub shiftlb4() -> ubyte {
        ubyte @shared yy=$ed
        return yy << 4
    }
    sub shiftlb5() -> ubyte {
        ubyte @shared yy=$ed
        return yy << 5
    }
    sub shiftlb6() -> ubyte {
        ubyte @shared yy=$ed
        return yy << 6
    }
    sub shiftlb7() -> ubyte {
        ubyte @shared yy=$ed
        return yy << 7
    }
    sub shiftlb8() -> ubyte {
        ubyte @shared yy=$ed
        return yy << 8
    }
    sub shiftlb9() -> ubyte {
        ubyte @shared yy=$ed
        return yy << 9
    }

    sub shiftrb0() -> ubyte {
        ubyte @shared yy=$ed
        return yy >> 0
    }
    sub shiftrb1() -> ubyte {
        ubyte @shared yy=$ed
        return yy >> 1
    }
    sub shiftrb2() -> ubyte {
        ubyte @shared yy=$ed
        return yy >> 2
    }
    sub shiftrb3() -> ubyte {
        ubyte @shared yy=$ed
        return yy >> 3
    }
    sub shiftrb4() -> ubyte {
        ubyte @shared yy=$ed
        return yy >> 4
    }
    sub shiftrb5() -> ubyte {
        ubyte @shared yy=$ed
        return yy >> 5
    }
    sub shiftrb6() -> ubyte {
        ubyte @shared yy=$ed
        return yy >> 6
    }
    sub shiftrb7() -> ubyte {
        ubyte @shared yy=$ed
        return yy >> 7
    }
    sub shiftrb8() -> ubyte {
        ubyte @shared yy=$ed
        return yy >> 8
    }
    sub shiftrb9() -> ubyte {
        ubyte @shared yy=$ed
        return yy >> 9
    }



    sub shiftlsb0() -> byte {
        byte @shared yy=-123
        return yy << 0
    }
    sub shiftlsb1() -> byte {
        byte @shared yy=-123
        return yy << 1
    }
    sub shiftlsb2() -> byte {
        byte @shared yy=-123
        return yy << 2
    }
    sub shiftlsb3() -> byte {
        byte @shared yy=-123
        return yy << 3
    }
    sub shiftlsb4() -> byte {
        byte @shared yy=-123
        return yy << 4
    }
    sub shiftlsb5() -> byte {
        byte @shared yy=-123
        return yy << 5
    }
    sub shiftlsb6() -> byte {
        byte @shared yy=-123
        return yy << 6
    }
    sub shiftlsb7() -> byte {
        byte @shared yy=-123
        return yy << 7
    }
    sub shiftlsb8() -> byte {
        byte @shared yy=-123
        return yy << 8
    }
    sub shiftlsb9() -> byte {
        byte @shared yy=-123
        return yy << 9
    }

    sub shiftrsb0() -> byte {
        byte @shared yy=-123
        return yy >> 0
    }
    sub shiftrsb1() -> byte {
        byte @shared yy=-123
        return yy >> 1
    }
    sub shiftrsb2() -> byte {
        byte @shared yy=-123
        return yy >> 2
    }
    sub shiftrsb3() -> byte {
        byte @shared yy=-123
        return yy >> 3
    }
    sub shiftrsb4() -> byte {
        byte @shared yy=-123
        return yy >> 4
    }
    sub shiftrsb5() -> byte {
        byte @shared yy=-123
        return yy >> 5
    }
    sub shiftrsb6() -> byte {
        byte @shared yy=-123
        return yy >> 6
    }
    sub shiftrsb7() -> byte {
        byte @shared yy=-123
        return yy >> 7
    }
    sub shiftrsb8() -> byte {
        byte @shared yy=-123
        return yy >> 8
    }
    sub shiftrsb9() -> byte {
        byte @shared yy=-123
        return yy >> 9
    }
}
