%import buffers
%import textio
%zeropage basicsafe

main {

    sub start() {
        smallstack.init()
        txt.print("free ")
        txt.print_ub(smallstack.free())
        txt.nl()

        smallstack.push(123)
        smallstack.pushw(55555)
        txt.print("free ")
        txt.print_ub(smallstack.free())
        txt.print(" size ")
        txt.print_ub(smallstack.size())
        txt.nl()

        txt.print_uw(smallstack.popw())
        txt.spc()
        txt.print_ub(smallstack.pop())
        txt.nl()
    }
}
