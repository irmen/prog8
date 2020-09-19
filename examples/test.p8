%import c64lib
%import c64graphics
%import c64textio
%import c64flt
;%option enable_floats
%zeropage basicsafe


main {

    sub start()  {

        ubyte i1=0
        ubyte i2=1

        byte b1 = 11
        byte b2 = 22
        word w1 = 1111
        word w2 = 2222
        float f1 = 1.111
        float f2 = 2.222

        byte[] barr = [1,2]
        word[] warr = [1111,2222]
        float[] farr= [1.111, 2.222]

        @($c000) = 11
        @($c001) = 22

        swap(b1,b2)
        swap(w1,w2)
        swap(f1,f2)
        swap(@($c000), @($c001))
        swap(barr[i1], barr[i2])
        swap(warr[i1], warr[i2])
        swap(farr[i1], farr[i2])

        txt.print_b(b1)
        c64.CHROUT(',')
        txt.print_b(b2)
        c64.CHROUT('\n')

        txt.print_w(w1)
        c64.CHROUT(',')
        txt.print_w(w2)
        c64.CHROUT('\n')

        c64flt.print_f(f1)
        c64.CHROUT(',')
        c64flt.print_f(f2)
        c64.CHROUT('\n')

        txt.print_b(barr[0])
        c64.CHROUT(',')
        txt.print_b(barr[1])
        c64.CHROUT('\n')

        txt.print_w(warr[0])
        c64.CHROUT(',')
        txt.print_w(warr[1])
        c64.CHROUT('\n')

        c64flt.print_f(farr[0])
        c64.CHROUT(',')
        c64flt.print_f(farr[1])
        c64.CHROUT('\n')

        txt.print_ub(@($c000))
        c64.CHROUT(',')
        txt.print_ub(@($c001))
        c64.CHROUT('\n')
    }
}
