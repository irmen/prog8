%zeropage basicsafe

main {

    sub start() {
        ubyte @shared foo = derp(99)
    }

    asmsub derp(ubyte xx @Y) -> ubyte @ A {
        %asm {{
            rts

        }}
    }
}

