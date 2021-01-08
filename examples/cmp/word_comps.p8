%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {
        word_less()
        word_lessequal()
        word_greaterequal()
        word_greater()
        uword_lessequal()
    }

    sub uword_lessequal() {
        uword lessvar
        uword comparevar

        txt.print("uword <=\n")

        txt.print_uw(65535)
        txt.nl()
        check_lesseq_uw(0, 65535)
        txt.print_uw(0)
        txt.nl()
        check_not_lesseq_uw(65535, 0)

        comparevar = 65535
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in comparevar downto 0  {
            check_lesseq_uw(lessvar, comparevar)
        }

        comparevar = 65535-2
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in comparevar downto 0  {
            check_lesseq_uw(lessvar, comparevar)
        }

        comparevar = 65535-254
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in comparevar downto 0  {
            check_lesseq_uw(lessvar, comparevar)
        }

        comparevar = 65535-255
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in comparevar downto 0  {
            check_lesseq_uw(lessvar, comparevar)
        }

        comparevar = 65535-256
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in comparevar downto 0  {
            check_lesseq_uw(lessvar, comparevar)
        }

        comparevar = 65535-5000
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in comparevar downto 0  {
            check_lesseq_uw(lessvar, comparevar)
        }

        comparevar = 32769
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in comparevar downto 0  {
            check_lesseq_uw(lessvar, comparevar)
        }


        comparevar = 32768
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in 65535 downto comparevar+1  {
            check_not_lesseq_uw(lessvar, comparevar)
        }

        comparevar = 1
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in 65535 downto comparevar+1  {
            check_not_lesseq_uw(lessvar, comparevar)
        }

        comparevar = 0
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in 65535 downto comparevar+1  {
            check_not_lesseq_uw(lessvar, comparevar)
        }

        comparevar = 11111
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in 65535 downto comparevar+1  {
            check_not_lesseq_uw(lessvar, comparevar)
        }

        comparevar = 255
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in 65535 downto comparevar+1  {
            check_not_lesseq_uw(lessvar, comparevar)
        }

        comparevar = 256
        txt.print_uw(comparevar)
        txt.nl()
        for lessvar in 65535 downto comparevar+1  {
            check_not_lesseq_uw(lessvar, comparevar)
        }


        test_stack.test()
        return

        sub check_lesseq_uw(uword w1, uword w2) {
            uword zero = 0
            ubyte error=0

            ubyte ub = w1<=w2
            if not ub {
                error++
                txt.print("ub!")
            }

            if w1<=(w2+zero) {
                zero = 0 ; dummy
            } else {
                error++
                txt.print("c!")
            }

            if error {
                txt.print("  ")
                txt.print_uw(w1)
                txt.print(" <= ")
                txt.print_uw(w2)
                txt.nl()
                sys.exit(1)
            }
        }

        sub check_not_lesseq_uw(uword w1, uword w2) {
            uword zero = 0
            ubyte error=0

            ubyte ub = w1<=w2
            if ub {
                error++
                txt.print("ub!")
            }

            if w1<=(w2+zero) {
                error++
                txt.print("c!")
            } else {
                zero = 0 ; dummy
            }

            if error {
                txt.print("  ")
                txt.print_uw(w1)
                txt.print(" not <= ")
                txt.print_uw(w2)
                txt.nl()
                sys.exit(1)
            }
        }
    }

    sub word_greater() {
        word biggervar
        word comparevar

        txt.print("word >\n")

        txt.print_w(-32767)
        txt.nl()
        check_greater_w(32767, -32767)
        txt.print_w(32766)
        txt.nl()
        check_not_greater_w(-32766, 32766)

        comparevar = 32765
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in comparevar downto -32768  {
            check_not_greater_w(biggervar, comparevar)
        }

        comparevar = -1
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in comparevar downto -32768  {
            check_not_greater_w(biggervar, comparevar)
        }

        comparevar = 0
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in comparevar downto -32768  {
            check_not_greater_w(biggervar, comparevar)
        }

        comparevar = 11111
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in comparevar downto -32768  {
            check_not_greater_w(biggervar, comparevar)
        }

        comparevar = 0
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar+1  {
            check_greater_w(biggervar, comparevar)
        }

        comparevar = -2
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar+1  {
            check_greater_w(biggervar, comparevar)
        }

        comparevar = -254
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar+1  {
            check_greater_w(biggervar, comparevar)
        }

        comparevar = -255
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar+1  {
            check_greater_w(biggervar, comparevar)
        }

        comparevar = -256
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar+1  {
            check_greater_w(biggervar, comparevar)
        }

        comparevar = -5000
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar+1  {
            check_greater_w(biggervar, comparevar)
        }

        comparevar = 1
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar+1  {
            check_greater_w(biggervar, comparevar)
        }

        comparevar = 255
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar+1  {
            check_greater_w(biggervar, comparevar)
        }

        comparevar = 256
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar+1  {
            check_greater_w(biggervar, comparevar)
        }

        comparevar = 257
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar+1  {
            check_greater_w(biggervar, comparevar)
        }

        comparevar = 32760
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar+1  {
            check_greater_w(biggervar, comparevar)
        }


        test_stack.test()
        return

        sub check_greater_w(word w1, word w2) {
            word zero = 0
            ubyte error=0

            ubyte ub = w1>(w2+zero)
            if not ub {
                error++
                txt.print("ubz!")
            }

            ub = w1>w2
            if not ub {
                error++
                txt.print("ub!")
            }

            if w1>(w2+zero) {
                zero = 0 ; dummy
            } else {
                error++
                txt.print("c!")
            }

            if error {
                txt.print("  ")
                txt.print_w(w1)
                txt.print(" > ")
                txt.print_w(w2)
                txt.nl()
                sys.exit(1)
            }
        }

        sub check_not_greater_w(word w1, word w2) {
            word zero = 0
            ubyte error=0

            ubyte ub = w1>w2
            if ub {
                error++
                txt.print("ub!")
            }

            if w1>(w2+zero) {
                error++
                txt.print("c!")
            } else {
                zero = 0 ; dummy
            }

            if w1>w2 {
                error++
                txt.print("c2!")
            } else {
                zero = 0 ; dummy
            }

            if error {
                txt.print("  ")
                txt.print_w(w1)
                txt.print(" not > ")
                txt.print_w(w2)
                txt.nl()
                sys.exit(1)
            }
        }
    }

    sub word_greaterequal() {
        word biggervar
        word comparevar

        txt.print("word >=\n")

        txt.print_w(-32767)
        txt.nl()
        check_greatereq_w(32767, -32767)
        txt.print_w(32766)
        txt.nl()
        check_not_greatereq_w(-32766, 32766)

        comparevar = 32765
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in comparevar-1 downto -32768  {
            check_not_greatereq_w(biggervar, comparevar)
        }

        comparevar = -1
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in comparevar-1 downto -32768  {
            check_not_greatereq_w(biggervar, comparevar)
        }

        comparevar = 0
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in comparevar-1 downto -32768  {
            check_not_greatereq_w(biggervar, comparevar)
        }

        comparevar = 11111
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in comparevar-1 downto -32768  {
            check_not_greatereq_w(biggervar, comparevar)
        }

        comparevar = 0
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar  {
            check_greatereq_w(biggervar, comparevar)
        }

        comparevar = -2
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar  {
            check_greatereq_w(biggervar, comparevar)
        }

        comparevar = -254
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar  {
            check_greatereq_w(biggervar, comparevar)
        }

        comparevar = -255
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar  {
            check_greatereq_w(biggervar, comparevar)
        }

        comparevar = -256
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar  {
            check_greatereq_w(biggervar, comparevar)
        }

        comparevar = -5000
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar  {
            check_greatereq_w(biggervar, comparevar)
        }

        comparevar = 1
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar  {
            check_greatereq_w(biggervar, comparevar)
        }

        comparevar = 255
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar  {
            check_greatereq_w(biggervar, comparevar)
        }

        comparevar = 256
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar  {
            check_greatereq_w(biggervar, comparevar)
        }

        comparevar = 257
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar  {
            check_greatereq_w(biggervar, comparevar)
        }

        comparevar = 32767
        txt.print_w(comparevar)
        txt.nl()
        for biggervar in 32767 downto comparevar  {
            check_greatereq_w(biggervar, comparevar)
        }


        test_stack.test()
        return

        sub check_greatereq_w(word w1, word w2) {
            word zero = 0
            ubyte error=0

            ubyte ub = w1>=(w2+zero)
            if not ub {
                error++
                txt.print("ubz!")
            }

            ub = w1>=w2
            if not ub {
                error++
                txt.print("ub!")
            }

            if w1>=(w2+zero) {
                zero = 0 ; dummy
            } else {
                error++
                txt.print("c!")
            }

            if error {
                txt.print("  ")
                txt.print_w(w1)
                txt.print(" >= ")
                txt.print_w(w2)
                txt.nl()
                sys.exit(1)
            }
        }

        sub check_not_greatereq_w(word w1, word w2) {
            word zero = 0
            ubyte error=0

            ubyte ub = w1>=w2
            if ub {
                error++
                txt.print("ub!")
            }

            if w1>=(w2+zero) {
                error++
                txt.print("c!")
            } else {
                zero = 0 ; dummy
            }

            if w1>=w2 {
                error++
                txt.print("c2!")
            } else {
                zero = 0 ; dummy
            }

            if error {
                txt.print("  ")
                txt.print_w(w1)
                txt.print(" not >= ")
                txt.print_w(w2)
                txt.nl()
                sys.exit(1)
            }
        }
    }

    sub word_lessequal() {
        word lessvar
        word comparevar

        txt.print("word <=\n")

        txt.print_w(32767)
        txt.nl()
        check_lesseq_w(-32767, 32767)
        txt.print_w(-32767)
        txt.nl()
        check_not_lesseq_w(32767, -32767)

        comparevar = 0
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = -2
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = -254
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = -255
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = -256
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = -5000
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = 1
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = 255
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = 256
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = 257
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = 32767
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }


        comparevar = -32768
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 32766 downto comparevar+1  {
            check_not_lesseq_w(lessvar, comparevar)
        }

        comparevar = -1
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 32766 downto comparevar+1  {
            check_not_lesseq_w(lessvar, comparevar)
        }

        comparevar = 0
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 32766 downto comparevar+1  {
            check_not_lesseq_w(lessvar, comparevar)
        }

        comparevar = 11111
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 32766 downto comparevar+1  {
            check_not_lesseq_w(lessvar, comparevar)
        }

        test_stack.test()
        return

        sub check_lesseq_w(word w1, word w2) {
            word zero = 0
            ubyte error=0

            ubyte ub = w1<=w2
            if not ub {
                error++
                txt.print("ub!")
            }

            if w1<=(w2+zero) {
                zero = 0 ; dummy
            } else {
                error++
                txt.print("c!")
            }

            if error {
                txt.print("  ")
                txt.print_w(w1)
                txt.print(" <= ")
                txt.print_w(w2)
                txt.nl()
                sys.exit(1)
            }
        }

        sub check_not_lesseq_w(word w1, word w2) {
            word zero = 0
            ubyte error=0

            ubyte ub = w1<=w2
            if ub {
                error++
                txt.print("ub!")
            }

            if w1<=(w2+zero) {
                error++
                txt.print("c!")
            } else {
                zero = 0 ; dummy
            }

            if error {
                txt.print("  ")
                txt.print_w(w1)
                txt.print(" not <= ")
                txt.print_w(w2)
                txt.nl()
                sys.exit(1)
            }
        }
    }

    sub word_less() {
        word lessvar
        word comparevar

        txt.print("word <\n")

        txt.print_w(32767)
        txt.nl()
        check_less_w(-32767, 32767)
        txt.print_w(-32767)
        txt.nl()
        check_not_less_w(32767, -32767)

        comparevar = 0
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in -1 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = -2
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in -3 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = -254
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in -255 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = -255
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in -256 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = -256
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in -257 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = -5000
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in -5001 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = 1
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 0 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = 255
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 254 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = 256
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 255 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = 257
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 256 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = 32767
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 32766 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = -32768
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 32766 downto -32768  {
            check_not_less_w(lessvar, comparevar)
        }

        comparevar = -1
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 32766 downto -1  {
            check_not_less_w(lessvar, comparevar)
        }

        comparevar = 0
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 32766 downto 0  {
            check_not_less_w(lessvar, comparevar)
        }

        comparevar = 11111
        txt.print_w(comparevar)
        txt.nl()
        for lessvar in 32766 downto 11111  {
            check_not_less_w(lessvar, comparevar)
        }

        test_stack.test()
        return

        sub check_less_w(word w1, word w2) {
            word zero = 0
            ubyte error=0

            ubyte ub = w1<w2
            if not ub {
                error++
                txt.print("ub!")
            }

            if w1<(w2+zero) {
                zero = 0 ; dummy
            } else {
                error++
                txt.print("c!")
            }

            if error {
                txt.print("  ")
                txt.print_w(w1)
                txt.print(" < ")
                txt.print_w(w2)
                txt.nl()
                sys.exit(1)
            }
        }

        sub check_not_less_w(word w1, word w2) {
            word zero = 0
            ubyte error=0

            ubyte ub = w1<w2
            if ub {
                error++
                txt.print("ub!")
            }

            if w1<(w2+zero) {
                error++
                txt.print("c!")
            } else {
                zero = 0 ; dummy
            }

            if error {
                txt.print("  ")
                txt.print_w(w1)
                txt.print(" not < ")
                txt.print_w(w2)
                txt.nl()
                sys.exit(1)
            }
        }
    }

}
