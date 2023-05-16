%import floats
%import textio
%zeropage basicsafe

main {

    sub start() {
        mul_ubyte(0, 0, 0)
        mul_ubyte(20, 1, 20)
        mul_ubyte(20, 10, 200)

        mul_byte(0, 0, 0)
        mul_byte(10, 10, 100)
        mul_byte(5, -5, -25)
        mul_byte(0, -30, 0)

        mul_uword(0,0,0)
        mul_uword(50000,1, 50000)
        mul_uword(500,100,50000)

        mul_word(0,0,0)
        mul_word(-10,1000,-10000)
        mul_word(1,-3333,-3333)

        mul_float(0,0,0)
        mul_float(2.5,10,25)
        mul_float(-1.5,10,-15)
    }

    sub mul_ubyte(ubyte a1, ubyte a2, ubyte c) {
        ubyte r = a1*a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("ubyte ")
        txt.print_ub(a1)
        txt.print(" * ")
        txt.print_ub(a2)
        txt.print(" = ")
        txt.print_ub(r)
        cbm.CHROUT('\n')
    }

    sub mul_byte(byte a1, byte a2, byte c) {
        byte r = a1*a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("byte ")
        txt.print_b(a1)
        txt.print(" * ")
        txt.print_b(a2)
        txt.print(" = ")
        txt.print_b(r)
        cbm.CHROUT('\n')
    }

    sub mul_uword(uword a1, uword  a2, uword c) {
        uword  r = a1*a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("uword ")
        txt.print_uw(a1)
        txt.print(" * ")
        txt.print_uw(a2)
        txt.print(" = ")
        txt.print_uw(r)
        cbm.CHROUT('\n')
    }

    sub mul_word(word a1, word a2, word c) {
        word r = a1*a2
        if r==c
            txt.print(" ok  ")
        else
            txt.print("err! ")
        txt.print("word ")
        txt.print_w(a1)
        txt.print(" * ")
        txt.print_w(a2)
        txt.print(" = ")
        txt.print_w(r)
        cbm.CHROUT('\n')
    }

    sub mul_float(float  a1, float a2, float  c) {
        float r = a1*a2
        if abs(r-c)<0.00001
            txt.print(" ok  ")
        else
            txt.print("err! ")

        txt.print("float ")
        floats.print_f(a1)
        txt.print(" * ")
        floats.print_f(a2)
        txt.print(" = ")
        floats.print_f(r)
        cbm.CHROUT('\n')
    }
}
