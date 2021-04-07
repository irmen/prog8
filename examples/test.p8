%import textio
%zeropage dontuse

main {

    sub start() {
        ubyte num = cx16.numbanks()

        txt.print_ub(num)
        txt.nl()
    }
}
