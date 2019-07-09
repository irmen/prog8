%import c64utils
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {
        A=10
        Y=22
        uword uw = A*Y

        str teststring = "hello"
        c64scr.print(&teststring)

        when uw {
            12345 -> {
                A=44
            }
            12346 -> {
                A=44
            }
            12347 -> {
                A=44
            }
            else -> {
                A=0
            }
        }

        when 4+A+Y {
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
                c64scr.print("!??!!??!\n")
                c64scr.print("!??!!??!!?!\n")
            }
        }
    }
}
