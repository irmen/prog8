%import textio
%zeropage basicsafe

main {
    sub start() {
        uword @shared xx=$ea31
        ;xx &= $00ff
        ;xx = lsb(xx)
        ;txt.print_uwhex(xx, true)
        ;xx = $ea31
        ;xx &= $ff00
        ; xx = msb(xx)
;        %asm {{
;            nop
;            nop
;        }}
        xx >>= 8
        %asm {{
           nop
        }}
        xx <<= 8
        %asm {{
           nop
        }}
        txt.print_uwhex(xx, true)
    }
}
