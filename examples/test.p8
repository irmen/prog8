%zeropage basicsafe
%option enable_floats
%import c64flt

~ main {

    float[]  fa = [1.1,2.2,3.3]
    ubyte[] uba = [10,2,3,4]
    byte[] ba = [-10,2,3,4]
    uword[] uwa = [100,20,30,40]
    word[] wa = [-100,20,30,40]

    sub start() {

        float a
        a=avg([1,2,3,4])
        c64flt.print_f(a)
        c64.CHROUT('\n')
        a=avg([100,200,300,400])
        c64flt.print_f(a)
        c64.CHROUT('\n')
        a=avg([1.1,2.2,3.3,4.4])
        c64flt.print_f(a)
        c64.CHROUT('\n')


    }
}
