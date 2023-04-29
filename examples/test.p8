%import textio
%import floats
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {

        ubyte bb = 199
        txt.print_ub(sqrt(bb))
        txt.nl()
        float fl = 199.99
        floats.print_f(floats.sqrtf(fl))
        txt.nl()
    }
}

