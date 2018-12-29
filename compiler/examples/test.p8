%import c64utils
%option enable_floats

~ main {

    ; @todo fix floating point number corruption

    byte[10] xbyte   = [1,2,3,4,5,6,7,8,9,-110]
    byte[10] ybyte   = [11,22,33,44,55,-66,-77,-88,-99,-110 ]
    byte[10] zbyte   = [1,2,3,4,5,6,7,8,9,-99]

    ubyte[10] xubyte = [1,2,3,4,5,6,77,88,99,111]
    ubyte[10] yubyte = [11,22,33,44,55,66,77,88,99,111]
    ubyte[10] zubyte = [1,2,3,4,5,66,7,88,99,111]

    word[10] xword   = [1,2,3,4,5,6,7,8,9,-1111]
    word[10] yword   = [11,22,33,44,55,66,77,88,99,-1111 ]
    word[10] zword   = [1,2,3,4,5,6,7,8,9,-9999]

    uword[10] xuword = [1,2,3,4,5,6,7,88,99,1111]
    uword[10] yuword = [11,22,33,44,55,66,77,88,99,1111 ]
    uword[10] zuword = [1,2,3,4,5,6,77,88,99,9999]

    float[10] xcoor  = [1,2,3,4,5,6,7,8,9.9,11.11 ]
    float[10] ycoor  = [11,22,33,44,55,66,77,88,99.9,111.11 ]
    float[10] zcoor  = [111,222,333,444,555,666,777,888,999.9,1001.11 ]

    sub start()  {
;        c64scr.print("\nxword:\n")
;        for word w1 in xword {
;            c64scr.print_w(w1)
;            c64.CHROUT(',')
;        }

;        c64scr.print("\nxcoor:\n")
;        for float f1 in xcoor {
;            c64flt.print_f(f1)
;            c64.CHROUT(',')
;        }

;        c64.CHROUT('\n')
;        c64scr.print("ycoor:\n")
;        for float f2 in ycoor {
;            c64flt.print_f(f2)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')
;        c64scr.print("zcoor:\n")
;        for float f3 in zcoor {
;            c64flt.print_f(f3)
;            c64.CHROUT(',')
;        }
;        c64.CHROUT('\n')

        c64.CHROUT('X')
        c64scr.print_ub(X)
        c64.CHROUT('\n')
        float avgbx = avg(xbyte)
        float avgby = avg(ybyte)
        float avgbz = avg(zbyte)
        float avgubx = avg(xubyte)
        float avguby = avg(yubyte)
        float avgubz = avg(zubyte)
        float avgwx = avg(xword)
        float avgwy = avg(yword)
        float avgwz = avg(zword)
        float avguwx = avg(xuword)
        float avguwy = avg(yuword)
        float avguwz = avg(zuword)
        float avgfx = avg(xcoor)
        float avgfy = avg(ycoor)
        float avgfz = avg(zcoor)
        c64.CHROUT('X')
        c64scr.print_ub(X)
        c64.CHROUT('\n')

        c64scr.print("avgbx=")
        c64flt.print_f(avgbx)
        c64.CHROUT('\n')
        c64scr.print("avgby=")
        c64flt.print_f(avgby)
        c64.CHROUT('\n')
        c64scr.print("avgbz=")
        c64flt.print_f(avgbz)
        c64.CHROUT('\n')

        c64scr.print("avgubx=")
        c64flt.print_f(avgubx)
        c64.CHROUT('\n')
        c64scr.print("avguby=")
        c64flt.print_f(avguby)
        c64.CHROUT('\n')
        c64scr.print("avgubz=")
        c64flt.print_f(avgubz)
        c64.CHROUT('\n')

        c64scr.print("avgwx=")
        c64flt.print_f(avgwx)
        c64.CHROUT('\n')
        c64scr.print("avgwy=")
        c64flt.print_f(avgwy)
        c64.CHROUT('\n')
        c64scr.print("avgwz=")
        c64flt.print_f(avgwz)
        c64.CHROUT('\n')

        c64scr.print("avguwx=")
        c64flt.print_f(avguwx)
        c64.CHROUT('\n')
        c64scr.print("avguwy=")
        c64flt.print_f(avguwy)
        c64.CHROUT('\n')
        c64scr.print("avguwz=")
        c64flt.print_f(avguwz)
        c64.CHROUT('\n')

        c64scr.print("avgfx=")
        c64flt.print_f(avgfx)
        c64.CHROUT('\n')
        c64scr.print("avgfy=")
        c64flt.print_f(avgfy)
        c64.CHROUT('\n')
        c64scr.print("avgfz=")
        c64flt.print_f(avgfz)
        c64.CHROUT('\n')

        return


        c64scr.print("\nseparated i=2\n")
        c64scr.print(" x[2]=")
        c64flt.print_f(xcoor[2])        ; @todo wrong value printed

        c64scr.print(" y[2]=")
        c64flt.print_f(ycoor[2])
        c64scr.print(" z[2]=")
        c64flt.print_f(zcoor[2])
        c64scr.print("\nseparated i=3\n")
        c64scr.print(" x[3]=")
        c64flt.print_f(xcoor[3])
        c64scr.print(" y[3]=")
       c64flt.print_f(ycoor[3])
        c64scr.print(" z[3]=")
        c64flt.print_f(zcoor[3])
    }
}
