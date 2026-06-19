%import textio
%zeropage basicsafe
%option no_sysinit

main {
    struct Node {
        ubyte x
        ubyte y
    }

;    ^^Node @requirezp ptr
;
;    sub start() {
;        txt.print_uwhex(&ptr, true)
;    }

    sub start() {
        ^^Node p1 = [22,33]
        txt.print_ub(p1.x)
        txt.spc()
        txt.print_ub(p1.y)
        txt.nl()

        clear(p1)

        txt.print_ub(p1.x)
        txt.spc()
        txt.print_ub(p1.y)
        txt.nl()
    }

    sub clear(^^Node ptr @R11) {
        ptr.x=0
        ptr.y=0
    }

}
