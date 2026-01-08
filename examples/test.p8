%import textio
%import strings
%zeropage basicsafe

main {
    sub start() {
        long @shared lv = $11223344

        txt.print_ubhex(lsb(lv>>8), true)       ; $33
        txt.spc()
        txt.print_ubhex(lv>>8 as ubyte, true)   ; $33
        txt.spc()
        txt.print_ubhex(msb(lsw(lv)), true)     ; $33
        txt.nl()
        txt.print_ubhex(lsb(lv>>16), true)      ; $22
        txt.spc()
        txt.print_ubhex(lv>>16 as ubyte, true)  ; $22
        txt.spc()
        txt.print_ubhex(lsb(msw(lv)), true)     ; $22
        txt.nl()
        txt.print_ubhex(lsb(lv>>24), true)      ; $11
        txt.spc()
        txt.print_ubhex(lv>>24 as ubyte, true)  ; $11
        txt.spc()
        txt.print_ubhex(msb(lv), true)          ; $11
        txt.nl()
        txt.nl()


        txt.print_uwhex(msw(lv<<16), true)       ; $3344
        txt.spc()
        txt.print_uwhex(lsw(lv), true)     ; $3344
        txt.nl()
        txt.nl()

        txt.print_ubhex(msb(lv<<8), true)       ; 22
        txt.spc()
        txt.print_ubhex(lsb(msw(lv)), true)     ; 22
        txt.nl()
        txt.print_ubhex(msb(lv<<16), true)      ; 33
        txt.spc()
        txt.print_ubhex(msb(lsw(lv)), true)     ; 33
        txt.nl()
        txt.print_ubhex(msb(lv<<24), true)      ; 44
        txt.spc()
        txt.print_ubhex(lsb__long(lv), true)    ; 44
        txt.nl()
    }
}
