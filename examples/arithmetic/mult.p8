%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {
        mul_ubyte(0, 0, 0)
        mul_ubyte(20, 1, 20)
        mul_ubyte(20, 10, 200)
        check_eval_stack()

        mul_byte(0, 0, 0)
        mul_byte(10, 10, 100)
        mul_byte(5, -5, -25)
        mul_byte(0, -30, 0)
        check_eval_stack()

        mul_uword(0,0,0)
        mul_uword(50000,1, 50000)
        mul_uword(500,100,50000)
        check_eval_stack()      ; TODO fix stack error

        mul_word(0,0,0)
        mul_word(-10,1000,-10000)
        mul_word(1,-3333,-3333)
        check_eval_stack()      ; TODO fix stack error

        mul_float(0,0,0)
        mul_float(2.5,10,25)
        mul_float(-1.5,10,-15)

        check_eval_stack()      ; TODO fix stack error
    }

    sub mul_ubyte(ubyte a1, ubyte a2, ubyte c) {
        ubyte r = a1*a2
        if r==c
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print("ubyte ")
        c64scr.print_ub(a1)
        c64scr.print(" * ")
        c64scr.print_ub(a2)
        c64scr.print(" = ")
        c64scr.print_ub(r)
        c64.CHROUT('\n')
    }

    sub mul_byte(byte a1, byte a2, byte c) {
        byte r = a1*a2
        if r==c
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print("byte ")
        c64scr.print_b(a1)
        c64scr.print(" * ")
        c64scr.print_b(a2)
        c64scr.print(" = ")
        c64scr.print_b(r)
        c64.CHROUT('\n')
    }

    sub mul_uword(uword a1, uword  a2, uword c) {
        uword  r = a1*a2
        if r==c
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print("uword ")
        c64scr.print_uw(a1)
        c64scr.print(" * ")
        c64scr.print_uw(a2)
        c64scr.print(" = ")
        c64scr.print_uw(r)
        c64.CHROUT('\n')
    }

    sub mul_word(word a1, word a2, word c) {
        word r = a1*a2
        if r==c
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print("word ")
        c64scr.print_w(a1)
        c64scr.print(" * ")
        c64scr.print_w(a2)
        c64scr.print(" = ")
        c64scr.print_w(r)
        c64.CHROUT('\n')
    }

    sub mul_float(float  a1, float a2, float  c) {
        float r = a1*a2
        if abs(r-c)<0.00001
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")

        c64scr.print("float ")
        c64flt.print_f(a1)
        c64scr.print(" * ")
        c64flt.print_f(a2)
        c64scr.print(" = ")
        c64flt.print_f(r)
        c64.CHROUT('\n')
    }

    sub check_eval_stack() {
        if X!=255 {
            c64scr.print("x=")
            c64scr.print_ub(X)
            c64scr.print(" error!\n")
        }
    }
}
