%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe


main {
    sub start() {
        float[] floats = [1.1, 2.2]
        ubyte index=1

        c64flt.print_f(floats[0])
        c64flt.print_f(floats[1])
        floats[0] = 9.99
        floats[index] = 8.88
        c64flt.print_f(floats[0])
        c64flt.print_f(floats[1])

    }
}


