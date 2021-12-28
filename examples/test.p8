%import textio
%zeropage basicsafe


main {
    sub start() {
        ubyte @shared xx
        str name = "irmen"
        ubyte[] values = [1,2,3,4,5,6,7]

        for xx in name {
            txt.chrout(xx)
            txt.spc()
        }
        txt.nl()

        for xx in values {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()

        for xx in 10 to 20 step 3 {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()

        for xx in "abcdef" {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()

        for xx in [2,4,6,8] {
            txt.print_ub(xx)
            txt.spc()
        }
        txt.nl()


;        if xx in 100 {          ; TODO error
;            xx++
;        }
;
;        if xx in 'a' {          ; TODO error
;            xx++
;        }
;
;        if xx in "abc" {         ; TODO containment test via when
;            xx++
;        }
;
;        if xx in [1,2,3,4,5] {    ; TODO containment test via when
;            xx++
;        }
;
;        if xx in [1,2,3,4,5,6,7,8,9,10] {    ; TODO containment test via loop?
;            xx++
;        }
;
;        if xx in name {         ; TODO containment test via loop
;            xx++
;        }
;
;        if xx in values {       ; TODO containment test via loop
;            xx++
;        }
;
;        if xx in 10 to 20 step 2 {      ; TODO
;
;        }

        ; TODO const optimizing of the containment tests
        ; TODO also with (u)word and floats



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
