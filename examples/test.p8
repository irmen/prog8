%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        float @shared fv = 3.1415927
        txt.print_f(floats.log2(fv))
    }
}
