%import textio
%zeropage basicsafe


main {
    sub start() {
        bool @shared zz
        ubyte xx

        while xx<42 {
            xx++
        }

        txt.print_ub(xx)
    }
}
