%import c64utils
%option enable_floats

~ main {

    float[10] xcoor  = [1,2,3,4,5,6,7,8,9.9,11.11 ]
    float[10] ycoor  = [11,22,33,44,55,66,77,88,99.9,111.11 ]
    float[10] zcoor  = [111,222,333,444,555,666,777,888,999.9,1001.11 ]

    sub start()  {
        c64scr.print("\nxcoor:\n")
        for float f1 in xcoor {
            c64flt.print_f(f1)
            c64.CHROUT(',')
        }

        c64.CHROUT('\n')
        c64scr.print("ycoor:\n")
        for float f2 in ycoor {
            c64flt.print_f(f2)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')
        c64scr.print("zcoor:\n")
        for float f3 in zcoor {
            c64flt.print_f(f3)
            c64.CHROUT(',')
        }
        c64.CHROUT('\n')

        c64.CHROUT('X')
        c64scr.print_ub(X)
        c64.CHROUT('\n')
        float avgfx = avg(xcoor)
        float avgfy = avg(ycoor)
        float avgfz = avg(zcoor)
        c64.CHROUT('X')
        c64scr.print_ub(X)
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

separated2:
        c64scr.print("\nseparated i=2\n")
        c64scr.print(" x[2]=")
        ubyte ii=2
        c64flt.print_f(xcoor[ii])

        c64scr.print(" y[2]=")
        c64flt.print_f(ycoor[ii])
        c64scr.print(" z[2]=")
        c64flt.print_f(zcoor[ii])

separated3:
        c64scr.print("\nseparated i=3\n")
        ii=3
        c64scr.print(" x[3]=")
        c64flt.print_f(xcoor[ii])
        c64scr.print(" y[3]=")
       c64flt.print_f(ycoor[ii])
        c64scr.print(" z[3]=")
        c64flt.print_f(zcoor[ii])


        c64.CHROUT('\n')
        c64.CHROUT('X')
        c64scr.print_ub(X)
        c64.CHROUT('\n')
        avgfx = avg(xcoor)
        avgfy = avg(ycoor)
        avgfz = avg(zcoor)
        c64.CHROUT('X')
        c64scr.print_ub(X)
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

    }
}
