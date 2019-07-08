%import c64utils
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {
        A=10
        Y=22

        when 4+A+Y {
            10 -> {
                c64scr.print("ten")
            }
            5 -> c64scr.print("five")
            30 -> c64scr.print("thirty")
            99 -> c64scr.print("nn")
            55 -> {
                ; should be optimized away
            }
            56 -> {
                ; should be optimized away
            }
            57 -> {
                ; should be optimized away
            }
            else -> {
                c64scr.print("!??!\n")
                c64scr.print("!??!!??!\n")
                c64scr.print("!??!!??!!?!\n")
            }
        }
    }
}
