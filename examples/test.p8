%import bcd
%import textio
%zeropage basicsafe
;;%option no_sysinit

main {
    sub start() {
        long @shared lv1 = $12345678
        cx16.r14=$eeee
        cx16.r15=$ffff

        txt.print_ubhex((lv1 & $f) as ubyte, false)
        txt.nl()

        ; this should print 08 07 06 05 04 03 02 01
        repeat 8 {
            txt.print_ubhex((lv1 & $f) as ubyte, false)
            txt.spc()
            lv1 >>= 4
        }

        ; TODO support long+1 / -1 expressions....
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
