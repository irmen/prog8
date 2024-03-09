%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        derp("hello")
        mult3("hello")
    }

    sub derp(str arg) -> str {
        cx16.r0++
        return arg
    }

    asmsub mult3(str input @XY) -> ubyte @A, str @XY {
        %asm {{
            lda  #99
            ldx  #100
            ldy  #101
            rts
        }}
    }
}
