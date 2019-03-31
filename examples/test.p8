%zeropage basicsafe
%option enable_floats

%import c64flt

~ main {

    sub start() {
        ubyte ub=2
        ubyte ub2=7
        uword uw=2
        uword uw2=5
        float fl=2.3
        float fl2=20

;        ub = ub ** 7
;        c64scr.print_ub(ub)
;        c64.CHROUT('\n')
;        uw = uw ** 5
;        c64scr.print_uw(uw)
;        c64.CHROUT('\n')
        fl = fl ** 20.0
        c64flt.print_f(fl)
        c64.CHROUT('\n')

;        ub=3            ; @todo no instruction?
;        ub **=7         ; @todo no instruction?
;        c64scr.print_ub(ub)
;        c64.CHROUT('\n')
;        uw = 9      ; @todo no instruction?
;        uw **=5         ; @todo no instruction?
;        c64scr.print_uw(uw)
;        c64.CHROUT('\n')
        fl = 2.3          ; @todo no instruction?
        fl **=20.0         ; @todo no instruction?
        c64flt.print_f(fl)
        c64.CHROUT('\n')


;        ub=3
;        ub **= 7
;        c64scr.print_ub(ub)
;        c64.CHROUT('\n')
;        uw = 9
;        uw **= 5
;        c64scr.print_uw(uw)
;        c64.CHROUT('\n')
        fl = 2.3
        fl **= 20.0
        c64flt.print_f(fl)
        c64.CHROUT('\n')

;        ub=3
;        ub **= ub2
;        c64scr.print_ub(ub)
;        c64.CHROUT('\n')
;        uw = 9
;        uw **= uw2
;        c64scr.print_uw(uw)
;        c64.CHROUT('\n')
        fl = 2.3
        fl **= fl2
        c64flt.print_f(fl)
        c64.CHROUT('\n')

    }

}
