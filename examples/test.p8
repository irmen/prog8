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
        txt.print_l(mklong2($a000,$bbbb))
        txt.spc()
        txt.print_l(mklong(9,8,7,6))
        txt.nl()
        cx16.r8 = $a000
        cx16.r9 = $bbbb
        cx16.r2L = 9
        cx16.r3L = 8
        cx16.r4L = 7
        cx16.r5L = 6
        txt.print_l(mklong2(cx16.r8, cx16.r9))
        txt.spc()
        txt.print_l(mklong(cx16.r2L,cx16.r3L,cx16.r4L,cx16.r5L))
        txt.nl()

;        long[] array = [-1999888777, -999, 42, 0, 77, 123456, 999999999]
;        long xx
;        for xx in array {
;            txt.print_uw(msw(xx))
;            txt.spc()
;            txt.print_uw(lsw(xx))
;            txt.nl()
;        }
;        txt.nl()
;        array[2] = 0
;        array[3] = 222222222
;        array[4] = bignum
;        array[5]++
;        array[6]--
;
;        txt.print_l(-1999888777)
;        txt.spc()
;        txt.print_l(-999)
;        txt.spc()
;        txt.print_l(-42)
;        txt.spc()
;        txt.print_l(0)
;        txt.spc()
;        txt.print_l(bignum)
;        txt.nl()
;        txt.print_l(bignum2)
;        txt.nl()
;        txt.print_l(-bignum2)
;        txt.nl()
;        bignum2 = -bignum2
;        bignum2++
;        bignum2++
;        txt.print_l(bignum2)
;        txt.nl()
;        bignum2--
;        bignum2--
;        txt.print_l(bignum2)
;        txt.spc()
;        txt.print_l(bignum)
;        txt.nl()
;        txt.nl()
;        bignum2 += bignum
;        txt.print_l(bignum2)
;        txt.nl()
;        bignum2 -= bignum
;        txt.print_l(bignum2)
;        txt.nl()

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
