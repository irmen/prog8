%import textio
%zeropage basicsafe

main {
    word bignum

    struct Node {
        ubyte id
        str name
        bool flag
        word counter
    }

    sub start() {
        ^^Node test = []
        bignum = 11111
        test.counter = 22222

        txt.print_w(bignum)
        txt.spc()
        bignum++

        txt.print_w(bignum)
        txt.spc()
        bignum--

        txt.print_w(bignum)
        txt.nl()

        txt.print_w(test.counter)
        txt.spc()
        test.counter ++

        txt.print_w(test.counter)
        txt.spc()
        test.counter --

        txt.print_w(test.counter)
        txt.nl()

        txt.print_w(bignum)
        txt.spc()
        bignum+=5555

        txt.print_w(bignum)
        txt.spc()
        bignum-=5555

        txt.print_w(bignum)
        txt.nl()

        txt.print_w(test.counter)
        txt.spc()
        test.counter += 5555

        txt.print_w(test.counter)
        txt.spc()
        test.counter -= 5555

        txt.print_w(test.counter)
        txt.nl()
    }
}
