%import textio
%import floats

main {
    ^^byte @shared sbptr
    ^^ubyte @shared ubptr
    ^^bool @shared bptr
    ^^word @shared wptr
    ^^float @shared fptr

    struct List {
        ubyte value
        ^^List next
    }

    sub start() {
        ^^List l1 = 1000
        ^^List l2 = 1100

        txt.print_ub(sizeof(List))
        txt.spc()
;        txt.print_ub(sizeof(l1^^)
;        txt.nl()

        txt.print_ub(sizeof(bptr))
        txt.spc()
        txt.print_ub(sizeof(wptr))
        txt.spc()
        txt.print_ub(sizeof(fptr))
        txt.nl()

;        txt.print_ub(sizeof(bptr^^))
;        txt.spc()
;        txt.print_ub(sizeof(wptr^^))
;        txt.spc()
;        txt.print_ub(sizeof(fptr^^))
;        txt.nl()
    }
}
