%import textio
%import conv

%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        for cx16.r0L in 0 to 255 {
            txt.print(conv.str_ub0(cx16.r0L))
            txt.nl()
        }
        txt.nl()
        txt.nl()
        for cx16.r0L in 0 to 255 {
            txt.print(conv.str_ub(cx16.r0L))
            txt.nl()
        }
        txt.nl()
        txt.nl()
        for cx16.r0sL in -128 to 127 {
            txt.print(conv.str_b(cx16.r0sL))
            txt.nl()
        }
        txt.nl()
        txt.nl()
    }
}
