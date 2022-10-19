main {
    asmsub multi() -> ubyte @A, ubyte @Pc {
        %asm {{
            lda #42
            sec
            rts
        }}
    }

    sub start() {
        ubyte value

        value = multi()

        while 0==multi() {
            value++
        }

        if multi() {
            value++
        }
    }
}
