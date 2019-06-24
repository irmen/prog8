%import c64utils
%zeropage basicsafe

~ main {

    sub start() {

        ubyte ub = 10
        byte bb=-100
        uword uw = 1000
        word ww = -25000
        ubyte i = 0

        while i < 100 {
            bb += 10
            c64scr.print_b(bb)
            c64.CHROUT(',')
            i++
        }

;        c64scr.print("while1\n")
;        while(ub < 220) {
;            c64scr.print_ub(ub)
;            c64.CHROUT(',')
;            ub += 25
;            if ub < 150 continue else break
;            ub=99
;        }
;        c64.CHROUT('\n')
;
;        c64scr.print("while2\n")
;        while(bb < 120) {
;            c64scr.print_b(bb)
;            c64.CHROUT(',')
;            bb += 25
;            if bb < 50 continue else break
;            bb=99
;        }
;        c64.CHROUT('\n')
;
;        c64scr.print("while3\n")
;        while(uw < 50000) {
;            c64scr.print_uw(uw)
;            c64.CHROUT(',')
;            uw += 2500
;            if uw < 30000 continue else break
;            uw=9999
;        }
;        c64.CHROUT('\n')
;
;        c64scr.print("while4\n")
;        while(ww < 30000) {
;            c64scr.print_w(ww)
;            c64.CHROUT(',')
;            ww += 2500
;            if ww < 10000 continue else break
;            ww=9999
;        }
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')
;
;        ub=22
;        bb=-111
;        uw=2222
;        ww=-22222
;
;        c64scr.print("repeat1\n")
;        repeat {
;            c64scr.print_ub(ub)
;            c64.CHROUT(',')
;            ub += 22
;            ; if ub < 150 continue else break
;            ;ub=99
;        } until ub>200
;        c64.CHROUT('\n')
;
;        c64scr.print("repeat2\n")
;        repeat {
;            c64scr.print_b(bb)
;            c64.CHROUT(',')
;            bb += 22
;            ;if bb < 50 continue else break
;            ;bb=99
;        } until bb > 100
;        c64.CHROUT('\n')
;
;        c64scr.print("repeat3\n")
;        repeat {
;            c64scr.print_uw(uw)
;            c64.CHROUT(',')
;            uw += 2222
;            ;if uw < 30000 continue else break
;            ;uw=9999
;        } until uw>50000
;        c64.CHROUT('\n')
;
;        c64scr.print("repeat4\n")
;        repeat {
;            c64scr.print_w(ww)
;            c64.CHROUT(',')
;            ww += 2222
;            ;if ww < 10000 continue else break
;            ;ww=9999
;        } until ww > 20000
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')
    }
}
