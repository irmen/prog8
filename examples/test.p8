%import textio
%zeropage basicsafe


main {
    sub start() {
        ubyte @shared xx

        if xx<100 {
            foobar()
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
