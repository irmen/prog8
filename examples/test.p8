%import bcd
%import textio
%zeropage basicsafe
;;%option no_sysinit

main {
    sub start() {
        long @shared lv1 = $12345678
        uword @shared w = $1234

        lv1 = bcd.addl(lv1, $11110000)
        bcd.addtol(&lv1, $11110000)
        w = bcd.adduw(w, $1111)

        txt.print_ulhex(lv1, true)
        txt.nl()
        txt.print_uwhex(w, true)
        txt.nl()

        ;lv1 |= -1      ; TODO fix value


;        txt.print_uwhex(msw(lv1<<1), true)
;        txt.print_uwhex(lsw(lv1<<1), true)
;        txt.nl()
;
;        ; TODO support long+1 / -1 expressions....
;
;        cx16.r4 = msw(lv1<<1)
;        cx16.r5 = lsw(lv1<<1)
;        txt.print_uwhex(cx16.r4, true)
;        txt.print_uwhex(cx16.r5, true)
;        txt.nl()
;
;        txt.print_ubhex((lv1<<1) as ubyte, true)
;        txt.nl()
    }
}
