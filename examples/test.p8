%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        regparams(true, 42, 9999)
    }

    sub regparams(bool arg1 @R0, byte arg2 @R1, uword arg3 @R2) {
        txt.print_bool(arg1)
        txt.nl()
        txt.print_b(arg2)
        txt.nl()
        txt.print_uw(arg3)
        txt.nl()

        cx16.r0=0
        cx16.r1=$ffff
        cx16.r2=11222

        txt.print_bool(arg1)
        txt.nl()
        txt.print_b(arg2)
        txt.nl()
        txt.print_uw(arg3)
        txt.nl()
    }
}
