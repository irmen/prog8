%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        float @shared f1 = 333.666
        float @shared f2 = 123.456
        float a = floats.mod(f1, f2)
        txt.print_f(a)
        txt.nl()
    }
}
