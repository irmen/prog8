%import c64utils
%zeropage basicsafe

~ main {

    sub start() {
        ubyte aa = 100
        ubyte yy = 22
        uword uw = (aa as uword)*yy

        c64scr.print("stack (255?): ")
        c64scr.print_ub(X)
        c64.CHROUT('\n')

        aa=30
        yy=2

        c64scr.print_ub(aa+yy)
        c64scr.print("?: ")
        check(aa, yy)
        aa++
        c64scr.print_ub(aa+yy)
        c64scr.print("?: ")
        check(aa, yy)
        aa++
        c64scr.print_ub(aa+yy)
        c64scr.print("?: ")
        check(aa, yy)

        c64scr.print_uw(uw)
        c64scr.print("?: ")
        checkuw(uw)
        uw++
        c64scr.print_uw(uw)
        c64scr.print("?: ")
        checkuw(uw)
        uw++
        c64scr.print_uw(uw)
        c64scr.print("?: ")
        checkuw(uw)

        c64scr.print("stack (255?): ")
        c64scr.print_ub(X)
    }

    sub checkuw(uword uw) {
        when uw {
            12345 -> c64scr.print("12345")
            12346 -> c64scr.print("12346")
            2200 -> c64scr.print("2200")
            2202 -> c64scr.print("2202")
            12347 -> c64scr.print("12347")
            else -> c64scr.print("not in table")
        }
        c64.CHROUT('\n')
    }

    sub check(ubyte a, ubyte y) {
        when a+y {
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
            else -> {
                c64scr.print("not in table")
            }
        }
        c64.CHROUT('\n')
    }
}
