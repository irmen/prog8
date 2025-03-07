%zeropage basicsafe
%import textio
%import floats
%import strings

main {
    sub start() {
        test_string()
        txt.nl()
        test_compare_variable()
        txt.nl()
        test_compare_literal()
    }

    sub test_string() {
        str name="john"

        if (strings.compare(name, "aaa")==0) or (strings.compare(name, "john")==0) or (strings.compare(name, "bbb")==0) {
            txt.print("name1 ok\n")
        }
        if (strings.compare(name, "aaa")==0) or (strings.compare(name, "zzz")==0) or (strings.compare(name, "bbb")==0) {
            txt.print("name2 fail!\n")
        }

        if (strings.ncompare(name, "aaa", 3)==0) or (strings.ncompare(name, "johm", 3)==0) or (strings.ncompare(name, "bbb", 3)==0) {
            txt.print("name1 ok\n")
        }
        if (strings.ncompare(name, "aaa", 2)==0) or (strings.ncompare(name, "zzz", 2)==0) or (strings.ncompare(name, "bbb", 2)==0) {
            txt.print("name2 fail!\n")
        }

        if name=="aaa" or name=="john" or name=="bbb"
            txt.print("name1b ok\n")
        if name=="aaa" or name=="zzz" or name=="bbb"
            txt.print("name2b fail!\n")

    }

    sub test_compare_literal() {
        byte @shared b = -100
        ubyte @shared ub = 20
        word @shared w = -20000
        uword @shared uw = 2000
        float @shared f = -100

        txt.print("all 1: ")
        txt.print_ub(b == -100 as ubyte)
        txt.print_ub(b != -99 as ubyte)
        txt.print_ub(b < -99 as ubyte)
        txt.print_ub(b <= -100 as ubyte)
        txt.print_ub(b > -101 as ubyte)
        txt.print_ub(b >= -100 as ubyte)
        txt.print_ub(ub ==20 as ubyte)
        txt.print_ub(ub !=19 as ubyte)
        txt.print_ub(ub <21 as ubyte)
        txt.print_ub(ub <=20 as ubyte)
        txt.print_ub(ub>19 as ubyte)
        txt.print_ub(ub>=20 as ubyte)
        txt.spc()
        txt.print_ub(w == -20000 as ubyte)
        txt.print_ub(w != -19999 as ubyte)
        txt.print_ub(w < -19999 as ubyte)
        txt.print_ub(w <= -20000 as ubyte)
        txt.print_ub(w > -20001 as ubyte)
        txt.print_ub(w >= -20000 as ubyte)
        txt.print_ub(uw == 2000 as ubyte)
        txt.print_ub(uw != 2001 as ubyte)
        txt.print_ub(uw < 2001 as ubyte)
        txt.print_ub(uw <= 2000 as ubyte)
        txt.print_ub(uw > 1999 as ubyte)
        txt.print_ub(uw >= 2000 as ubyte)
        txt.spc()
        txt.print_ub(f == -100.0 as ubyte)
        txt.print_ub(f != -99.0 as ubyte)
        txt.print_ub(f < -99.0 as ubyte)
        txt.print_ub(f <= -100.0 as ubyte)
        txt.print_ub(f > -101.0 as ubyte)
        txt.print_ub(f >= -100.0 as ubyte)
        txt.nl()

        txt.print("all 0: ")
        txt.print_ub(b == -99 as ubyte)
        txt.print_ub(b != -100 as ubyte)
        txt.print_ub(b < -100 as ubyte)
        txt.print_ub(b <= -101 as ubyte)
        txt.print_ub(b > -100 as ubyte)
        txt.print_ub(b >= -99 as ubyte)
        txt.print_ub(ub ==21 as ubyte)
        txt.print_ub(ub !=20 as ubyte)
        txt.print_ub(ub <20 as ubyte)
        txt.print_ub(ub <=19 as ubyte)
        txt.print_ub(ub>20 as ubyte)
        txt.print_ub(ub>=21 as ubyte)
        txt.spc()
        txt.print_ub(w == -20001 as ubyte)
        txt.print_ub(w != -20000 as ubyte)
        txt.print_ub(w < -20000 as ubyte)
        txt.print_ub(w <= -20001 as ubyte)
        txt.print_ub(w > -20000 as ubyte)
        txt.print_ub(w >= -19999 as ubyte)
        txt.print_ub(uw == 1999 as ubyte)
        txt.print_ub(uw != 2000 as ubyte)
        txt.print_ub(uw < 2000 as ubyte)
        txt.print_ub(uw <= 1999 as ubyte)
        txt.print_ub(uw > 2000 as ubyte)
        txt.print_ub(uw >= 2001 as ubyte)
        txt.spc()
        txt.print_ub(f == -99.0 as ubyte)
        txt.print_ub(f != -100.0 as ubyte)
        txt.print_ub(f < -100.0 as ubyte)
        txt.print_ub(f <= -101.0 as ubyte)
        txt.print_ub(f > -100.0 as ubyte)
        txt.print_ub(f >= -99.0 as ubyte)
        txt.nl()

        txt.print("all .: ")
        if b == -100 txt.chrout('.')
        if b != -99 txt.chrout('.')
        if b < -99 txt.chrout('.')
        if b <= -100 txt.chrout('.')
        if b > -101 txt.chrout('.')
        if b >= -100 txt.chrout('.')
        if ub ==20 txt.chrout('.')
        if ub !=19 txt.chrout('.')
        if ub <21 txt.chrout('.')
        if ub <=20 txt.chrout('.')
        if ub>19 txt.chrout('.')
        if ub>=20 txt.chrout('.')
        txt.spc()
        if w == -20000 txt.chrout('.')
        if w != -19999 txt.chrout('.')
        if w < -19999 txt.chrout('.')
        if w <= -20000 txt.chrout('.')
        if w > -20001 txt.chrout('.')
        if w >= -20000 txt.chrout('.')
        if uw == 2000 txt.chrout('.')
        if uw != 2001 txt.chrout('.')
        if uw < 2001 txt.chrout('.')
        if uw <= 2000 txt.chrout('.')
        if uw > 1999 txt.chrout('.')
        if uw >= 2000 txt.chrout('.')
        txt.spc()
        if f == -100.0 txt.chrout('.')
        if f != -99.0 txt.chrout('.')
        if f < -99.0 txt.chrout('.')
        if f <= -100.0 txt.chrout('.')
        if f > -101.0 txt.chrout('.')
        if f >= -100.0 txt.chrout('.')
        txt.nl()

        txt.print(" no !: ")
        if b == -99 txt.chrout('!')
        if b != -100 txt.chrout('!')
        if b < -100 txt.chrout('!')
        if b <= -101 txt.chrout('!')
        if b > -100 txt.chrout('!')
        if b >= -99 txt.chrout('!')
        if ub ==21 txt.chrout('!')
        if ub !=20 txt.chrout('!')
        if ub <20 txt.chrout('!')
        if ub <=19 txt.chrout('!')
        if ub>20 txt.chrout('!')
        if ub>=21 txt.chrout('!')
        txt.spc()
        if w == -20001 txt.chrout('!')
        if w != -20000 txt.chrout('!')
        if w < -20000 txt.chrout('!')
        if w <= -20001 txt.chrout('!')
        if w > -20000 txt.chrout('!')
        if w >= -19999 txt.chrout('!')
        if uw == 1999 txt.chrout('!')
        if uw != 2000 txt.chrout('!')
        if uw < 2000 txt.chrout('!')
        if uw <= 1999 txt.chrout('!')
        if uw > 2000 txt.chrout('!')
        if uw >= 2001 txt.chrout('!')
        txt.spc()
        if f == -99.0 txt.chrout('!')
        if f != -100.0 txt.chrout('!')
        if f < -100.0 txt.chrout('!')
        if f <= -101.0 txt.chrout('!')
        if f > -100.0 txt.chrout('!')
        if f >= -99.0 txt.chrout('!')
        txt.nl()

        txt.print("all .: ")
        if b == -100 txt.chrout('.') else txt.chrout('!')
        if b != -99 txt.chrout('.') else txt.chrout('!')
        if b < -99 txt.chrout('.') else txt.chrout('!')
        if b <= -100 txt.chrout('.') else txt.chrout('!')
        if b > -101 txt.chrout('.') else txt.chrout('!')
        if b >= -100 txt.chrout('.') else txt.chrout('!')
        if ub ==20 txt.chrout('.') else txt.chrout('!')
        if ub !=19 txt.chrout('.') else txt.chrout('!')
        if ub <21 txt.chrout('.') else txt.chrout('!')
        if ub <=20 txt.chrout('.') else txt.chrout('!')
        if ub>19 txt.chrout('.') else txt.chrout('!')
        if ub>=20 txt.chrout('.') else txt.chrout('!')
        txt.spc()
        if w == -20000 txt.chrout('.') else txt.chrout('!')
        if w != -19999 txt.chrout('.') else txt.chrout('!')
        if w < -19999 txt.chrout('.') else txt.chrout('!')
        if w <= -20000 txt.chrout('.') else txt.chrout('!')
        if w > -20001 txt.chrout('.') else txt.chrout('!')
        if w >= -20000 txt.chrout('.') else txt.chrout('!')
        if uw == 2000 txt.chrout('.') else txt.chrout('!')
        if uw != 2001 txt.chrout('.') else txt.chrout('!')
        if uw < 2001 txt.chrout('.') else txt.chrout('!')
        if uw <= 2000 txt.chrout('.') else txt.chrout('!')
        if uw > 1999 txt.chrout('.') else txt.chrout('!')
        if uw >= 2000 txt.chrout('.') else txt.chrout('!')
        txt.spc()
        if f == -100.0 txt.chrout('.') else txt.chrout('!')
        if f != -99.0 txt.chrout('.') else txt.chrout('!')
        if f < -99.0 txt.chrout('.') else txt.chrout('!')
        if f <= -100.0 txt.chrout('.') else txt.chrout('!')
        if f > -101.0 txt.chrout('.') else txt.chrout('!')
        if f >= -100.0 txt.chrout('.') else txt.chrout('!')
        txt.nl()

        txt.print("all .: ")
        if b == -99 txt.chrout('!') else txt.chrout('.')
        if b != -100 txt.chrout('!') else txt.chrout('.')
        if b < -100 txt.chrout('!') else txt.chrout('.')
        if b <= -101 txt.chrout('!') else txt.chrout('.')
        if b > -100 txt.chrout('!') else txt.chrout('.')
        if b >= -99 txt.chrout('!') else txt.chrout('.')
        if ub ==21 txt.chrout('!') else txt.chrout('.')
        if ub !=20 txt.chrout('!') else txt.chrout('.')
        if ub <20 txt.chrout('!') else txt.chrout('.')
        if ub <=19 txt.chrout('!') else txt.chrout('.')
        if ub>20 txt.chrout('!') else txt.chrout('.')
        if ub>=21 txt.chrout('!') else txt.chrout('.')
        txt.spc()
        if w == -20001 txt.chrout('!') else txt.chrout('.')
        if w != -20000 txt.chrout('!') else txt.chrout('.')
        if w < -20000 txt.chrout('!') else txt.chrout('.')
        if w <= -20001 txt.chrout('!') else txt.chrout('.')
        if w > -20000 txt.chrout('!') else txt.chrout('.')
        if w >= -19999 txt.chrout('!') else txt.chrout('.')
        if uw == 1999 txt.chrout('!') else txt.chrout('.')
        if uw != 2000 txt.chrout('!') else txt.chrout('.')
        if uw < 2000 txt.chrout('!') else txt.chrout('.')
        if uw <= 1999 txt.chrout('!') else txt.chrout('.')
        if uw > 2000 txt.chrout('!') else txt.chrout('.')
        if uw >= 2001 txt.chrout('!') else txt.chrout('.')
        txt.spc()
        if f == -99.0 txt.chrout('!') else txt.chrout('.')
        if f != -100.0 txt.chrout('!') else txt.chrout('.')
        if f < -100.0 txt.chrout('!') else txt.chrout('.')
        if f <= -101.0 txt.chrout('!') else txt.chrout('.')
        if f > -100.0 txt.chrout('!') else txt.chrout('.')
        if f >= -99.0 txt.chrout('!') else txt.chrout('.')
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

        f = 0.0
        floats.print(f)
        while f<2.2 {
            f+=0.1
            floats.print(f)
        }
        floats.print(f)
        txt.print("  2.2\n")
    }

    sub test_compare_variable() {
        byte @shared b = -100
        ubyte @shared ub = 20
        word @shared w = -20000
        uword @shared uw = 2000
        float @shared f = -100

        byte @shared minus_100 = -100
        byte @shared minus_99 = -99
        byte @shared minus_20 = -20
        byte @shared minus_101 = -101
        ubyte @shared nineteen = 19
        ubyte @shared twenty = 20
        ubyte @shared twenty1 = 21
        ubyte @shared twohundred = 200
        float @shared f_minus_100 = -100.0
        float @shared f_minus_101 = -101.0
        float @shared f_minus_99 = -99.0
        float @shared twodottwo = 2.2
        word @shared minus8000 = -8000
        word @shared eightthousand = 8000
        word @shared w_min_20000 = -20000
        word @shared w_min_19999 = -19999
        word @shared w_min_20001 = -20001
        uword @shared twothousand = 2000
        uword @shared twothousand1 = 2001
        uword @shared nineteen99 = 1999


        txt.print("all 1: ")
        txt.print_ub(b == minus_100 as ubyte)
        txt.print_ub(b != minus_99 as ubyte)
        txt.print_ub(b < minus_99 as ubyte)
        txt.print_ub(b <= minus_100 as ubyte)
        txt.print_ub(b > minus_101 as ubyte)
        txt.print_ub(b >= minus_100 as ubyte)
        txt.print_ub(ub ==twenty as ubyte)
        txt.print_ub(ub !=nineteen as ubyte)
        txt.print_ub(ub <twenty1 as ubyte)
        txt.print_ub(ub <=twenty as ubyte)
        txt.print_ub(ub>nineteen as ubyte)
        txt.print_ub(ub>=twenty as ubyte)
        txt.spc()
        txt.print_ub(w == w_min_20000 as ubyte)
        txt.print_ub(w != w_min_19999 as ubyte)
        txt.print_ub(w < w_min_19999 as ubyte)
        txt.print_ub(w <= w_min_20000 as ubyte)
        txt.print_ub(w > w_min_20001 as ubyte)
        txt.print_ub(w >= w_min_20000 as ubyte)
        txt.print_ub(uw == twothousand as ubyte)
        txt.print_ub(uw != twothousand1 as ubyte)
        txt.print_ub(uw < twothousand1 as ubyte)
        txt.print_ub(uw <= twothousand as ubyte)
        txt.print_ub(uw > nineteen99 as ubyte)
        txt.print_ub(uw >= twothousand as ubyte)
        txt.spc()
        txt.print_ub(f == f_minus_100 as ubyte)
        txt.print_ub(f != f_minus_99 as ubyte)
        txt.print_ub(f < f_minus_99 as ubyte)
        txt.print_ub(f <= f_minus_100 as ubyte)
        txt.print_ub(f > f_minus_101 as ubyte)
        txt.print_ub(f >= f_minus_100 as ubyte)
        txt.nl()

        txt.print("all 0: ")
        txt.print_ub(b == minus_99 as ubyte)
        txt.print_ub(b != minus_100 as ubyte)
        txt.print_ub(b < minus_100 as ubyte)
        txt.print_ub(b <= minus_101 as ubyte)
        txt.print_ub(b > minus_100 as ubyte)
        txt.print_ub(b >= minus_99 as ubyte)
        txt.print_ub(ub ==twenty1 as ubyte)
        txt.print_ub(ub !=twenty as ubyte)
        txt.print_ub(ub <twenty as ubyte)
        txt.print_ub(ub <=nineteen as ubyte)
        txt.print_ub(ub>twenty as ubyte)
        txt.print_ub(ub>=twenty1 as ubyte)
        txt.spc()
        txt.print_ub(w == w_min_20001 as ubyte)
        txt.print_ub(w != w_min_20000 as ubyte)
        txt.print_ub(w < w_min_20000 as ubyte)
        txt.print_ub(w <= w_min_20001 as ubyte)
        txt.print_ub(w > w_min_20000 as ubyte)
        txt.print_ub(w >= w_min_19999 as ubyte)
        txt.print_ub(uw == nineteen99 as ubyte)
        txt.print_ub(uw != twothousand as ubyte)
        txt.print_ub(uw < twothousand as ubyte)
        txt.print_ub(uw <= nineteen99 as ubyte)
        txt.print_ub(uw > twothousand as ubyte)
        txt.print_ub(uw >= twothousand1 as ubyte)
        txt.spc()
        txt.print_ub(f == f_minus_99 as ubyte)
        txt.print_ub(f != f_minus_100 as ubyte)
        txt.print_ub(f < f_minus_100 as ubyte)
        txt.print_ub(f <= f_minus_101 as ubyte)
        txt.print_ub(f > f_minus_100 as ubyte)
        txt.print_ub(f >= f_minus_99 as ubyte)
        txt.nl()

        txt.print("all .: ")
        if b == minus_100 txt.chrout('.')
        if b != minus_99 txt.chrout('.')
        if b < minus_99 txt.chrout('.')
        if b <= minus_100 txt.chrout('.')
        if b > minus_101 txt.chrout('.')
        if b >= minus_100 txt.chrout('.')
        if ub == twenty txt.chrout('.')
        if ub != nineteen txt.chrout('.')
        if ub < twenty1 txt.chrout('.')
        if ub <= twenty txt.chrout('.')
        if ub> nineteen txt.chrout('.')
        if ub>= twenty txt.chrout('.')
        txt.spc()
        if w == w_min_20000 txt.chrout('.')
        if w != w_min_19999 txt.chrout('.')
        if w < w_min_19999 txt.chrout('.')
        if w <= w_min_20000 txt.chrout('.')
        if w > w_min_20001 txt.chrout('.')
        if w >= w_min_20000 txt.chrout('.')
        if uw == twothousand txt.chrout('.')
        if uw != twothousand1 txt.chrout('.')
        if uw < twothousand1 txt.chrout('.')
        if uw <= twothousand txt.chrout('.')
        if uw > nineteen99 txt.chrout('.')
        if uw >= twothousand txt.chrout('.')
        txt.spc()
        if f == f_minus_100 txt.chrout('.')
        if f != f_minus_99 txt.chrout('.')
        if f < f_minus_99 txt.chrout('.')
        if f <= f_minus_100 txt.chrout('.')
        if f > f_minus_101 txt.chrout('.')
        if f >= f_minus_100 txt.chrout('.')
        txt.nl()

        txt.print(" no !: ")
        if b == minus_99 txt.chrout('!')
        if b != minus_100 txt.chrout('!')
        if b < minus_100 txt.chrout('!')
        if b <= minus_101 txt.chrout('!')
        if b > minus_100 txt.chrout('!')
        if b >= minus_99 txt.chrout('!')
        if ub == twenty1 txt.chrout('!')
        if ub != twenty txt.chrout('!')
        if ub < twenty txt.chrout('!')
        if ub <= nineteen txt.chrout('!')
        if ub> twenty txt.chrout('!')
        if ub>= twenty1 txt.chrout('!')
        txt.spc()
        if w == w_min_20001 txt.chrout('!')
        if w != w_min_20000 txt.chrout('!')
        if w < w_min_20000 txt.chrout('!')
        if w <= w_min_20001 txt.chrout('!')
        if w > w_min_20000 txt.chrout('!')
        if w >= w_min_19999 txt.chrout('!')
        if uw == nineteen99 txt.chrout('!')
        if uw != twothousand txt.chrout('!')
        if uw < twothousand txt.chrout('!')
        if uw <= nineteen99 txt.chrout('!')
        if uw > twothousand txt.chrout('!')
        if uw >= twothousand1 txt.chrout('!')
        txt.spc()
        if f == f_minus_99 txt.chrout('!')
        if f != f_minus_100 txt.chrout('!')
        if f < f_minus_100 txt.chrout('!')
        if f <= f_minus_101 txt.chrout('!')
        if f > f_minus_100 txt.chrout('!')
        if f >= f_minus_99 txt.chrout('!')
        txt.nl()

        txt.print("all .: ")
        if b == minus_100 txt.chrout('.') else txt.chrout('!')
        if b != minus_99 txt.chrout('.') else txt.chrout('!')
        if b < minus_99 txt.chrout('.') else txt.chrout('!')
        if b <= minus_100 txt.chrout('.') else txt.chrout('!')
        if b > minus_101 txt.chrout('.') else txt.chrout('!')
        if b >= minus_100 txt.chrout('.') else txt.chrout('!')
        if ub == twenty txt.chrout('.') else txt.chrout('!')
        if ub != nineteen txt.chrout('.') else txt.chrout('!')
        if ub < twenty1 txt.chrout('.') else txt.chrout('!')
        if ub <= twenty txt.chrout('.') else txt.chrout('!')
        if ub> nineteen txt.chrout('.') else txt.chrout('!')
        if ub>=twenty txt.chrout('.') else txt.chrout('!')
        txt.spc()
        if w == w_min_20000 txt.chrout('.') else txt.chrout('!')
        if w != w_min_19999 txt.chrout('.') else txt.chrout('!')
        if w < w_min_19999 txt.chrout('.') else txt.chrout('!')
        if w <= w_min_20000 txt.chrout('.') else txt.chrout('!')
        if w > w_min_20001 txt.chrout('.') else txt.chrout('!')
        if w >= w_min_20000 txt.chrout('.') else txt.chrout('!')
        if uw == twothousand txt.chrout('.') else txt.chrout('!')
        if uw != twothousand1 txt.chrout('.') else txt.chrout('!')
        if uw < twothousand1 txt.chrout('.') else txt.chrout('!')
        if uw <= twothousand txt.chrout('.') else txt.chrout('!')
        if uw > nineteen99 txt.chrout('.') else txt.chrout('!')
        if uw >= twothousand txt.chrout('.') else txt.chrout('!')
        txt.spc()
        if f == f_minus_100 txt.chrout('.') else txt.chrout('!')
        if f != f_minus_99 txt.chrout('.') else txt.chrout('!')
        if f < f_minus_99 txt.chrout('.') else txt.chrout('!')
        if f <= f_minus_100 txt.chrout('.') else txt.chrout('!')
        if f > f_minus_101 txt.chrout('.') else txt.chrout('!')
        if f >= f_minus_100 txt.chrout('.') else txt.chrout('!')
        txt.nl()

        txt.print("all .: ")
        if b == minus_99 txt.chrout('!') else txt.chrout('.')
        if b != minus_100 txt.chrout('!') else txt.chrout('.')
        if b < minus_100 txt.chrout('!') else txt.chrout('.')
        if b <= minus_101 txt.chrout('!') else txt.chrout('.')
        if b > minus_100 txt.chrout('!') else txt.chrout('.')
        if b >= minus_99 txt.chrout('!') else txt.chrout('.')
        if ub == twenty1 txt.chrout('!') else txt.chrout('.')
        if ub !=twenty txt.chrout('!') else txt.chrout('.')
        if ub <twenty txt.chrout('!') else txt.chrout('.')
        if ub <=nineteen txt.chrout('!') else txt.chrout('.')
        if ub>twenty txt.chrout('!') else txt.chrout('.')
        if ub>= twenty1 txt.chrout('!') else txt.chrout('.')
        txt.spc()
        if w == w_min_20001 txt.chrout('!') else txt.chrout('.')
        if w != w_min_20000 txt.chrout('!') else txt.chrout('.')
        if w < w_min_20000 txt.chrout('!') else txt.chrout('.')
        if w <= w_min_20001 txt.chrout('!') else txt.chrout('.')
        if w > w_min_20000 txt.chrout('!') else txt.chrout('.')
        if w >= w_min_19999 txt.chrout('!') else txt.chrout('.')
        if uw == nineteen99 txt.chrout('!') else txt.chrout('.')
        if uw != twothousand txt.chrout('!') else txt.chrout('.')
        if uw < twothousand txt.chrout('!') else txt.chrout('.')
        if uw <= nineteen99 txt.chrout('!') else txt.chrout('.')
        if uw > twothousand txt.chrout('!') else txt.chrout('.')
        if uw >= twothousand1 txt.chrout('!') else txt.chrout('.')
        txt.spc()
        if f == f_minus_99 txt.chrout('!') else txt.chrout('.')
        if f != f_minus_100 txt.chrout('!') else txt.chrout('.')
        if f < f_minus_100 txt.chrout('!') else txt.chrout('.')
        if f <= f_minus_101 txt.chrout('!') else txt.chrout('.')
        if f > f_minus_100 txt.chrout('!') else txt.chrout('.')
        if f >= f_minus_99 txt.chrout('!') else txt.chrout('.')
        txt.nl()


        b = minus_100
        while b <= minus_20
            b++
        txt.print_b(b)
        txt.print(" -19\n")
        b = minus_100
        while b < -minus_20
            b++
        txt.print_b(b)
        txt.print(" -20\n")

        ub = 20
        while ub <= twohundred
            ub++
        txt.print_ub(ub)
        txt.print(" 201\n")
        ub = 20
        while ub < twohundred
            ub++
        txt.print_ub(ub)
        txt.print(" 200\n")

        w = w_min_20000
        while w <= minus8000 {
            w++
        }
        txt.print_w(w)
        txt.print("  -7999\n")
        w = w_min_20000
        while w < minus8000 {
            w++
        }
        txt.print_w(w)
        txt.print("  -8000\n")

        uw = 2000
        while uw <= eightthousand {
            uw++
        }
        txt.print_uw(uw)
        txt.print("  8001\n")
        uw = 2000
        while uw < eightthousand {
            uw++
        }
        txt.print_uw(uw)
        txt.print("  8000\n")

        f = 0.0
        floats.print(f)
        while f<twodottwo {
            f+=0.1
            floats.print(f)
        }
        floats.print(f)
        txt.print("  2.2\n")
    }
}
