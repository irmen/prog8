%import textio
%import diskio
%zeropage basicsafe

main {
    struct element {
        ubyte type
        long  x
        long  y
    }

    sub start() {
;        cx16.r5L = 10
;        txt.print_l(cx16.r5L as long * $2000)
;        txt.spc()
;        txt.print_l(($2000 as long) * cx16.r5L)     ; TODO fix long result? or wait till the long consts have landed?
;        txt.nl()

        cx16.rambank(15)
        txt.print_l(diskio.load_size(10, $1000, $6000))
        txt.nl()
        cx16.rambank(25)
        txt.print_l(diskio.load_size(10, $1000, $f000))


;        ^^element myElement = $6000
;        myElement.y = $44444444
;        long @shared lv
;
;        myElement.y -= lv
;
;        txt.print_ulhex(myElement.y, true)
    }
}
