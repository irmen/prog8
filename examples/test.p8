%import c64utils
;%import c64flt
;%option enable_floats
%zeropage dontuse

main {

    sub subje() {
        ubyte a1
        ubyte a2
        ubyte zz
        for a1 in 1 to 3 {
            ubyte qq = 100                  ; TODO should be inited here, and not in the subroutine.
            for a2 in 1 to 3 {
                ubyte zz = 100              ; TODO should be inited here, and not in the subroutine.
                c64scr.print_ub(a1)
                c64.CHROUT(',')
                c64scr.print_ub(a2)
                c64.CHROUT(',')
                c64scr.print_ub(qq)         ; TODO qq shoud be 100..103 repeated three times...
                c64.CHROUT(',')
                c64scr.print_ub(zz)         ; TODO zz should always be 100...
                c64.CHROUT('\n')
                qq++
                zz++
            }
        }
    }

    sub start() {
        subje()
        c64.CHROUT('\n')
        subje()
        c64.CHROUT('\n')
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
