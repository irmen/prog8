%zeropage basicsafe

main {
    sub start() {
        ; TODO test clamp long, min long, max long

        uword wvar

        thing(peekw(&wvar+cx16.r0L))
        thing(peekw($e844))
        thing(peekw(cx16.r0))
    }

    asmsub thing(uword value @R0) {
        %asm {{
            rts
        }}
    }

}
