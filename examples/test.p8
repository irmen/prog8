%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        cx16.r0L = returns3()
        cx16.r0L, cx16.r1L, cx16.r2L, cx16.r3L = returns3()
        txt.print_uwhex()
        txt.print_uwhex(1, true, 2, 3)
    }

    asmsub returns3() -> ubyte @A, ubyte @X, bool @Pc {
        %asm {{
            rts
        }}
    }
}

