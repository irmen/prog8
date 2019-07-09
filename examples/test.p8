%import c64utils
%zeropage basicsafe

~ main {

    sub start() {
        A=100
        Y=22
        uword uw = (A as uword)*Y

        c64scr.print("stack (255?): ")
        c64scr.print_ub(X)
        c64.CHROUT('\n')

        c64scr.print_uw(uw)
        c64scr.print("?: ")
        when uw {
            12345 -> c64scr.print("12345")
            12346 -> c64scr.print("12346")
            2200 -> c64scr.print("2200")
            12347 -> c64scr.print("12347")
            else -> c64scr.print("else")
        }
        c64.CHROUT('\n')

        A=30
        Y=2

        c64scr.print_ub(A+Y)
        c64scr.print("?: ")
        when A+Y {
            10 -> {
                c64scr.print("ten")
            }
            5 -> c64scr.print("five")
            30 -> c64scr.print("thirty")
            31 -> c64scr.print("thirty1")
            32 -> c64scr.print("thirty2")
            33 -> c64scr.print("thirty3")
            99 -> c64scr.print("nn")
            55 -> {
                ; should be optimized away
            }
            56 -> {
                ; should be optimized away
            }
            57243 -> {
                ; should be optimized away
            }
            else -> {
                c64scr.print("!??!\n")
            }
        }
        c64.CHROUT('\n')

        c64scr.print("stack (255?): ")
        c64scr.print_ub(X)
    }
}
