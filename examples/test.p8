%import textio
%zeropage basicsafe

main {

    ; test program for the optimization of repeat var allocation (asmgen.createRepeatCounterVar)
    ; output must be:  60  6164  6224  12328

    uword xx

    sub start() {

        xx=0

        repeat 10 {
            xx++
        }
        repeat 10 {
            xx++
        }
        repeat 10 {
            xx++
        }
        repeat 10 {
            xx++
        }
        repeat 5 {
            repeat 4 {
                xx++
            }
        }
        txt.print_uw(xx)
        txt.nl()

        repeat 1000 {
            xx++
        }
        repeat 1000 {
            xx++
        }
        repeat 1000 {
            xx++
        }
        repeat 260 {
            repeat 4 {
                xx++
            }
        }
        repeat 260 {
            repeat 260 {
                xx++
            }
        }
        txt.print_uw(xx)
        txt.nl()

        sub2()

        if xx!=12328
            txt.print("\n!fail!\n")
        else
            txt.print("\nok\n")
    }

    sub sub2() {

        repeat 10 {
            xx++
        }
        repeat 10 {
            xx++
        }
        repeat 10 {
            xx++
        }
        repeat 10 {
            xx++
        }
        repeat 5 {
            repeat 4 {
                xx++
            }
        }
        txt.print_uw(xx)
        txt.nl()

        repeat 1000 {
            xx++
        }
        repeat 1000 {
            xx++
        }
        repeat 1000 {
            xx++
        }
        repeat 260 {
            repeat 4 {
                xx++
            }
        }
        repeat 260 {
            repeat 260 {
                xx++
            }
        }
        txt.print_uw(xx)
        txt.nl()
    }
}
