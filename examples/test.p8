%import c64flt
%zeropage basicsafe

main {
    sub start() {
        float f1 = 2.2
        float f2 = 1.0
        float f4 = 4.0
        float f5 = 5.0

        f1 /= f2+f4
        c64flt.print_f(f1)
        c64.CHROUT('\n')
    }
}
