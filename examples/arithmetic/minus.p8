%import floats
%import textio
%zeropage basicsafe

; TODO fix crash on CX16

main {

    sub start() {
        minus_ubyte(0, 0, 0)
        minus_ubyte(200, 0, 200)
        minus_ubyte(200, 100, 100)
        minus_ubyte(100, 200, 156)

        minus_byte(0, 0, 0)
        minus_byte(100, 100, 0)
        minus_byte(50, -50, 100)
        minus_byte(0, -30, 30)
        minus_byte(-30, 0, -30)

        minus_uword(0,0,0)
        minus_uword(50000,0, 50000)
        minus_uword(50000,20000,30000)
        minus_uword(20000,50000,35536)

        minus_word(0,0,0)
        minus_word(1000,1000,0)
        minus_word(-1000,1000,-2000)
        minus_word(1000,500,500)
        minus_word(0,-3333,3333)
        minus_word(-3333,0,-3333)

        minus_float(0,0,0)
        minus_float(2.5,1.5,1.0)
        minus_float(-1.5,3.5,-5.0)
    }

    sub minus_ubyte(ubyte a1, ubyte a2, ubyte c) {
        ubyte r = a1-a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("ubyte ")
        txt.print_ub(a1)
        txt.print(" - ")
        txt.print_ub(a2)
        txt.print(" = ")
        txt.print_ub(r)
        c64.CHROUT('\n')
    }

    sub minus_byte(byte a1, byte a2, byte c) {
        byte r = a1-a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("byte ")
        txt.print_b(a1)
        txt.print(" - ")
        txt.print_b(a2)
        txt.print(" = ")
        txt.print_b(r)
        c64.CHROUT('\n')
    }

    sub minus_uword(uword a1, uword  a2, uword c) {
        uword  r = a1-a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("uword ")
        txt.print_uw(a1)
        txt.print(" - ")
        txt.print_uw(a2)
        txt.print(" = ")
        txt.print_uw(r)
        c64.CHROUT('\n')
    }

    sub minus_word(word a1, word a2, word c) {
        word r = a1-a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("word ")
        txt.print_w(a1)
        txt.print(" - ")
        txt.print_w(a2)
        txt.print(" = ")
        txt.print_w(r)
        c64.CHROUT('\n')
    }

    sub minus_float(float  a1, float a2, float  c) {
        float r = a1-a2
        if abs(r-c)<0.00001
            txt.print(" ok  ")
        else
            txt.print("err! ")

        txt.print("float ")
        floats.print_f(a1)
        txt.print(" - ")
        floats.print_f(a2)
        txt.print(" = ")
        floats.print_f(r)
        c64.CHROUT('\n')
    }
}
