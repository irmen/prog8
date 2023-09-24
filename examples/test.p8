%import textio
%import verafx
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {

        const word MULTIPLIER = 431

        ; verify results:
        for value in -50 to 50 {
            if value*MULTIPLIER != verafx.muls(value, MULTIPLIER) {
                txt.print("verafx muls error\n")
                sys.exit(1)
            }
        }


        word value
        txt.print("verafx muls...")
        cbm.SETTIM(0,0,0)
        for value in -50 to 50 {
            repeat 250 void verafx.muls(value, MULTIPLIER)
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("6502 muls...")
        cbm.SETTIM(0,0,0)
        for value in -50 to 50 {
            repeat 250 cx16.r0s = value*MULTIPLIER
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

    }
}

