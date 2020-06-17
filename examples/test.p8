%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe



main {

    sub start() {
        float[] fa=[1.1111,2.2222,3.3333,4.4444]
        ubyte[] uba = [1,2,3,4]
        word[] uwa = [1111,2222,3333,4444]
        ubyte ii = 1
        ubyte jj = 3

        float f1 = 1.123456
        float f2 = 2.223344

        swap(f1, f2)

        swap(fa[0], fa[1])
        swap(uba[0], uba[1])
        swap(uwa[0], uwa[1])

        ubyte i1
        ubyte i2
        swap(fa[i1], fa[i2])
        swap(uba[i1], uba[i2])
        swap(uwa[i1], uwa[i2])

        c64flt.print_f(f1)
        c64.CHROUT('\n')
        c64flt.print_f(f2)
        c64.CHROUT('\n')

        swap(f1,f2)
        c64flt.print_f(f1)
        c64.CHROUT('\n')
        c64flt.print_f(f2)
        c64.CHROUT('\n')
    }

}


