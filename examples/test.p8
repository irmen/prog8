%import c64utils
%zeropage basicsafe
%import c64flt

~ main {

    uword ww = 33               ; @todo error?  or should it be 33?
    uword w2 = foo()            ; @todo same issue


    sub start() {

        uword xw = 33               ; @todo error?  or should it be 33?
        uword x2 = foo()            ; @todo same issue

        c64scr.print_uw(ww)
        c64.CHROUT('\n')
        c64scr.print_uw(w2)
        c64.CHROUT('\n')
        c64scr.print_uw(xw)
        c64.CHROUT('\n')
        c64scr.print_uw(x2)
        c64.CHROUT('\n')

        @($d020) = 34

    }

    sub foo() -> uword {
        A=4
        return rndw()+A
    }

;        for ubyte y in 0 to 3 {
;            for ubyte x in 0 to 10 {
;                ubyte product = x*y
;                c64scr.setcc(x, y, 160, product)
;            }
;        }
;        c64.CHROUT('\n')
;        c64.CHROUT('\n')
;
;        for ubyte y in 12 to 15 {
;            for ubyte x in 0 to 10 {
;                ubyte sumv = x+y
;                c64scr.setcc(x, y, 160, sumv)
;            }
;        }

        ;ubyte bb = len(xcoor)

        ; storage for rotated coordinates
;        ubyte[len(xcoor)] xx = 2
;        float[len(xcoor)] rotatedx=0.0
;
;        ubyte[4] x = 23
;        float[4] yy = 4.4

;        c64flt.print_f(xcoor[1])
;        c64.CHROUT(',')
;        c64flt.print_f(xcoor[2])
;        c64.CHROUT('\n')
;        swap(xcoor[1], xcoor[2])
;        c64flt.print_f(xcoor[1])
;        c64.CHROUT(',')
;        c64flt.print_f(xcoor[2])
;        c64.CHROUT('\n')

}
