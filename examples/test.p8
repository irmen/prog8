%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        cx16.r2 = $eeee
        txt.print_uwhex(cx16.r2, true)
        txt.nl()
        cx16.r2,void = thing2()      ; TODO fix IR+6502 codegen missing an ext.b  (it is present when thing only returns single returnvalue)
        txt.print_uwhex(cx16.r2, true)
        txt.nl()
        cx16.r2 = thing()      ; codegen does ext.b correctly here
        txt.print_uwhex(cx16.r2, true)
        txt.nl()
    }

    asmsub thing() -> ubyte @A {
        %asm {{
            lda #$44
            rts
        }}
    }

    asmsub thing2() -> ubyte @A, bool @Pc {
        %asm {{
            lda #$aa
            clc
            rts
        }}
    }
}
