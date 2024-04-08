%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared x,y = multi()
    }

    asmsub multi() -> ubyte @A, ubyte @Y {
        %asm {{
            rts
        }}
    }
}
