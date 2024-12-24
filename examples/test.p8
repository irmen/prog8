%import floats
%import textio
%zeropage basicsafe
%option no_sysinit


main {
    sub start() {
        if cx16.r0L & $80 != 0
            return
        if cx16.r1L & $80 == 0
            return
        if cx16.r2L & $40 != 0
            return
        if cx16.r3L & $40 == 0
            return

        cx16.r0L = 0
        test()
        txt.nl()
        cx16.r0L = 255
        test()
        txt.nl()

        sub test() {
            if cx16.r0L & $80 != 0
                txt.chrout('1')
            if cx16.r0L & $80 == 0
                txt.chrout('2')
            if cx16.r0L & $40 != 0
                txt.chrout('3')
            if cx16.r0L & $40 == 0
                txt.chrout('4')
            if cx16.r0L & $20 != 0
                txt.chrout('5')
            if cx16.r0L & $20 == 0
                txt.chrout('6')
        }
    }
}
