%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

        ubyte[] arr1=[11,22,33]
        uword[] array=[1111 ,2222,3333]
        float[] farr = [1.111, 2.222, 3.333]

        ubyte i = 1

        c64scr.print_ub(arr1[1])
        c64.CHROUT('\n')
        arr1 [i] ++
        c64scr.print_ub(arr1[1])
        c64.CHROUT('\n')

        c64scr.print_uw(array[1])
        c64.CHROUT('\n')
        array[i] ++
        c64scr.print_uw(array[1])
        c64.CHROUT('\n')

        c64flt.print_f(farr[1])
        c64.CHROUT('\n')
        farr[i] ++
        c64flt.print_f(farr[1])
        c64.CHROUT('\n')
    }
}

