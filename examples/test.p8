%import floats
%import textio

main {
    sub start() {
        simpleptrindexing()

;;        struct List {
;;            float f
;;            ^^uword s
;;            ^^float fp
;;            uword n
;;        }
;;        ^^List  l = List()
;;        l.s = 2000
;;        l.fp = 3000
;;
;;        pokew(2000, 1)
;;        pokew(2002, 2)
;;        pokew(2004, 3)
;;        pokew(2006, 4)
;;        pokew(2008, 5)
;;        pokef(3000, 1.111)
;;        pokef(3008, 2.222)
;;        pokef(3016, 3.333)
;;        pokef(3024, 4.444)
;;        pokef(3032, 5.555)
;;
;;        cx16.r9L = 2
;;
;;        lvref1()
;;        lvref2()
;;        lvref1f()
;;        lvref2f()
;;
;;        ref1()
;;        ref2()
;;        ref1f()
;;        ref2f()
;
;        sub lvref1() {
;            l.s[2] = 3333
;        }
;        sub lvref2() {
;            l.s[cx16.r9L+1] = 4444
;        }
;        sub lvref1f() {
;            l.fp[2] = 3333.3
;        }
;        sub lvref2f() {
;            l.fp[cx16.r9L+1] = 4444.4
;        }
;
;        sub ref1() {
;            cx16.r0 = l.s[2]
;            txt.print_uw(l.s[2])
;            txt.nl()
;        }
;        sub ref2() {
;            cx16.r1 = l.s[cx16.r9L+1]
;            txt.print_uw(l.s[cx16.r9L+1])
;            txt.nl()
;        }
;        sub ref1f() {
;            txt.print_f(l.fp[2])
;            txt.nl()
;        }
;        sub ref2f() {
;            txt.print_f(l.fp[cx16.r9L+1])
;            txt.nl()
;        }
    }


    sub simpleptrindexing() {
        ^^float flptr = 2000

;        flptr[0] = 0.0
;        flptr[1] = 1.1
        flptr[2] = 2.2      ;; TODO Fix wrong memory write

;        txt.print_f(flptr[0])
;        txt.nl()
;        txt.print_f(flptr[1])
;        txt.nl()
        txt.print_f(peekf(2000+8*2))
        txt.nl()
        txt.print_f(flptr[2])
        txt.nl()

        pokef(2000+8*2, 9.9999)
        txt.print_f(peekf(2000+8*2))
        txt.nl()
        txt.print_f(flptr[2])
        txt.nl()
    }

}

