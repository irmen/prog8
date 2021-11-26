%import textio
%import conv

main {

    sub start() {

        ubyte  @shared xx=20
        ubyte  @shared yy=10

        routine2()
        routine2()
        routine2()

        repeat {
            xx++
        }
    }

    asmsub routine2() -> ubyte @A {
        %asm {{
            adc #20
            rts
        }}
    }

}
