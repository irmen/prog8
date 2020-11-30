%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        ; word_less()
        word_lessequal()
    }

    sub word_lessequal() {
        word lessvar
        word comparevar

        txt.print("word <=\n")
        comparevar = 0
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = -2
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = -254
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = -255
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = -256
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = -5000
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = 1
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = 255
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = 256
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = 257
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }

        comparevar = 32767
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in comparevar downto -32768  {
            check_lesseq_w(lessvar, comparevar)
        }


        comparevar = -32768
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in 32766 downto comparevar+1  {
            check_not_lesseq_w(lessvar, comparevar)
        }

        comparevar = -1
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in 32766 downto comparevar+1  {
            check_not_lesseq_w(lessvar, comparevar)
        }

        comparevar = 0
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in 32766 downto comparevar+1  {
            check_not_lesseq_w(lessvar, comparevar)
        }

        comparevar = 11111
        txt.print_w(comparevar)
        txt.chrout('\n')
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
                txt.chrout('\n')
                exit(1)
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
                txt.chrout('\n')
                exit(1)
            }
        }
    }

    sub word_less() {
        word lessvar
        word comparevar

        txt.print("word <\n")
        comparevar = 0
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in -1 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = -2
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in -3 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = -254
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in -255 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = -255
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in -256 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = -256
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in -257 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = -5000
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in -5001 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = 1
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in 0 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = 255
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in 254 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = 256
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in 255 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = 257
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in 256 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = 32767
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in 32766 downto -32768  {
            check_less_w(lessvar, comparevar)
        }

        comparevar = -32768
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in 32766 downto -32768  {
            check_not_less_w(lessvar, comparevar)
        }

        comparevar = -1
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in 32766 downto -1  {
            check_not_less_w(lessvar, comparevar)
        }

        comparevar = 0
        txt.print_w(comparevar)
        txt.chrout('\n')
        for lessvar in 32766 downto 0  {
            check_not_less_w(lessvar, comparevar)
        }

        comparevar = 11111
        txt.print_w(comparevar)
        txt.chrout('\n')
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
                txt.chrout('\n')
                exit(1)
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
                txt.chrout('\n')
                exit(1)
            }
        }
    }

}
