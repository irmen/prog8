%import textio
%import conv

%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        repeat 40 txt.nl()
        txt.print("int to string tests\n\n")

        for cx16.r0L in 0 to 255 {
            txt.print_ub0(cx16.r0L)
            txt.spc()
            txt.spc()
        }
        txt.nl()
        txt.nl()
        for cx16.r0L in 0 to 255 {
            txt.print_ub(cx16.r0L)
            txt.spc()
            txt.spc()
        }
        txt.nl()
        txt.nl()
        for cx16.r0sL in -128 to 127 {
            txt.print_b(cx16.r0sL)
            txt.spc()
            txt.spc()
        }
        txt.nl()
        txt.nl()

        repeat {}
    }
}
