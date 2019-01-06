%import c64utils

~ main {

    ubyte ub1
    ubyte ub2
    ubyte ub3
    ubyte ub4
    byte b1 = 120
    byte b2 = -13
    byte b3
    byte b4

    uword uw1 = 52000
    uword uw2 = 1333
    uword uw3
    uword uw4
    word w1 = 22000
    word w2 = 1333
    word w3
    word w4

    sub start()  {
        w1 = 120
        w2 = -13
        w3 = w1 // w2
        c64scr.print_w(w3)
        c64.CHROUT(':')
        w1 = -120
        w2 = 13
        w3 = w1 // w2
        c64scr.print_w(w3)
        c64.CHROUT(':')
        w2 = 13
        w3 = w1 // w2
        c64scr.print_w(w3)
        c64.CHROUT(':')

        w3 = w1 // -5
        c64scr.print_w(w3)
        c64.CHROUT(':')

        w3 = w1 // -7
        c64scr.print_w(w3)
        c64.CHROUT(':')

        w3 = w1 // -99
        c64scr.print_w(w3)
        c64.CHROUT(':')

        c64scr.print_ub(X)
        c64.CHROUT('\n')
    }
}
