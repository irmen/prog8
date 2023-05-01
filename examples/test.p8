%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        txt.print("hello")
        ; foobar()
    }

    asmsub foobar() {
        %asm {{
            nop
            rts

        }}
    }
}

