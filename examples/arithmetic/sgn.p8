%import c64flt
%zeropage basicsafe

main {

    sub start() {
        byte b1
        byte b2
        ubyte ub1
        ubyte ub2
        word w1
        word w2
        uword uw1
        uword uw2
        float f1
        float f2

        b1 = 10
        b2 = 10
        if sgn(b2-b1) != 0
            c64scr.print("sgn1 error1\n")

        b1 = -100
        b2 = -100
        if sgn(b2-b1) != 0
            c64scr.print("sgn1 error2\n")

        ub1 = 200
        ub2 = 200
        if sgn(ub2-ub1) != 0
            c64scr.print("sgn1 error3\n")

        w1 = 100
        w2 = 100
        if sgn(w2-w1) != 0
            c64scr.print("sgn1 error4\n")

        w1 = -2000
        w2 = -2000
        if sgn(w2-w1) != 0
            c64scr.print("sgn1 error5\n")

        uw1 = 999
        uw2 = 999
        if sgn(uw2-uw1) != 0
            c64scr.print("sgn1 error6\n")

        f1 = 3.45
        f2 = 3.45
        if sgn(f2-f1) != 0
            c64scr.print("sgn1 error7\n")


        ; -1
        b1 = 11
        b2 = 10
        if sgn(b2-b1) != -1
            c64scr.print("sgn2 error1\n")

        b1 = -10
        b2 = -100
        if sgn(b2-b1) != -1
            c64scr.print("sgn2 error2\n")

        ub1 = 202
        ub2 = 200
        if sgn(ub2 as byte - ub1 as byte) != -1
            c64scr.print("sgn2 error3\n")

        w1 = 101
        w2 = 100
        if sgn(w2-w1) != -1
            c64scr.print("sgn2 error4\n")

        w1 = -200
        w2 = -2000
        if sgn(w2-w1) != -1
            c64scr.print("sgn2 error5\n")

        uw1 = 2222
        uw2 = 999
        if sgn((uw2 as word) - (uw1 as word)) != -1
            c64scr.print("sgn2 error6a\n")
        if sgn(uw2 - uw1) != 1      ; always 0 or 1 if unsigned
            c64scr.print("sgn2 error6b\n")

        f1 = 3.45
        f2 = 1.11
        if sgn(f2-f1) != -1
            c64scr.print("sgn2 error7\n")

        ; +1
        b1 = 11
        b2 = 20
        if sgn(b2-b1) != 1
            c64scr.print("sgn3 error1\n")

        b1 = -10
        b2 = -1
        if sgn(b2-b1) != 1
            c64scr.print("sgn3 error2\n")

        ub1 = 202
        ub2 = 205
        if sgn(ub2-ub1) != 1
            c64scr.print("sgn3 error3\n")

        w1 = 101
        w2 = 200
        if sgn(w2-w1) != 1
            c64scr.print("sgn3 error4\n")

        w1 = -200
        w2 = -20
        if sgn(w2-w1) != 1
            c64scr.print("sgn3 error5\n")

        uw1 = 2222
        uw2 = 9999
        if sgn(uw2-uw1) != 1
            c64scr.print("sgn3 error6\n")

        f1 = 3.45
        f2 = 5.11
        if sgn(f2-f1) != 1
            c64scr.print("sgn3 error7\n")

        c64scr.print("should see no sgn errors\n")
    }
 }
