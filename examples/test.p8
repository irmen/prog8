%zeropage basicsafe

~ main {

    sub start() {

        uword multiple

        while multiple < 5 {
            multiple += 2
            c64scr.print_uw(multiple)       ; TODO
            c64.CHROUT('\n')        ; TODO
        }

        c64scr.print("done!\n")

    }


}
