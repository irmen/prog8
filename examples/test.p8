%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    ubyte bb = 1
    uword ww = 1
    float ff = 1.111
    str derp = "zzzz"

    sub start() {
        func()
        txt.print(derp)
    }

    sub func() {
        ubyte fbb = 1
        uword fww = 1
        float fff = 1.111

        txt.print_ub(fbb)
        txt.spc()
        txt.print_uw(fww)
        txt.spc()
        floats.print_f(fff)
        txt.nl()
        txt.print_ub(bb)
        txt.spc()
        txt.print_uw(ww)
        txt.spc()
        floats.print_f(ff)
        txt.nl()
        txt.print_ub(block2.bb)
        txt.spc()
        txt.print_uw(block2.ww)
        txt.spc()
        floats.print_f(block2.ff)
        txt.nl()

        fbb++
        fww++
        fff += 1.1
        bb++
        ww++
        ff += 1.1
        block2.bb++
        block2.ww++
        block2.ff += 1.1
    }
}

block2 {
    ubyte bb = 1
    uword ww = 1
    float ff = 1.111
}
