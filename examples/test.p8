%import c64utils
;%import c64flt
;%option enable_floats
%zeropage dontuse

main {

    sub subje(ubyte arg1) {
        ubyte yy=33
        ubyte zz
        ubyte[5] array1 = [1,2,3,4,5]

        c64scr.print_ub(arg1)
        c64.CHROUT(',')
        c64scr.print_ub(yy)
        c64.CHROUT(',')
        c64scr.print_ub(zz)
        c64.CHROUT('\n')
        yy++
        A=zz
        Y=array1[2]
    }

    sub start() {
        ubyte zz2
        A=zz2
        subje(111)
        subje(112)
        subje(113)
        subje(114)
        return

;        ubyte ub1
;        ubyte ub2 = 99
;        uword uw1
;        uword uw2 = 9999
;        ubyte[5] array1
;        ubyte[5] array2 = [22,33,44,55,66]
;
;        c64scr.print_ub(ub1)
;        c64.CHROUT(',')
;        c64scr.print_ub(ub2)
;        c64.CHROUT(',')
;        c64scr.print_uw(uw1)
;        c64.CHROUT(',')
;        c64scr.print_uw(uw2)
;        c64.CHROUT(',')
;        c64scr.print_ub(array1[0])
;        c64.CHROUT(',')
;        c64scr.print_ub(array2[0])
;        c64.CHROUT('\n')
;
;        ub1++
;        ub2++
;        uw1++
;        uw2++
;        array1[0]++
;        array2[0]++
;
;        c64scr.print_ub(ub1)
;        c64.CHROUT(',')
;        c64scr.print_ub(ub2)
;        c64.CHROUT(',')
;        c64scr.print_uw(uw1)
;        c64.CHROUT(',')
;        c64scr.print_uw(uw2)
;        c64.CHROUT(',')
;        c64scr.print_ub(array1[0])
;        c64.CHROUT(',')
;        c64scr.print_ub(array2[0])
;        c64.CHROUT('\n')
    }
}
