%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte @shared xx
        repeat {
            xx++
            if xx==10
                break
        }
        txt.print_ub(xx)
        txt.nl()

        while xx<50 {
            xx++
            if xx==40
                break
        }
        txt.print_ub(xx)
        txt.nl()

        do {
            xx++
            if xx==80
                break
        } until xx>100
        txt.print_ub(xx)
        txt.nl()

        for xx in 0 to 25 {
            if xx==20
                break
        }
        txt.print_ub(xx)
        txt.nl()
    }
}
