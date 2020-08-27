%import c64flt
%import c64textio
%zeropage basicsafe

main {

    sub start() {
        plus_ubyte(0, 0, 0)
        plus_ubyte(0, 200, 200)
        plus_ubyte(100, 200, 44)

        plus_byte(0, 0, 0)
        plus_byte(-100, 100, 0)
        plus_byte(-50, 100, 50)
        plus_byte(0, -30, -30)
        plus_byte(-30, 0, -30)

        plus_uword(0,0,0)
        plus_uword(0,50000,50000)
        plus_uword(50000,20000,4464)

        plus_word(0,0,0)
        plus_word(-1000,1000,0)
        plus_word(-500,1000,500)
        plus_word(0,-3333,-3333)
        plus_word(-3333,0,-3333)

        plus_float(0,0,0)
        plus_float(1.5,2.5,4.0)
        plus_float(-1.5,3.5,2.0)
        plus_float(-1.1,3.3,2.2)
    }

    sub plus_ubyte(ubyte a1, ubyte a2, ubyte c) {
        ubyte r = a1+a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("ubyte ")
        txt.print_ub(a1)
        txt.print(" + ")
        txt.print_ub(a2)
        txt.print(" = ")
        txt.print_ub(r)
        c64.CHROUT('\n')
    }

    sub plus_byte(byte a1, byte a2, byte c) {
        byte r = a1+a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("byte ")
        txt.print_b(a1)
        txt.print(" + ")
        txt.print_b(a2)
        txt.print(" = ")
        txt.print_b(r)
        c64.CHROUT('\n')
    }

    sub plus_uword(uword a1, uword  a2, uword c) {
        uword  r = a1+a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("uword ")
        txt.print_uw(a1)
        txt.print(" + ")
        txt.print_uw(a2)
        txt.print(" = ")
        txt.print_uw(r)
        c64.CHROUT('\n')
    }

    sub plus_word(word a1, word a2, word c) {
        word r = a1+a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("word ")
        txt.print_w(a1)
        txt.print(" + ")
        txt.print_w(a2)
        txt.print(" = ")
        txt.print_w(r)
        c64.CHROUT('\n')
    }

    sub plus_float(float  a1, float a2, float  c) {
        float r = a1+a2
        if abs(r-c)<0.00001
            txt.print(" ok  ")
        else
            txt.print("err! ")

        txt.print("float ")
        c64flt.print_f(a1)
        txt.print(" + ")
        c64flt.print_f(a2)
        txt.print(" = ")
        c64flt.print_f(r)
        c64.CHROUT('\n')
    }
}
