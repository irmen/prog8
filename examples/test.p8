%import c64utils
%import c64flt

~ main {

    sub start()  {

        word[8] rotatedx = [11,33,55,77,22,44,66,88]
        word[8] rotatedy = [11,33,55,77,22,44,66,88]
        word[8] rotatedz = [1,3,-5,7,2,4,-6,8]

        printarray()

        c64scr.print_ub(X)
        c64.CHROUT('\n')

        for ubyte sorti in 6 to 0 step -1 {
            for ubyte i1 in 0 to sorti {
                ubyte i2=i1+1
                if(rotatedz[i2]>rotatedz[i1]) {
                    word t = rotatedx[i1]
                    rotatedx[i1] = rotatedx[i2]
                    rotatedx[i2] = t
                    t = rotatedy[i1]
                    rotatedy[i1] = rotatedy[i2]
                    rotatedy[i2] = t
                    t = rotatedz[i1]
                    rotatedz[i1] = rotatedz[i2]
                    rotatedz[i2] = t
                }
            }
        }

        c64scr.print_ub(X)
        c64.CHROUT('\n')

        printarray()


        sub printarray() {
            for word a in rotatedx {
                c64scr.print_w(a)
                c64.CHROUT(',')
            }
            c64.CHROUT('\n')
            for word a in rotatedy {
                c64scr.print_w(a)
                c64.CHROUT(',')
            }
            c64.CHROUT('\n')
            for word a in rotatedz {
                c64scr.print_w(a)
                c64.CHROUT(',')
            }
            c64.CHROUT('\n')
            c64.CHROUT('\n')
        }
    }


}

