%import textio
%zeropage basicsafe

main {
    long bignum = 12345678
    long bignum2 = -999999

;    struct Node {
;        ubyte id
;        str name
;        long array
;        bool flag
;        long counter
;    }

    sub start() {
        txt.print_l(-1999888777)
        txt.spc()
        txt.print_l(-999)
        txt.spc()
        txt.print_l(-42)
        txt.spc()
        txt.print_l(0)
        txt.spc()
        txt.print_l(bignum)
        txt.nl()
        txt.print_l(bignum2)
        txt.nl()
        txt.print_l(-bignum2)
        txt.nl()
        bignum2 = -bignum2
        bignum2++
        bignum2++
        txt.print_l(bignum2)
        txt.nl()
        bignum2--
        bignum2--
        txt.print_l(bignum2)
        txt.spc()
        txt.print_l(bignum)
        txt.nl()
        txt.nl()
        bignum2 += bignum
        txt.print_l(bignum2)
        txt.nl()
        bignum2 -= bignum
        txt.print_l(bignum2)
        txt.nl()

;        ^^Node test = []
;
;        bignum++
;        bignum2--

;        txt.print_l(bignum)
;        txt.spc()
;        txt.print_l(bignum2)
;        txt.nl()
;
;        str output = "...................."
;        txt.print_l(bignum)
;        txt.spc()
;        txt.print(conv.str_l(bignum))
;        txt.nl()
;
;        bignum = 999999
;        bignum--                        ; TODO this works in the current VM...
;        bignum = -888888
;
;        test.counter = 0
;        test.counter ++                 ; TODO ... why doesn't this? (requires plusMinusMultAnyLong routine)
;        test.counter = bignum2
;        test.counter --
    }

}
