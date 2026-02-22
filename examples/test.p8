%import textio
%import strings
%zeropage basicsafe

main {
    sub start() {
        void strings.copy(peekw(cx16.r0 + (cx16.r1+cx16.r2)*$0002), "zzzz")

        cx16.r11 = 4000
        cx16.r12 = 5000
        cx16.r0 = 111

        pokew(cx16.r12, 55222)
        pokew(cx16.r12+10, 44333)

        pokew(cx16.r11+2, peekw(cx16.r12))
        txt.print_uw(peekw(4002))
        txt.nl()

        pokew(cx16.r11+2, peekw(cx16.r12+10))
        txt.print_uw(peekw(4002))
        txt.nl()

        pokew(cx16.r11+2, cx16.r0*cx16.r0)
        txt.print_uw(peekw(4002))
        txt.nl()

        ubyte @shared b1, b2
        uword @shared w

        if b1+b2 == lsb(w)
            cx16.r0++

        if b1+b2 == msb(w)
            cx16.r0++

        ; TODO test divmod results
    }
}
