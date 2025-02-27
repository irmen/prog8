%import textio
%zeropage basicsafe
%option no_sysinit

main {
    const uword screenwidth = 80
    const ubyte ten = 10
    ubyte @shared vten = 10

    sub start()  {
        ; TODO should print 3 , 3
        cx16.r0L = msb(vten * screenwidth)      ;   TODO in main , vten is casted to uword
        txt.print_ub(cx16.r0L)
        txt.nl()
        txt.print_ub(msb(vten * screenwidth))   ;  TODO in main, vten is casted to uword
        txt.nl()

        ; ok; prints 0, 0
;        cx16.r0L = msb(vten * 80)
;        txt.print_ub(cx16.r0L)
;        txt.nl()
;        txt.print_ub(msb(vten * 80))
;        txt.nl()

    }
}
