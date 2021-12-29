%import textio
%zeropage basicsafe


main {
    sub start() {
        ubyte @shared xx
        str name = "irmen"
        ubyte[] values = [1,2,3,4,5]

        if 1 in values {
            txt.print("1 ok\n")
        } else {
            txt.print("1 err\n")
        }
        if 5 in values {
            txt.print("7 ok\n")
        } else {
            txt.print("7 err\n")
        }
        if not(8 in values) {
            txt.print("8 ok\n")
        } else {
            txt.print("8 err\n")
        }

        xx = 1
        if xx in values {
            txt.print("xx1 ok\n")
        } else {
            txt.print("xx1 err\n")
        }
        if xx in [1,3,5] {
            txt.print("xx1b ok\n")
        } else {
            txt.print("xx1b err\n")
        }
        xx=5
        if xx in values {
            txt.print("xx7 ok\n")
        } else {
            txt.print("xx7 err\n")
        }
        if xx in [1,3,5] {
            txt.print("xx7b ok\n")
        } else {
            txt.print("xx7b err\n")
        }
        xx=8
        if not(xx in values) {
            txt.print("xx8 ok\n")
        } else {
            txt.print("xx8 err\n")
        }
        if not(xx in [1,3,5]) {
            txt.print("xx8b ok\n")
        } else {
            txt.print("xx8b err\n")
        }

        if xx==9 or xx==10 or xx==11 or xx==12 or xx==13 {
            txt.print("9 10 11\n")
        }

        txt.print("\nthe end\n")
    }

    sub foobar() {
        txt.print("foobar\n")
    }

}
;
;
;main {
;    ubyte @shared foo=99
;      sub thing(uword rr) {
;        ubyte @shared xx = rr[1]    ; should still work as var initializer that will be rewritten
;        ubyte @shared yy
;        yy = rr[2]
;        uword @shared other
;        ubyte @shared zz = other[3]
;      }
;    sub start() {
;
;        txt.print("should print:  10  40  80  20\n")
;
;        ubyte @shared xx
;
;        if xx >0
;            goto $c000
;        else
;           xx++
;labeltje:
;
;        repeat {
;            xx++
;            if xx==10
;                break
;        }
;        txt.print_ub(xx)
;        txt.nl()
;
;        while xx<50 {
;            xx++
;            if xx==40
;                break
;        }
;        txt.print_ub(xx)
;        txt.nl()
;
;        do {
;            xx++
;            if xx==80
;                break
;        } until xx>100
;        txt.print_ub(xx)
;        txt.nl()
;
;        for xx in 0 to 25 {
;            if xx==20
;                break
;        }
;        txt.print_ub(xx)
;        txt.nl()
;    }
;}
