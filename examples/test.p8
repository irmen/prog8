%import textio
%zeropage basicsafe
%option no_sysinit

main {
    ; Test the routine
    sub start() {

        ubyte x,y,z  = 11,22,33

        txt.print_ub(x)
        txt.spc()
        txt.print_ub(y)
        txt.spc()
        txt.print_ub(z)
        txt.nl()

        x,y,z = 99,88,77
        x,y,z = multi()

        txt.print_ub(x)
        txt.spc()
        txt.print_ub(y)
        txt.spc()
        txt.print_ub(z)
        txt.nl()
    }

    sub multi() -> ubyte, ubyte, ubyte {
        cx16.r0++
        return cx16.r0L, cx16.r1L, cx16.r2L
    }
}

