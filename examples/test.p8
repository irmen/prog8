%import floats
%import textio
%zeropage basicsafe

main {
    sub start() {
        float @shared f1 = floats.π
        f1++
        f1 = sqrt(f1 * 99)
        txt.print_f(f1)
        txt.nl()
    }
}
