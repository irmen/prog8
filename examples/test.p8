%import c64utils
%zeropage basicsafe


~ main {

    sub start() {

        foo(1)
    }

    sub foo(ubyte param1)  {

        sub subsub() {

        }

        sub param1() {
        }
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
