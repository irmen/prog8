%import textio
%zeropage basicsafe

main {
    struct element {
        ubyte type
        long  x
        long  y
    }

    sub start() {
        long @shared l1 = $e1fa84c6
        long @shared l2 = -1
        long @shared l3 = $ffffffff
        long @shared l4 = $7fffffff
        ^^long lptr = 50000

        l1 ^= -1
        l2 ^= $ffffffff
        l3 ^= $7fffffff
        l3 ^= l4

        lptr^^ = 82348234
        l2 = lptr^^

        lptr^^ = 0                ; TODO fix crash
        lptr^^ = l2               ; TODO fix crash
        lptr^^ = 82348234+l2      ; TODO fix crash
        l3 = lptr^^+1             ; TODO fix crash


;        cx16.r5L = 10
;        txt.print_l(cx16.r5L as long * $2000)
;        txt.spc()
;        txt.print_l(($2000 as long) * cx16.r5L)     ; TODO fix long result? or wait till the long consts have landed?
;        txt.nl()

;        ^^element myElement = $6000
;        myElement.y = $12345678
;        long @shared lv = $10101010
;        cx16.r0 = $ffff
;
;        myElement.y += lv+cx16.r0
;        txt.print_ulhex(myElement.y, true)
;        txt.spc()
;        myElement.y -= lv+cx16.r0
;        txt.print_ulhex(myElement.y, true)
    }
}
