%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        word lessvar
        word comparevar

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
    }

}
