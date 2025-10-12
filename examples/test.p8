%import bcd
%import textio
%zeropage basicsafe
;;%option no_sysinit

main {
    sub start() {
        long @shared lv1 = $12345678

        ; TODO support long+1 / -1 expressions....
        cx16.r4 = msw(lv1<<1)
        cx16.r5 = lsw(lv1<<1)
        txt.print_uwhex(cx16.r4, true)
        txt.print_uwhex(cx16.r5, true)
        txt.nl()

        txt.print_ubhex((lv1<<1) as ubyte, true)
        txt.nl()
    }
}
