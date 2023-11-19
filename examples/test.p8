%import textio
%zeropage basicsafe

main {
    sub start() {

        txt.print("for:\n")
        for cx16.r0L in 10 to 20 {
            txt.print_ub(cx16.r0L)
            txt.print(" before...")
            if cx16.r0L > 15
                break
            if cx16.r0L ==14
                continue
            txt.print("after\n")
        }
        txt.nl()

        txt.print("repeat:\n")
        cx16.r0L=10
        repeat 10 {
            cx16.r0L++
            txt.print_ub(cx16.r0L)
            txt.print(" before...")
            if cx16.r0L > 15
                break
            if cx16.r0L ==14
                continue
            txt.print("after\n")
        }
        txt.nl()

        txt.print("while:\n")
        cx16.r0L=10
        while cx16.r0L<20 {
            cx16.r0L++
            txt.print_ub(cx16.r0L)
            txt.print(" before...")
            if cx16.r0L > 15
                break
            if cx16.r0L ==14
                continue
            txt.print("after\n")
        }
        txt.nl()

        txt.print("until:\n")
        cx16.r0L=10
        do {
            cx16.r0L++
            txt.print_ub(cx16.r0L)
            txt.print(" before...")
            if cx16.r0L > 15
                break
            if cx16.r0L ==14
                continue
            txt.print("after\n")
        } until cx16.r0L>20
        txt.nl()

    }
}
