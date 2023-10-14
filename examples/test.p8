%import textio
%zeropage basicsafe

main {
    sub start() {
        uword[] w_array = [1111,2222,3333,4444]
        uword[] @split split_array = [1111,2222,3333,4444]
        ubyte index = 2

        uword value = 9999
        w_array[index] = value
        split_array[index] = value

        uword pstep = w_array[index]
        uword psteps = split_array[index]
        ;; uword @zp ptr = &w_array[index]

        txt.print_uw(pstep)
        txt.nl()
        txt.print_uw(psteps)
        txt.nl()
;        txt.print_uw(peekw(ptr))
;        txt.nl()

        w_array[index] += 10
        split_array[index] += 10
        txt.print_uw(w_array[index])
        txt.nl()
        txt.print_uw(split_array[index])
        txt.nl()
    }
}

