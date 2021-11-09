%import textio
%import floats
%zeropage basicsafe

main {

    sub start() {
        ubyte unused                ; TODO FIX : why is this not removed as an unused variable?
        ubyte @shared unused2

        ubyte bb
        uword ww
        ww = not bb or not ww       ; TODO WHY DOES THIS USE STACK EVAL
    }


;    if not iteration_in_progress or not num_bytes
;        return

;    word xx=0
;    word[] xarr = [1,2,3]
;    ubyte ai
;
;    if not @($c000) {
;        txt.print("xx is zero\n")
;    }
;
;    while not xarr[ai] {
;        xx ++
;    }
;
;    do {
;        xx--
;    } until not xarr[ai]
;
;    if not xarr[ai] {
;        txt.print("xx is zero\n")
;    }

;    ubyte yy=$30
;    ubyte zz=9
;    sys.memset(xx+200, yy*2, ~yy)
;
;
;    if yy & %10000 {
;        yy++
;    }
;
;    @($c030) = 10
;    @(~xx) *= 2
;    txt.print_ub(@($c030))
;
;    float f1 = 1111.11
;    float f2 = 2222.22
;    float[] fa = [2222.22, 3333.33]
;
;    swap(f1, fa[1])
;    floats.print_f(f1)
;    txt.nl()
;    floats.print_f(fa[1])

}
