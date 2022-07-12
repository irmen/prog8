%import textio
%zeropage basicsafe


main {
    sub start() {
        bool @shared zz
        ubyte xx
        ubyte yy

        while xx<42 {
            xx = yy+99
            xx = yy*5
        }

        txt.print_ub(xx)
    }
}
