%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        word @shared w = -20
        word[] warr = [-1111, -2222, -3333]
        word[] @split swarr = [-1111, -2222, -3333]

        cx16.r0L=1
        if warr[cx16.r0L] > 0
            txt.print("yep1")

        if warr[cx16.r0L] <= 0
            txt.print("yep2")

        if swarr[cx16.r0L] > 0
            txt.print("yep3")

        if swarr[cx16.r0L] <= 0
            txt.print("yep4")
    }
}

