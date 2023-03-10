%import textio
%option no_sysinit
%zeropage basicsafe
%import floats

main {
    sub start() {
        uword[] array= [1111,2222,3333]
        ubyte idx=1
        txt.print_uw(array[idx])
        txt.nl()
        array[idx]+=1234
        txt.print_uw(array[idx])
        txt.nl()

        ubyte[] array2= [11,22,33]
        idx=1
        txt.print_ub(array2[idx])
        txt.nl()
        array2[idx]+=34
        txt.print_ub(array2[idx])
        txt.nl()

        float[] array3= [11.11,22.22,33.33]
        idx=1
        floats.print_f(array3[idx])
        txt.nl()
        array3[idx]+=55.66
        floats.print_f(array3[idx])
        txt.nl()
    }
}
