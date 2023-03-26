%zeropage basicsafe
%import textio

main {
    sub start() {
        byte b = -100
        ubyte ub = 20
        word w = -20000
        uword uw = 2000

        txt.print("all 1: ")
        txt.print_ub(b == -100)
        txt.print_ub(b != -99)
        txt.print_ub(b < -99)
        txt.print_ub(b <= -100)
        txt.print_ub(b > -101)
        txt.print_ub(b >= -100)
        txt.print_ub(ub ==20)
        txt.print_ub(ub !=19)
        txt.print_ub(ub <21)
        txt.print_ub(ub <=20)
        txt.print_ub(ub>19)
        txt.print_ub(ub>=20)
        txt.spc()
        txt.print_ub(w == -20000)
        txt.print_ub(w != -19999)
        txt.print_ub(w < -19999)
        txt.print_ub(w <= -20000)
        txt.print_ub(w > -20001)
        txt.print_ub(w >= -20000)
        txt.print_ub(uw == 2000)
        txt.print_ub(uw != 2001)
        txt.print_ub(uw < 2001)
        txt.print_ub(uw <= 2000)
        txt.print_ub(uw > 1999)
        txt.print_ub(uw >= 2000)
        txt.nl()
        txt.print("all 0: ")
        txt.print_ub(b == -99)
        txt.print_ub(b != -100)
        txt.print_ub(b < -100)
        txt.print_ub(b <= -101)
        txt.print_ub(b > -100)
        txt.print_ub(b >= -99)
        txt.print_ub(ub ==21)
        txt.print_ub(ub !=20)
        txt.print_ub(ub <20)
        txt.print_ub(ub <=19)
        txt.print_ub(ub>20)
        txt.print_ub(ub>=21)
        txt.spc()
        txt.print_ub(w == -20001)
        txt.print_ub(w != -20000)
        txt.print_ub(w < -20000)
        txt.print_ub(w <= -20001)
        txt.print_ub(w > -20000)
        txt.print_ub(w >= -19999)
        txt.print_ub(uw == 1999)
        txt.print_ub(uw != 2000)
        txt.print_ub(uw < 2000)
        txt.print_ub(uw <= 1999)
        txt.print_ub(uw > 2000)
        txt.print_ub(uw >= 2001)
        txt.nl()


        b = -100
        while b <= -20
            b++
        txt.print_b(b)
        txt.print(" -19\n")
        b = -100
        while b < -20
            b++
        txt.print_b(b)
        txt.print(" -20\n")

        ub = 20
        while ub <= 200
            ub++
        txt.print_ub(ub)
        txt.print(" 201\n")
        ub = 20
        while ub < 200
            ub++
        txt.print_ub(ub)
        txt.print(" 200\n")

        w = -20000
        while w <= -8000 {
            w++
        }
        txt.print_w(w)
        txt.print("  -7999\n")
        w = -20000
        while w < -8000 {
            w++
        }
        txt.print_w(w)
        txt.print("  -8000\n")

        uw = 2000
        while uw <= 8000 {
            uw++
        }
        txt.print_uw(uw)
        txt.print("  8001\n")
        uw = 2000
        while uw < 8000 {
            uw++
        }
        txt.print_uw(uw)
        txt.print("  8000\n")

;        float f
;        while f<2.2 {
;            f+=0.1
;        }
    }
}
