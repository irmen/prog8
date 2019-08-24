%zeropage basicsafe

; TODO implement asm generation for all loops here

main {

    sub start() {
        byte bvar
        ubyte var2

        ubyte[] barr = [22,33,44,55,66]
        word[] warr = [-111,222,-333,444]

;        for A in "hello" {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for A in [1,3,5,99] {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for A in 10 to 20 {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for A in 20 to 10 step -1 {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for A in 10 to 21 step 3 {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for A in 24 to 10 step -3 {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for A in barr {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')
;
;        ubyte cc
;        for cc in "hello" {
;            c64scr.print_ub(cc)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for cc in [1,3,5,99] {
;            c64scr.print_ub(cc)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for cc in 10 to 20 {
;            c64scr.print_ub(cc)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for cc in 20 to 10 step -1 {
;            c64scr.print_ub(cc)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for cc in 10 to 21 step 3 {
;            c64scr.print_ub(cc)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for cc in 24 to 10 step -3 {
;            c64scr.print_ub(cc)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for cc in barr {
;            c64scr.print_ub(cc)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')
;
;        uword uw
;        for uw in [1111, 2222, 3333] {
;            c64scr.print_uw(uw)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for ww in warr {
;            c64scr.print_w(ww)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for uw in 1111 to 1117  {
;            c64scr.print_uw(uw)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for uw in 2000 to 1995 step -1 {
;            c64scr.print_uw(uw)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for uw in 1111 to 50000 step 4444 {
;            c64scr.print_uw(uw)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')

        word endw1 = 999
        uword enduw1 = 2600
        word endw2 = -999
        byte endb1 = 100
        byte endb2 = -100
        byte bb
        word ww
        uword uw
        ubyte ub
        ubyte ubb

;        for A in 95 to endb1 step 1 {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for bb in 95 to endb1 step 1 {
;            c64scr.print_b(bb)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for A in endb1 to 95 step -1 {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for bb in -95 to endb2 step -1 {
;            c64scr.print_b(bb)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        c64.CHROUT('\n')
;
;        for ww in 995 to endw1 step 1 {
;            c64scr.print_w(ww)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for ww in -995 to endw2 step -1 {
;            c64scr.print_w(ww)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')
;
;        for A in 90 to endb1 step 3 {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for bb in 90 to endb1 step 3 {
;            c64scr.print_b(bb)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        ubb = 10
;        for A in 20 to ubb step -3 {
;            c64scr.print_ub(A)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for bb in -90 to endb2 step -3 {
;            c64scr.print_b(bb)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')
;
;        for uw in 999 to enduw1 step 500 {
;            c64scr.print_uw(uw)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for uw in enduw1 to 999 step -500 {
;            c64scr.print_uw(uw)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')

        for ww in -999 to endw1 step 500 {          ; TODO fix loop asm
            c64scr.print_w(ww)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

;        for ww in 999 to endw2 step -500 {
;            c64scr.print_w(ww)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')
;
;        enduw1 = 2000
;        for uw in 500 to enduw1 step 500 {
;            c64scr.print_uw(uw)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;
;        for uw in enduw1 to 500 step -500 {
;            c64scr.print_uw(uw)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')

        endw1 = 1000
        for ww in -1000 to endw1 step 500 {         ; TODO fix loop asm
            c64scr.print_w(ww)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        for ww in 3000 to endw1 step -500 {
            c64scr.print_w(ww)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
        c64.CHROUT('\n')

        ubb=X
        c64scr.print_ub(ubb)
    }
}
