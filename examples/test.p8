%import textio
%import floats
%zeropage basicsafe

main {
    float[4] array

    sub testpow(float x) -> float {
        return 1.234
    }

    sub start() {
        float res
        ubyte j = 2
        res += testpow(1.234)
        floats.print_f(res)
        txt.nl()
        floats.print_f(array[j])
        txt.nl()
        txt.nl()

        array[j] += testpow(1.234)
        floats.print_f(res)
        txt.nl()
        floats.print_f(array[j])
        txt.nl()
    }
}
