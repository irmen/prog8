%import textio
%import floats
%zeropage basicsafe


main {
    sub start()  {
        ubyte[] ba = [11,22,33]
        uword[] wa = [1111,2222,3333]
        float[] fa = [1.1, 2.2, 3.3]

        txt.print_ub(ba[1])
        txt.nl()
        txt.print_uw(wa[1])
        txt.nl()
        floats.print_f(fa[1])       ; TODO FIX FLOAT PRINT IN VM
        txt.nl()

        ubyte index=1
        ubyte calc=1
        ba[index] += 1
        wa[index] += 1
        fa[index] += 1
        txt.print_ub(ba[1])
        txt.nl()
        txt.print_uw(wa[1])
        txt.nl()
        floats.print_f(fa[1])       ; TODO FIX FLOAT PRINT IN VM
        txt.nl()
    }
}
