
main {

    sub start() {

        &ubyte derp = $c000

        @($c000) = not @($c000)
        @($c000) = ~ @($c000)

        ubyte uu
        uu = abs(uu)
        routine2(12345, true)
        repeat {
        }

    }

    asmsub routine2(uword num @AY, ubyte switch @Pc) {
        %asm {{
            adc #20
            rts
        }}
    }

}
