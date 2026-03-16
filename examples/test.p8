%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ; unsigned word remainder: 5555 % 44 = 11
        txt.print("uword: 5555%44 = ")
        uword @shared w1 = 5555
        uword @shared w2 = 44
        uword @shared rem = w1 % w2
        txt.print_uw(rem)
        txt.print("  (expected: 11)")
        txt.nl()

        ; test with signed word values: -1000 % 7 = -6
        txt.print("word: -1000%7 = ")
        word @shared sw1 = -1000
        word @shared sw2 = 7
        word @shared srem = sw1 % sw2
        txt.print_w(srem)
        txt.print("  (expected: -6)")
        txt.nl()

        ; test with signed byte values: -50 % 7 = -1
        txt.print("byte: -50%7 = ")
        byte @shared sb1 = -50
        byte @shared sb2 = 7
        byte @shared brem = sb1 % sb2
        txt.print_b(brem)
        txt.print("  (expected: -1)")
        txt.nl()

        ; unsigned byte remainder: 200 % 30 = 20
        txt.print("ubyte: 200%30 = ")
        ubyte @shared ub1 = 200
        ubyte @shared ub2 = 30
        ubyte @shared urem = ub1 % ub2
        txt.print_ub(urem)
        txt.print("  (expected: 20)")
        txt.nl()

        ; unsigned byte remainder: 45 % 7 = 3
        txt.print("ubyte: 45%7 = ")
        ubyte @shared ub3 = 45
        ubyte @shared ub4 = 7
        ubyte @shared urem2 = ub3 % ub4
        txt.print_ub(urem2)
        txt.print("  (expected: 3)")
        txt.nl()

        ; signed word with negative divisor: 100 % -8 = 4
        txt.print("word: 100%-8 = ")
        word @shared sw3 = 100
        word @shared sw4 = -8
        word @shared srem2 = sw3 % sw4
        txt.print_w(srem2)
        txt.print("  (expected: 4)")
        txt.nl()

        ; signed byte with negative divisor: 50 % -7 = 1
        txt.print("byte: 50%-7 = ")
        byte @shared sb3 = 50
        byte @shared sb4 = -7
        byte @shared brem2 = sb3 % sb4
        txt.print_b(brem2)
        txt.print("  (expected: 1)")
        txt.nl()

        ; zero dividend (unsigned word): 0 % 5 = 0
        txt.print("uword: 0%5 = ")
        uword @shared wz1 = 0
        uword @shared wz2 = 5
        uword @shared zrem = wz1 % wz2
        txt.print_uw(zrem)
        txt.print("  (expected: 0)")
        txt.nl()

        ; zero divisor (unsigned byte): 10 % 0 = 0
        txt.print("ubyte: 10%0 = ")
        ubyte @shared ubz1 = 10
        ubyte @shared ubz2 = 0
        ubyte @shared uremz = ubz1 % ubz2
        txt.print_ub(uremz)
        txt.print("  (expected: 0)")
        txt.nl()

        ; zero divisor (unsigned word): 1000 % 0 = 0
        txt.print("uword: 1000%0 = ")
        uword @shared wzd1 = 1000
        uword @shared wzd2 = 0
        uword @shared wremz = wzd1 % wzd2
        txt.print_uw(wremz)
        txt.print("  (expected: 0)")
        txt.nl()

        ; test with const args (signed word)
        txt.print("word const: -1000%7 = ")
        word @shared csrem = -1000 % 7
        txt.print_w(csrem)
        txt.print("  (expected: -6)")
        txt.nl()

        ; test with const args (signed byte)
        txt.print("byte const: -50%7 = ")
        byte @shared crem = -50 % 7
        txt.print_b(crem)
        txt.print("  (expected: -1)")
        txt.nl()

        ; augmented assignment %=
        ; unsigned word %=: 5555 %= 44  (should be 11)
        txt.print("uword: 5555%=44 = ")
        uword @shared wrem1 = 5555
        cx16.r0++
        wrem1 %= 44
        txt.print_uw(wrem1)
        txt.print("  (expected: 11)")
        txt.nl()

        ; unsigned byte %=: 200 %= 30  (should be 20)
        txt.print("ubyte: 200%=30 = ")
        ubyte @shared ubrem1 = 200
        cx16.r0++
        ubrem1 %= 30
        txt.print_ub(ubrem1)
        txt.print("  (expected: 20)")
        txt.nl()

        ; unsigned byte %=: 45 %= 7  (should be 3)
        txt.print("ubyte: 45%=7 = ")
        ubyte @shared ubrem2 = 45
        cx16.r0++
        ubrem2 %= 7
        txt.print_ub(ubrem2)
        txt.print("  (expected: 3)")
        txt.nl()

        ; signed word %=: -1000 %= 7  (should be -6)
        txt.print("word: -1000%=7 = ")
        word @shared swrem1 = -1000
        cx16.r0++
        swrem1 %= 7
        txt.print_w(swrem1)
        txt.print("  (expected: -6)")
        txt.nl()

        ; signed byte %=: -50 %= 7  (should be -1)
        txt.print("byte: -50%=7 = ")
        byte @shared sbrem1 = -50
        cx16.r0++
        sbrem1 %= 7
        txt.print_b(sbrem1)
        txt.print("  (expected: -1)")
        txt.nl()

        ; signed word with negative divisor %=: 100 %= -8  (should be 4)
        txt.print("word: 100%=-8 = ")
        word @shared swrem2 = 100
        cx16.r0++
        swrem2 %= -8
        txt.print_w(swrem2)
        txt.print("  (expected: 4)")
        txt.nl()

        ; signed byte with negative divisor %=: 50 %= -7  (should be 1)
        txt.print("byte: 50%=-7 = ")
        byte @shared sbrem2 = 50
        cx16.r0++
        sbrem2 %= -7
        txt.print_b(sbrem2)
        txt.print("  (expected: 1)")
        txt.nl()
    }
}

