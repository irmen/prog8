%import textio
%import floats
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte v1 = 11
        ubyte v2 = 88
        byte v1s = 22
        byte v2s = -99

        uword w1 = 1111
        uword w2 = 8888
        word w1s = 2222
        word w2s = -9999

        float f1 = 1111.1
        float f2 = -999.9

        floats.print_f(floats.minf(f1, f2))
        txt.spc()
        floats.print_f(floats.maxf(f1, f2))
        txt.nl()

        txt.print_uw(min(v1, v2))
        txt.spc()
        txt.print_w(min(v1s, v2s))
        txt.spc()
        txt.print_uw(max(v1, v2))
        txt.spc()
        txt.print_w(max(v1s, v2s))
        txt.nl()

        txt.print_uw(min(w1, w2))
        txt.spc()
        txt.print_w(min(w1s, w2s))
        txt.spc()
        txt.print_uw(max(w1, w2))
        txt.spc()
        txt.print_w(max(w1s, w2s))
        txt.nl()
    }
}

