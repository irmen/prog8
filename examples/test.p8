%import textio
%import conv
%import strings
%zeropage basicsafe


main {
    ; Test the routine
    sub testsgn() {
        byte @shared b1 = -100
        word @shared w1 = -1000
        long @shared l1 = -1000000

        cx16.r0sL = sgn(b1)
        cx16.r1sL = sgn(w1)
        cx16.r2sL = sgn(l1)
    }

    sub start() {
        testsgn()

        long @shared lv
        uword @shared wv
        ubyte @shared bv

        txt.print_l(0)  ; value
        txt.spc()
        txt.print_uw(0)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(0)) ; calculated result
        txt.spc()
        lv = 0
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(55555555)  ; value
        txt.spc()
        txt.print_uw(7453)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(55555555)) ; calculated result
        txt.spc()
        lv = 55555555
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        ; Additional test cases for square root
        txt.print_l(1)  ; value
        txt.spc()
        txt.print_uw(1)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(1)) ; calculated result
        txt.spc()
        lv = 1
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(4)  ; value
        txt.spc()
        txt.print_uw(2)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(4)) ; calculated result
        txt.spc()
        lv = 4
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(9)  ; value
        txt.spc()
        txt.print_uw(3)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(9)) ; calculated result
        txt.spc()
        lv = 9
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(16)  ; value
        txt.spc()
        txt.print_uw(4)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(16)) ; calculated result
        txt.spc()
        lv = 16
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(25)  ; value
        txt.spc()
        txt.print_uw(5)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(25)) ; calculated result
        txt.spc()
        lv = 25
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(36)  ; value
        txt.spc()
        txt.print_uw(6)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(36)) ; calculated result
        txt.spc()
        lv = 36
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(49)  ; value
        txt.spc()
        txt.print_uw(7)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(49)) ; calculated result
        txt.spc()
        lv = 49
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(64)  ; value
        txt.spc()
        txt.print_uw(8)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(64)) ; calculated result
        txt.spc()
        lv = 64
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(81)  ; value
        txt.spc()
        txt.print_uw(9)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(81)) ; calculated result
        txt.spc()
        lv = 81
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(100)  ; value
        txt.spc()
        txt.print_uw(10)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(100)) ; calculated result
        txt.spc()
        lv = 100
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(121)  ; value
        txt.spc()
        txt.print_uw(11)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(121)) ; calculated result
        txt.spc()
        lv = 121
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(144)  ; value
        txt.spc()
        txt.print_uw(12)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(144)) ; calculated result
        txt.spc()
        lv = 144
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(255)  ; value
        txt.spc()
        txt.print_uw(15)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(255)) ; calculated result
        txt.spc()
        lv = 255
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(256)  ; value
        txt.spc()
        txt.print_uw(16)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(256)) ; calculated result
        txt.spc()
        lv = 256
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(1024)  ; value
        txt.spc()
        txt.print_uw(32)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(1024)) ; calculated result
        txt.spc()
        lv = 1024
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(65536)  ; value
        txt.spc()
        txt.print_uw(256)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(65536)) ; calculated result
        txt.spc()
        lv = 65536
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(1000000)  ; value
        txt.spc()
        txt.print_uw(1000)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(1000000)) ; calculated result
        txt.spc()
        lv = 1000000
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(16777216)  ; value
        txt.spc()
        txt.print_uw(4096)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(16777216)) ; calculated result
        txt.spc()
        lv = 16777216
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(2147483647)  ; value
        txt.spc()
        txt.print_uw(46340)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(2147483647)) ; calculated result
        txt.spc()
        lv = 2147483647
        txt.print_uw(sqrt(lv)) ; calculated result
        txt.nl()

        txt.print_l(4294967295)  ; value
        txt.spc()
        txt.print_uw(65535)  ; expected result
        txt.spc()
        txt.print_uw(prog8_lib.sqrt_long(4294967295)) ; calculated result
        txt.nl()

;        test_b()
;        test_w()
;        test_long()
;
;        sub test_b() {
;            bv = 99
;            txt.print_ub(min(bv, 200))
;            txt.spc()
;            bv = min(bv, 200)
;            txt.print_ub(bv)
;            txt.nl()
;        }
;
;        sub test_w() {
;            wv = 22211
;            txt.print_uw(min(wv, 55555))
;            txt.spc()
;            wv = min(wv, 55555)
;            txt.print_uw(wv)
;            txt.nl()
;        }
;
;        sub test_long() {
;            lv = 55551111
;            txt.print_uw(min(lv, 88888888))
;            txt.spc()
;            lv = min(lv, 88888888)
;            txt.print_uw(lv)
;            txt.nl()
;        }
    }
}

