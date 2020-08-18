%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {
;        byte[] data = [11,22,33,44,55,66]
;        word[] dataw = [1111,2222,3333,4444,5555,6666]
;
;        byte d
;        word w
;
;        for d in data {
;            c64scr.print_b(d)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        for w in dataw {
;            c64scr.print_w(w)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')


        ubyte bb
        ubyte from = 10
        ubyte end = 20

        for bb in from to end step 3 {
            c64scr.print_ub(bb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
        for bb in end to from step -3 {
            c64scr.print_ub(bb)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        word ww
        word fromw = -10
        word endw = 21

        for ww in fromw to endw step 3 {
            c64scr.print_w(ww)
            c64.CHROUT(',')
        }

        c64.CHROUT('\n')
        for ww in endw to fromw step -3 {
            c64scr.print_w(ww)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
    }
}
