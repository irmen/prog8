
main {

    sub start() {
        uword ww

        main.routine2.num = ww+1
        main.routine2.switch=true

        routine2(ww+1, true)

        repeat {
        }

    }

    asmsub routine2(uword num @AY, ubyte switch @X) {
        %asm {{
            adc #20
            rts
        }}
    }

}
