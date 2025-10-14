%import textio
%import math
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        long @shared lv, lv2

        cx16.r0L = if lv==9999  then 99 else 42
        txt.print_ub(cx16.r0L)
        txt.nl()

        lv2 = if lv==9999  then 999999 else 424242
        txt.print_l(lv2)
        txt.nl()
        lv=9999
        lv2 = if lv==9999  then 999999 else 424242
        txt.print_l(lv2)
        txt.nl()


;        long @shared lv1 = $12345678
;
;        txt.print_ubhex(msb(lv1), true)
;        txt.spc()
;        txt.print_ubhex(lsb(lv1), true)
;        txt.spc()
;        txt.print_ubhex(lv1 as ubyte, true)
;        txt.nl()
;        txt.print_uwhex(msw(lv1), true)
;        txt.spc()
;        txt.print_uwhex(lsw(lv1), true)
;        txt.nl()

;        ; TODO support long+1 / -1 expressions....
;        cx16.r4 = msw(lv1-1)
;        cx16.r5 = lsw(lv1-1)
;        txt.print_uwhex(cx16.r4, true)
;        txt.print_uwhex(cx16.r5, true)
;        txt.nl()
;
;        txt.print_ubhex((lv1-1) as ubyte, true)
;        txt.nl()
    }
}
