%import textio
%zeropage basicsafe

main {
    sub start() {
        ubyte @shared xx
        ubyte @shared yy
        ubyte camg

        ubyte @shared interlaced = (camg & $04) != 0

        yy = xx
        yy = yy==7

        yy++
        yy = xx==7
        yy++

;        if xx==99 {
;            xx++
;            yy++
;        }
;        txt.nl()
    }
}
