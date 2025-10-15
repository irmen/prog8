%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte w,h

        w,h = txt.size()
        txt.print("Screen size: ")
        txt.print_ub(w)
        txt.spc()
        txt.print_ub(h)
        txt.nl()
        txt.print("width/height=")
        txt.print_ub(txt.width())
        txt.spc()
        txt.print_ub(txt.height())
        txt.nl()


;        long @shared lv, lv2
;
;        lv = $11111111
;        lv2 = $55555555
;
;        lv = lv | lv2 ^ 999999
;
;        txt.print_ulhex(lv, true)           ; $555b177b


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
