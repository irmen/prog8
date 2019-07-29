%import c64flt
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {
        uword target = 4444
;        @($d020) = A
;        @($d020) = A+4
;        @(target) = A+4
;        @(target+4) = A+4

        whenubyte(20)
        whenubyte(111)
        whenbyte(-10)
        whenbyte(-111)
        whenbyte(0)

        whenuword(500)
        whenuword(44)
        whenword(-3000)
        whenword(-44)
        whenword(0)

        sub whenbyte(byte value) {
            when value {
                -4 -> c64scr.print("minusfour")
                -5 -> c64scr.print("minusfive")
                -10,-20,-30 -> {
                    c64scr.print("minusten or twenty or thirty")
                }
                -99 -> c64scr.print("minusninetynine")
                else -> c64scr.print("don't know")
            }
            c64.CHROUT('\n')
        }

        sub whenubyte(ubyte value) {
            when value {
                4 -> c64scr.print("four")
                5 -> c64scr.print("five")
                10,20,30 -> {
                    c64scr.print("ten or twenty or thirty")
                }
                99 -> c64scr.print("ninetynine")
                else -> c64scr.print("don't know")
            }
            c64.CHROUT('\n')
        }

        sub whenuword(uword value) {
            when value {
                400 -> c64scr.print("four100")
                500 -> c64scr.print("five100")
                1000,2000,3000 -> {
                    c64scr.print("thousand 2thousand or 3thousand")
                }
                9999 -> c64scr.print("ninetynine99")
                else -> c64scr.print("don't know")
            }
            c64.CHROUT('\n')
        }

        sub whenword(word value) {
            when value {
                -400 -> c64scr.print("minusfour100")
                -500 -> c64scr.print("minusfive100")
                -1000,-2000,-3000 -> {
                    c64scr.print("minusthousand 2thousand or 3thousand")
                }
                -9999 -> c64scr.print("minusninetynine99")
                else -> c64scr.print("don't know")
            }
            c64.CHROUT('\n')
        }
    }
}
