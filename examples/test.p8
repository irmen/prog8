%import textio

main {
    ubyte begin = 10
    ubyte end = 20

    sub start() {
        ubyte xx
        for xx in begin to end step 3 {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()
        for xx in end to begin step -3 {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()
    }
}
