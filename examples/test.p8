%import c64utils
%zeropage basicsafe

~ main {

    ; @todo see problem in looplabelproblem.p8

    sub start() {
        uword z2 = sqrt16(50000)

        c64scr.print_ub(sqrt16(0))
        c64.CHROUT('\n')
        c64scr.print_ub(sqrt16(200))
        c64.CHROUT('\n')
        c64scr.print_ub(sqrt16(20000))
        c64.CHROUT('\n')
        c64scr.print_ub(sqrt16(63333))
        c64.CHROUT('\n')
        c64scr.print_ub(sqrt16(65535))
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        z2=0
        c64scr.print_ub(sqrt16(z2))
        c64.CHROUT('\n')
        z2=200
        c64scr.print_ub(sqrt16(z2))
        c64.CHROUT('\n')
        z2=20000
        c64scr.print_ub(sqrt16(z2))
        c64.CHROUT('\n')
        z2=63333
        c64scr.print_ub(sqrt16(z2))
        c64.CHROUT('\n')
        z2=65535
        c64scr.print_ub(sqrt16(z2))
        c64.CHROUT('\n')
        A=99
    }

}
