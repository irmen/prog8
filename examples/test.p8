%import textio
%import floats
%zeropage basicsafe


main {
    sub start() {
        float fl
        fl = -3.14
        floats.print_f(abs(fl))         ; WHY IS THIS GETTING A BOOLEAN CAST???
        txt.nl()
    }
}
