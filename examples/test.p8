%import textio
%zeropage basicsafe

main {
    sub start() {
        word @shared wv = $1122
        long @shared lv = $11223344

;        - lsb(word>>8) and (word>>8) as ubyte -> msb(word)
;        - msb(word<<8) -> lsb(word)

        txt.print_ubhex(lsb(wv>>8), true)       ; $11
        txt.spc()
        txt.print_ubhex(msb(wv), true)          ; $11
        txt.nl()
        txt.print_ubhex(wv>>8 as ubyte, true)   ; $11
        txt.spc()
        txt.print_ubhex(msb(wv), true)          ; $11
        txt.nl()
        txt.print_ubhex(msb(wv<<8), true)       ; $22
        txt.spc()
        txt.print_ubhex(lsb(wv), true)          ; $22
        txt.nl()
        txt.nl()

;        - lsw(long>>16) and  (long>>16) as uword  -> msw(long)
;        - msw(long<<16) -> lsw(long)
        txt.print_uwhex(lsw(lv>>16), true)      ; $1122
        txt.spc()
        txt.print_uwhex(msw(lv), true)       ; $1122
        txt.nl()
        txt.print_uwhex(lv>>16 as uword, true)  ; $1122
        txt.spc()
        txt.print_uwhex(msw(lv), true)       ; $1122
        txt.nl()
        txt.print_uwhex(msw(lv<<16), true)      ; $3344
        txt.spc()
        txt.print_uwhex(lsw(lv), true)       ; $3344
        txt.nl()
        txt.nl()
    }
}
