%import textio
%zeropage basicsafe


main {
    struct Ptr {
        word value
        byte bvalue
    }

    sub start() {
        ^^Ptr pp = Ptr()
        uword @nozp plainptr = &pp.bvalue
        pp.value = -999
        pp.bvalue = -99
        txt.print_w(clamp(pp.value, 10, 2000))
        txt.spc()
        txt.print_b(clamp(pp.bvalue, 10, 100))
        txt.spc()
        pp.bvalue = 5
        txt.print_ub(clamp(plainptr[0], 10, 100))
        txt.nl()

        pp.value = 5000
        pp.bvalue = 120
        txt.print_w(clamp(pp.value, 10, 2000))
        txt.spc()
        txt.print_b(clamp(pp.bvalue, 10, 100))
        txt.spc()
        pp.bvalue = 120
        txt.print_ub(clamp(plainptr[0], 10, 100))
        txt.nl()

        pp.value = 1234
        pp.bvalue = 66
        txt.print_w(clamp(pp.value, 10, 2000))
        txt.spc()
        txt.print_b(clamp(pp.bvalue, 10, 100))
        txt.spc()
        pp.bvalue = 66
        txt.print_ub(clamp(plainptr[0], 10, 100))
        txt.nl()
    }

}

;%import floats
;
;main {
;    sub start() {
;        struct List {
;            uword s
;            float fl
;            uword n
;        }
;        ^^List  l = List()
;        l.n[cx16.r0L] = 99
;;        l.s[cx16.r0L+2] = 42
;;        l.n[cx16.r0L+2] = 99
;    }
;}