;%import textio
;%zeropage basicsafe
;%option no_sysinit
;
;main {
;    sub start() {
;        routine()
;        routine()
;        check()
;    }
;
;    sub check() {
;        ubyte tmp_x
;        ubyte tmp_y
;        ubyte tmp_z
;        tmp_x = cx16.r0L
;        tmp_y = cx16.r1L
;        tmp_z = cx16.r2L
;        tmp_x++
;        tmp_y++
;        tmp_z++
;    }
;
;    sub routine() {
;        ubyte @nozp @shared local_nozp =44     ; set to 0 at subroutine entry (STZ) - redundant because of initialization below
;        ubyte @zp @shared local_zp, local_zp2, zp99          ; set to 0 at subroutine entry (STZ) - redundant because of initialization below
;        ubyte @zp @shared local_zp3         ; set to 0 at subroutine entry (STZ) - redundant because of initialization below
;
;        ; do some stuff that doesn't touch the variables
;        cx16.r0++
;        zp99++
;        repeat 99 cx16.r1++
;        swap(cx16.r0L, local_zp2)       ; ... except zp2 here - so that one should remain zeroed out initially
;        for cx16.r0L in 0 to 10 {
;            cx16.r1++
;        }
;
;
;        local_nozp = 11
;        local_zp = 22
;        local_zp2 = 33
;        local_zp3, void = multi()
;
;        txt.print_ub(local_nozp)
;        txt.spc()
;        txt.print_ub(local_zp)
;        txt.spc()
;        txt.print_ub(local_zp2)
;        txt.nl()
;
;        local_nozp++
;        local_zp++
;        local_zp2++
;
;        txt.print_ub(local_nozp)
;        txt.spc()
;        txt.print_ub(local_zp)
;        txt.spc()
;        txt.print_ub(local_zp2)
;        txt.spc()
;        txt.nl()
;    }
;
;    sub multi() -> ubyte, ubyte {
;        return cx16.r0L, cx16.r1L
;    }
;}
