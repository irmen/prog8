%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {

;        uword[] array=[$1111 ,$2222,$3333]
;        uword uw = $ee33
;        ubyte i = 2
;        array[1] = array[1] as ubyte
;        array[i] = array[i] as ubyte
;        c64scr.print_uwhex(array[0], 1)
;        c64.CHROUT(',')
;        c64scr.print_uwhex(array[1], 1)
;        c64.CHROUT(',')
;        c64scr.print_uwhex(array[2], 1)
;        c64.CHROUT('\n')

        float[] farr = [1.111, 2.222, 3.333]
        c64flt.print_f(farr[1])
        c64.CHROUT('\n')
        float ff
        float ff2 = 0
        ff = ff2 + farr[1] + 99
        c64flt.print_f(ff)
        c64.CHROUT('\n')

    }
}

