%import c64utils
%zeropage basicsafe

~ main {

    sub start() {
        A=0
        Y=0
        ubyte aa =0

        while Y<10 {
            rsave()
            c64scr.print_ub(Y)
            c64.CHROUT(',')
            rrestore()
            Y++
        }
        c64.CHROUT('!')
        c64.CHROUT('!')
        c64.CHROUT('\n')

;        repeat {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;            A--
;        } until A<5
;
;        c64.CHROUT('!')
;        c64.CHROUT('!')
;        c64.CHROUT('\n')
;
;        for A in 0 to 4 {
;            for Y in 0 to 3 {
;                c64scr.print_ub(A)
;                c64.CHROUT(',')
;                c64scr.print_ub(Y)
;                c64.CHROUT('\n')
;            }
;        }
    }

}
