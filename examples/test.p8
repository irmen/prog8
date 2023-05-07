%import textio
%zeropage basicsafe

main {

    uword vv = 60

        sub print_time(uword seconds) {
            ubyte remainder = seconds % $0003 ==0
            txt.print_uw(remainder)
            txt.nl()
        }

        sub print_time2(ubyte seconds) {
            ubyte remainder = seconds % 3 ==0
            txt.print_uw(remainder)
            txt.nl()
        }


    sub start() {
        print_time(9870)
        print_time(9871)
        print_time(9872)
        print_time(9873)
        txt.nl()
        print_time2(50)
        print_time2(51)
        print_time2(52)
        print_time2(53)
    }
}

