%import textio
%zeropage basicsafe

main {
    sub start() {
        sys.pushl($aabb1234)
        long lv = sys.popl()
        txt.print_ulhex(lv, false)

;        cx16.r0 = sys.popw()
;        cx16.r1 = sys.popw()

;        txt.print_uwhex(cx16.r0, false)
;        txt.print_uwhex(cx16.r1, false)
    }
}
