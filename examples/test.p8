%import textio
%zeropage basicsafe

main {
    sub start() {
        sys.set_carry()
        txt.print_ub(if_cc 0 else 1)
        txt.nl()
        sys.clear_carry()
        txt.print_ub(if_cc 0 else 1)
        txt.nl()
        sys.set_carry()
        txt.print_ub(if_cs 0 else 1)
        txt.nl()
        sys.clear_carry()
        txt.print_ub(if_cs 0 else 1)
        txt.nl()
    }
}

;%import textio
;%zeropage basicsafe
;
;main {
;    struct Node {
;        ubyte id
;        str name
;        uword array
;    }
;
;    ^^Node @shared @zp node = 2000
;
;    sub start() {
;        txt.print_uw(node)
;        txt.spc()
;        cx16.r0 = &node.array       ; TODO don't clobber node pointer itself!!
;        txt.print_uw(cx16.r0)
;        txt.spc()
;        txt.print_uw(node)
;        txt.nl()
;    }
;}
