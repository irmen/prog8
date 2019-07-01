%import c64utils
%zeropage basicsafe


~ main {

    sub start() {
        c64scr.setcc(0,0,160,0)
        c64scr.setcc(0,1,160,1)
        c64scr.setcc(0,2,160,2)
        c64scr.setcc(0,3,160,3)
        c64scr.setcc(0,4,160,4)
        c64scr.setcc(0,5,160,5)
        c64scr.setcc(0,6,160,6)
        c64scr.setcc(0,7,160,7)
        c64scr.setcc(0,8,160,8)
        c64scr.setcc(0,9,160,9)
        c64scr.setcc(0,10,160,10)
        c64scr.setcc(0,11,160,11)
        c64scr.setcc(0,12,160,12)
        c64scr.setcc(0,13,160,13)
        c64scr.setcc(0,14,160,14)
        c64scr.setcc(0,15,160,15)

        c64scr.setcc(1,0,160,0)
        c64scr.setcc(1,1,160,1)
        c64scr.setcc(1,2,160,2)
        c64scr.setcc(1,3,160,3)
        c64scr.setcc(1,4,160,4)
        c64scr.setcc(1,5,160,5)
        c64scr.setcc(1,6,160,6)
        c64scr.setcc(1,7,160,7)
        c64scr.setcc(1,8,160,8)
        c64scr.setcc(1,9,160,9)
        c64scr.setcc(1,10,160,10)
        c64scr.setcc(1,11,160,11)
        c64scr.setcc(1,12,160,12)
        c64scr.setcc(1,13,160,13)
        c64scr.setcc(1,14,160,14)
        c64scr.setcc(1,15,160,15)

        c64scr.setcc(2,0,160,0)
        c64scr.setcc(2,1,160,1)
        c64scr.setcc(2,2,160,2)
        c64scr.setcc(2,3,160,3)
        c64scr.setcc(2,4,160,4)
        c64scr.setcc(2,5,160,5)
        c64scr.setcc(2,6,160,6)
        c64scr.setcc(2,7,160,7)
        c64scr.setcc(2,8,160,8)
        c64scr.setcc(2,9,160,9)
        c64scr.setcc(2,10,160,10)
        c64scr.setcc(2,11,160,11)
        c64scr.setcc(2,12,160,12)
        c64scr.setcc(2,13,160,13)
        c64scr.setcc(2,14,160,14)
        c64scr.setcc(2,15,160,15)

        _x:
            goto _x

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
}
