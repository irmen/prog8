%import c64lib
%import c64utils
%import c64flt
%zeropage basicsafe

main {

    sub start() {
        div_ubyte(0, 1, 0)
        div_ubyte(100, 6, 16)
        div_ubyte(255, 2, 127)

        div_byte(0, 1, 0)
        div_byte(100, -6, -16)
        div_byte(127, -2, -63)

        div_uword(0,1,0)
        div_uword(40000,500,80)
        div_uword(43211,2,21605)

        div_word(0,1,0)
        div_word(-20000,500,-40)
        div_word(-2222,2,-1111)

        div_float(0,1,0)
        div_float(999.9,111.0,9.008108108108107)

    }

    sub div_ubyte(ubyte a1, ubyte a2, ubyte c) {
        ubyte r = a1/a2
        if r==c
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print("ubyte ")
        c64scr.print_ub(a1)
        c64scr.print(" / ")
        c64scr.print_ub(a2)
        c64scr.print(" = ")
        c64scr.print_ub(r)
        c64.CHROUT('\n')
    }

    sub div_byte(byte a1, byte a2, byte c) {
        byte r = a1/a2
        if r==c
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print("byte ")
        c64scr.print_b(a1)
        c64scr.print(" / ")
        c64scr.print_b(a2)
        c64scr.print(" = ")
        c64scr.print_b(r)
        c64.CHROUT('\n')
    }

    sub div_uword(uword a1, uword  a2, uword c) {
        uword  r = a1/a2
        if r==c
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print("uword ")
        c64scr.print_uw(a1)
        c64scr.print(" / ")
        c64scr.print_uw(a2)
        c64scr.print(" = ")
        c64scr.print_uw(r)
        c64.CHROUT('\n')
    }

    sub div_word(word a1, word a2, word c) {
        word r = a1/a2
        if r==c
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")
        c64scr.print("word ")
        c64scr.print_w(a1)
        c64scr.print(" / ")
        c64scr.print_w(a2)
        c64scr.print(" = ")
        c64scr.print_w(r)
        c64.CHROUT('\n')
    }

    sub div_float(float  a1, float a2, float  c) {
        float r = a1/a2
        if abs(r-c)<0.00001
            c64scr.print(" ok  ")
        else
            c64scr.print("err! ")

        c64scr.print("float ")
        c64flt.print_f(a1)
        c64scr.print(" / ")
        c64flt.print_f(a2)
        c64scr.print(" = ")
        c64flt.print_f(r)
        c64.CHROUT('\n')
    }
}
