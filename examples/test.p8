%import c64utils
;%import c64flt
;%option enable_floats
%zeropage basicsafe

main {

    sub start() {
        ubyte ub1
        ubyte ub2 = 99
        uword uw1
        uword uw2 = 9999
        ubyte[5] array1
        ubyte[5] array2 = [22,33,44,55,66]

        c64scr.print_ub(ub1)
        c64.CHROUT(',')
        c64scr.print_ub(ub2)
        c64.CHROUT(',')
        c64scr.print_uw(uw1)
        c64.CHROUT(',')
        c64scr.print_uw(uw2)
        c64.CHROUT(',')
        c64scr.print_ub(array1[0])
        c64.CHROUT(',')
        c64scr.print_ub(array2[0])
        c64.CHROUT('\n')

        ub1++
        ub2++
        uw1++
        uw2++
        array1[0]++
        array2[0]++

        c64scr.print_ub(ub1)
        c64.CHROUT(',')
        c64scr.print_ub(ub2)
        c64.CHROUT(',')
        c64scr.print_uw(uw1)
        c64.CHROUT(',')
        c64scr.print_uw(uw2)
        c64.CHROUT(',')
        c64scr.print_ub(array1[0])
        c64.CHROUT(',')
        c64scr.print_ub(array2[0])
        c64.CHROUT('\n')
    }
}
