%import textio
%zeropage basicsafe

main {

    sub start()  {
        uword[] texts1 = [ 1,2,3 ]
        uword[] @nosplit texts2 = [ 1,2,3 ]

        cx16.r4 = texts1
        cx16.r5 = texts2

;        txt.print_uwhex(&texts1, true)
;        txt.spc()
;        txt.print_uwhex(cx16.r4, true)
;        txt.nl()
;        txt.print_uwhex(&texts2, true)
;        txt.spc()
;        txt.print_uwhex(cx16.r5, true)
;        txt.nl()

        cx16.r4 = &texts1
        cx16.r5 = &texts2

;        txt.print_uwhex(&texts1, true)
;        txt.spc()
;        txt.print_uwhex(cx16.r4, true)
;        txt.nl()
;        txt.print_uwhex(&texts2, true)
;        txt.spc()
;        txt.print_uwhex(cx16.r5, true)
;        txt.nl()
    }
}
