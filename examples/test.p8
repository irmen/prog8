%import textio
%zeropage basicsafe
%option no_sysinit

main {

    sub start() {
        uword @shared ww

;        ww += 5
;        ww += 256
;        ww += $9500
;        ; TODO optimize into  just a single  ww += $9605
;
;        ww *= 3
;        ww *= 4
;        ww *= 5
;        ; TODO optimize into just a single ww *= 60

        txt.print_uwhex(ww, true)
        txt.print_uwhex(ww + 5, true)
        txt.print_uwhex(ww + 256, true)
        txt.print_uwhex(ww + 512, true)
        txt.print_uwhex(ww + $9b00, true)
        txt.print_uwhex(ww + $9b22, true)
        txt.nl()
        txt.print_uwhex(ww - 5, true)
        txt.print_uwhex(ww - 256, true)
        txt.print_uwhex(ww - 512, true)
        txt.print_uwhex(ww - $9b00, true)
        txt.print_uwhex(ww - $9b22, true)
    }
}
