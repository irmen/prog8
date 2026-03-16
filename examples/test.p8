%import textio
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ; unsigned word divmod: 5555 / 44 = 126 remainder 11
        txt.print("uword: 5555/44 = ")
        uword @shared w1 = 5555
        uword @shared w2 = 44
        uword division, remainder = divmod(w1, w2)
        txt.print_uw(division)
        txt.spc()
        txt.print_uw(remainder)
        txt.print("  (expected: 126 11)")
        txt.nl()

        ; test with signed word values: -1000 / 7 = -142 remainder -6
        txt.print("word: -1000/7 = ")
        word @shared sw1 = -1000
        word @shared sw2 = 7
        word sdiv, srem = divmod(sw1, sw2)
        txt.print_w(sdiv)
        txt.spc()
        txt.print_w(srem)
        txt.print("  (expected: -142 -6)")
        txt.nl()

        ; test with signed byte values: -50 / 7 = -7 remainder -1
        txt.print("byte: -50/7 = ")
        byte @shared sb1 = -50
        byte @shared sb2 = 7
        byte bdiv, brem = divmod(sb1, sb2)
        txt.print_b(bdiv)
        txt.spc()
        txt.print_b(brem)
        txt.print("  (expected: -7 -1)")
        txt.nl()

        ; unsigned byte divmod: 200 / 30 = 6 remainder 20
        txt.print("ubyte: 200/30 = ")
        ubyte @shared ub1 = 200
        ubyte @shared ub2 = 30
        ubyte udiv, urem = divmod(ub1, ub2)
        txt.print_ub(udiv)
        txt.spc()
        txt.print_ub(urem)
        txt.print("  (expected: 6 20)")
        txt.nl()

        ; signed word with negative divisor: 100 / -8 = -12 remainder 4
        txt.print("word: 100/-8 = ")
        word @shared sw3 = 100
        word @shared sw4 = -8
        word sdiv2, srem2 = divmod(sw3, sw4)
        txt.print_w(sdiv2)
        txt.spc()
        txt.print_w(srem2)
        txt.print("  (expected: -12 4)")
        txt.nl()

        ; signed byte with negative divisor: 50 / -7 = -7 remainder 1
        txt.print("byte: 50/-7 = ")
        byte @shared sb3 = 50
        byte @shared sb4 = -7
        byte bdiv2, brem2 = divmod(sb3, sb4)
        txt.print_b(bdiv2)
        txt.spc()
        txt.print_b(brem2)
        txt.print("  (expected: -7 1)")
        txt.nl()

        ; zero dividend (unsigned word): 0 / 5 = 0 remainder 0
        txt.print("uword: 0/5 = ")
        uword @shared wz1 = 0
        uword @shared wz2 = 5
        uword zdiv, zrem = divmod(wz1, wz2)
        txt.print_uw(zdiv)
        txt.spc()
        txt.print_uw(zrem)
        txt.print("  (expected: 0 0)")
        txt.nl()

        ; zero divisor (unsigned byte): 10 / 0 = max remainder 0
        txt.print("ubyte: 10/0 = ")
        ubyte @shared ubz1 = 10
        ubyte @shared ubz2 = 0
        ubyte udivz, uremz = divmod(ubz1, ubz2)
        txt.print_ub(udivz)
        txt.spc()
        txt.print_ub(uremz)
        txt.print("  (expected: 255 0)")
        txt.nl()

        ; test with const args (signed word) - both args must be same type
        txt.print("word const: -1000/7 = ")
        word csdiv, csrem = divmod(-1000 as word, 7 as word)
        txt.print_w(csdiv)
        txt.spc()
        txt.print_w(csrem)
        txt.print("  (expected: -142 -6)")
        txt.nl()

        ; test with const args (signed byte) - both args must be same type  
        txt.print("byte const: -50/7 = ")
        byte cdiv, crem = divmod(-50 as byte, 7 as byte)
        txt.print_b(cdiv)
        txt.spc()
        txt.print_b(crem)
        txt.print("  (expected: -7 -1)")
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
