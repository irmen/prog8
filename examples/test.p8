%zeropage basicsafe

main {

    sub start() {

        byte bb=10

        while bb<15 {
            bb++
            c64scr.print_b(bb)
            c64.CHROUT('\n')
        }

        word ww=5

        while ww > -5 {
            ww--
            c64scr.print_w(ww)
            c64.CHROUT('\n')
        }


    }
}
