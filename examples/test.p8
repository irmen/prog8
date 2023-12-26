%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        sys.push(11)
        sys.pushw(2222)
        floats.push(floats.π)
        cx16.r2++

        float pi = floats.pop()
        floats.print_f(pi)
        txt.nl()

        cx16.r1 = sys.popw()
        cx16.r0L = sys.pop()

        txt.print_ub(cx16.r0L)
        txt.nl()
        txt.print_uw(cx16.r1)
        txt.nl()

    }
}
