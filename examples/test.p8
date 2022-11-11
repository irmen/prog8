%import textio
%zeropage basicsafe

main {

    sub start() {
        uword[] @shared ptrs = [&x1, &x2, &start, 4242, 4242]
        ubyte x1
        ubyte x2

        txt.print_uwhex(ptrs[0], true)
        txt.spc()
        txt.print_uwhex(ptrs[1], true)
        txt.spc()
        txt.print_uwhex(ptrs[2], true)
        txt.nl()
    }
}
