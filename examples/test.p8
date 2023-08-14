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
        uword wvalue
        uword wvalue2

        txt.print("word square..")
        cbm.SETTIM(0,0,0)
        repeat 200 {
            for wvalue in 0 to 200 {
                cx16.r0 = wvalue*wvalue
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("word square via multiply new..")
        cbm.SETTIM(0,0,0)
        wvalue2 = wvalue
        repeat 200 {
            for wvalue in 0 to 200 {
                cx16.r0 = wvalue*wvalue2
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("word square verify..")
        for wvalue in 0 to 200 {
            wvalue2 = wvalue
            if wvalue*wvalue != wvalue*wvalue2 {
                txt.print("different! ")
                txt.print_uw(wvalue)
                txt.spc()
                txt.spc()
                txt.print_uw(wvalue*wvalue)
                txt.spc()
                txt.print_uw(wvalue*wvalue2)
                sys.exit(1)
            }
        }
        txt.nl()
    }
}
