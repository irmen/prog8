%import textio
%zeropage basicsafe

main {
    sub start() {
        cx16.mouse_config(1, 0)

        repeat {
            ubyte mb = cx16.mouse_pos()
            txt.print_uw(cx16.r0)
            txt.spc()
            txt.print_uw(cx16.r1)
            txt.spc()
            txt.print_ub(mb)
            txt.nl()
        }
    }
}
