%import textio
%zeropage basicsafe

main {

    sub start() {
        word xx

        xx=-$7f00

        txt.print_w(xx)
        txt.nl()
        if xx>=0 {
            txt.print(">=0\n")
        } else {
            txt.print("<0\n")
        }
        if xx<=0 {
            txt.print("<=0\n")
        } else {
            txt.print(">0\n")
        }

        return



        if xx {             ; doesn't use stack...
            xx++
        }

        xx = xx+1           ; doesn't use stack...

        if 8<xx {
        }

        if xx+1 {             ; TODO why does this use stack?
            xx++
        }

        xx = xx & %0001     ; doesn't use stack...

        if xx & %0001 {     ; TODO why does this use stack?
            xx--
        }

        do {
            xx++
        } until xx+1

        while xx+1 {
            xx++
        }
    }
}
