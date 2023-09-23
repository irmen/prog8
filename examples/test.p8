%import textio
%import floats

main {
    sub start() {
        ubyte from = 10
        ubyte compare=9
        if from==compare
            goto equal

        txt.print("from is not compare\n")
equal:

        ubyte end = 15
        ubyte xx
        for xx in from to end {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()

        ubyte ten=9
        if from!=ten
            txt.print("from is not 10\n")
    }
}
