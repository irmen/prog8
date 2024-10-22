%import floats
%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        ubyte[4] values
        uword[4] wvalues
        float[4] fvalues
        cx16.r0L = 0
        cx16.r1L = 3
        values[cx16.r0L+2] = if cx16.r1L>2  99 else 111
        wvalues[cx16.r0L+2] = if cx16.r1L>2  9999 else 1111
        fvalues[cx16.r0L+2] = if cx16.r1L>2  9.99 else 1.111

        txt.print_ub(values[2])
        txt.nl()
        txt.print_uw(wvalues[2])
        txt.nl()
        floats.print(fvalues[2])
    }
}
