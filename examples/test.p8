%import c64utils
%zeropage basicsafe

~ main {

    sub start() {
        c64scr.print("you start here! --> S\n")
        for A in 0 to 16 {
            for Y in 0 to 39 {
                if rnd() >128 c64.CHROUT(109)
                else c64.CHROUT(110)
            }
        }
        c64scr.print("      x <-- try to find your way here!")
    }

}
