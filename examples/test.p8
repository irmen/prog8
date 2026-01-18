%import textio
%import strings
%import floats
%option no_sysinit
%zeropage basicsafe


main {
    float f1 = 3.1415927
    sub start() {
        txt.print_f(sqrt(f1))
    }
}
