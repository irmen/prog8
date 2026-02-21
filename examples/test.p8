%import textio
%zeropage basicsafe

main {
    sub start() {
        pokew(cx16.r11+2, peekw(cx16.r11))
        pokew(cx16.r11+2, cx16.r0*cx16.r0)


        ubyte @shared b1, b2
        uword @shared w

        if b1+b2 == lsb(w)
            cx16.r0++

        if b1+b2 == msb(w)
            cx16.r0++

        ; TODO test divmod results
    }
}
