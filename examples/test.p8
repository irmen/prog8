%option enable_floats
%import textio
%zeropage basicsafe
%option no_sysinit

main {

    float f

    sub start() {
        txt.print("classic float pointer+1: ")
        txt.print_uwhex(&f, true)
        txt.spc()
        txt.print_uwhex(&f + 1, true)
        txt.spc()
        txt.print_uw(&f + 1 - &f)
        txt.nl()

        txt.print("typed float pointer+1: ")
        txt.print_uwhex(&&f, true)
        txt.spc()
        txt.print_uwhex(&&f + 1, true)
        txt.spc()
        txt.print_uw(&&f + 1 - &&f)
        txt.nl()
    }
}
