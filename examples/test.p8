%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte[] b_array = [11,22,33,44]
        uword[] w_array = [1111,2222,3333,4444]
        uword[] @split split_array = [1111,2222,3333,4444]
        ubyte index = 2

        uword value = 9999
        w_array[index] = value
        split_array[index] = value

        uword pstep = w_array[index]
        uword psteps = split_array[index]

        txt.print_uw(pstep)
        txt.nl()
        txt.print_uw(psteps)
        txt.nl()

        uword @zp ptr = &w_array[index]
        txt.print_uw(peekw(ptr))
        txt.nl()
        txt.print_uwhex(&w_array, true)
        txt.spc()
        txt.print_uwhex(ptr, true)
        txt.nl()
        %breakpoint
        ptr = &split_array
        txt.print_uw(peekw(ptr))
        txt.nl()
        txt.print_uwhex(&w_array, true)
        txt.spc()
        txt.print_uwhex(ptr, true)
        txt.nl()

        ptr = &b_array[index]
        txt.print_ub(peek(ptr))
        txt.nl()
        txt.print_uwhex(&b_array, true)
        txt.spc()
        txt.print_uwhex(ptr, true)
        txt.nl()

    }
}

