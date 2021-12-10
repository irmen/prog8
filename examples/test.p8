%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte @zp xx
        for xx in 0 to 10 {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()

        for cx16.r0L in 0 to 10 {
            txt.print_ub(cx16.r0L)
            txt.spc()
        }
        txt.nl()

        for main.derp.xx in 0 to 10 {
            txt.print_ub(main.derp.xx)
            txt.spc()
        }

        derp()
        txt.nl()
    }

    sub derp() {
        ubyte xx

        xx++
    }
}
