%option no_sysinit
%zeropage dontuse


main {
    sub start() {
        derp()
        derp2()
    }

    asmsub derp() {
        %asm {{
            nop
            nop
        }}
    }

    inline asmsub derp2() {
        %asm {{
            nop
            nop
        }}
    }
}
