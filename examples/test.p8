%import textio
%import floats
%zeropage basicsafe
%import test_stack

main {

    sub start() {

        uword address = $c000
        ubyte ub = 1
        ubyte ub2 = 1

        @(address) = 13

        @(address) <<= ub+ub2

        txt.print_ub(@(address))
        txt.chrout('\n')
        txt.print_ub(13 << (ub+ub2))
        txt.chrout('\n')


        @(address) = 200

        @(address) >>= ub+ub2

        txt.print_ub(@(address))
        txt.chrout('\n')
        txt.print_ub(200 >> (ub+ub2))
        txt.chrout('\n')


        test_stack.test()

    }
}
