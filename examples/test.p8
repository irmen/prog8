%import c64utils
%import c64flt
%option enable_floats

~ main {

    sub start() {

        byte[10]  ba = [1,2,3,4,5,6,7,8,9,-88]
        ubyte[10] uba = [1,2,3,4,5,6,7,8,9,10]

        c64scr.print_w(sum(ba))
        c64.CHROUT('\n')
        c64scr.print_uw(sum(uba))
        c64.CHROUT('\n')

        c64scr.print_ub(X)
        c64.CHROUT('\n')


;        c64scr.print_w(w2)
;        c64.CHROUT('\n')
;        c64scr.print_w(w3)
;        c64.CHROUT('\n')
;        c64scr.print_uw(uw2)
;        c64.CHROUT('\n')
    }


    ; @todo float & float -> nice error instead of crash

}
