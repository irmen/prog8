%zeropage basicsafe

~ main {

    sub start() {

        ubyte[5] array2 =  20 to 100        ; @todo fix this initializer value
        word[5] array3 = 3000 to 3100       ; @todo fix this initializer value

        c64scr.print_uw(len(array2))
        c64.CHROUT('\n')
        c64scr.print_uw(len(array3))
        c64.CHROUT('\n')
        c64scr.print_uw(len(array4))
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        c64scr.print_ub(array2[0])
        c64.CHROUT(',')
        c64scr.print_ub(array2[1])
        c64.CHROUT(',')
        c64scr.print_ub(array2[2])
        c64.CHROUT('\n')
        c64scr.print_w(array3[0])
        c64.CHROUT(',')
        c64scr.print_w(array3[1])
        c64.CHROUT(',')
        c64scr.print_w(array3[2])
        c64.CHROUT('\n')
    }
}
