%import textio
%zeropage basicsafe


main {
    sub start() {
        bool status
        cx16.r0L, status = func()
        if_cs {
            cx16.r0L++
        }
    }

    asmsub func() -> ubyte @A, bool @Pv {
        %asm {{
            lda  #99
            sec
            rts
        }}
    }
}
