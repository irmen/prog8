%import textio
%zeropage basicsafe

cbm2 {
    sub SETTIM(ubyte a, ubyte b, ubyte c) {
    }
    sub RDTIM16() -> uword {
        return 0
    }
}

main {
    sub start() {
        ubyte value
        uword wvalue
        ubyte other = 99
        uword otherw = 99

        value=13
        wvalue=99
        txt.print_ub(value*value)
        txt.spc()
        txt.print_uw(wvalue*wvalue)
        txt.nl()


        txt.print("byte multiply..")
        cbm.SETTIM(0,0,0)
        repeat 100 {
            for value in 0 to 255 {
                cx16.r0L = value*other
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("byte squares...")
        cbm.SETTIM(0,0,0)
        repeat 100 {
            for value in 0 to 255 {
                cx16.r0L = value*value
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("word multiply..")
        cbm.SETTIM(0,0,0)
        repeat 50 {
            for wvalue in 0 to 255 {
                cx16.r0 = wvalue*otherw
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("word squares...")
        cbm.SETTIM(0,0,0)
        repeat 50 {
            for wvalue in 0 to 255 {
                cx16.r0 = wvalue*wvalue
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()
    }
}
