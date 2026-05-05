%import textio
%zeropage basicsafe

main {
    sub start() {
        uword lv = 3000
        ^^long longs = 4000
        ^^long longsptr = memory("zzz", 2000, 0)        ; SHOULD BE CONST
        const ^^long longsptr2 = memory("zzz2", 2000, 0)  ; is CONST, already works

        txt.print_uwhex(lv, true)
        txt.nl()
        txt.print_uwhex(longs, true)
        txt.nl()
        txt.print_uwhex(longsptr, true)
        txt.nl()
        txt.print_uwhex(longsptr2, true)
        txt.nl()
    }
}
