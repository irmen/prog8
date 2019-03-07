%import c64utils
%zeropage basicsafe

~ main {

    ; @todo see problem in looplabelproblem.p8

    ; @todo when a for loop is executed multiple times, is the loop var correct?  (via goto start)

    sub start() {

waitjoy:
        ubyte key=c64.GETIN()
        if_z goto waitjoy
        c64scr.print_ub(key)
        c64.CHROUT('\n')
        goto waitjoy
    }
}
