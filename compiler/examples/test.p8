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
        w1 = 12000
        w2 = -333     ; -333
        w3 = w1 // w2
        c64scr.print_w(w3)
        c64.CHROUT('\n')
        w1 = -12000        ; -12000
        w2 = 333
        w3 = w1 // w2
        c64scr.print_w(w3)
        c64.CHROUT('\n')
        w2 = 4444
        w3 = w1 // w2
        c64scr.print_w(w3)
        c64.CHROUT('\n')

        w3 = w1 // -5        ; -5
        c64scr.print_w(w3)
        c64.CHROUT('\n')

        w3 = w1 // -7        ; -7
        c64scr.print_w(w3)
        c64.CHROUT('\n')

        w1 = -w1
        w3 = w1 // -999       ;-999
        c64scr.print_w(w3)
        c64.CHROUT('\n')

        c64scr.print_ub(X)
        c64.CHROUT('\n')
    }
}
