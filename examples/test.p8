%import textio
%zeropage basicsafe

main {

    sub start() {
        uword xx1
        uword xx2
        uword xx3
        uword total

        c64.SETTIM(0,0,0)
        repeat 600 {
            repeat 600 {
                xx1++
                xx2++
                xx3++
            }
        }
        uword time = c64.RDTIM16()
        txt.print_uw(time)
    }
}
