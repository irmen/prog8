%import textio
%zeropage basicsafe

main {
    sub start() {

        long @shared lv1, lv2
        bool b1,b2

        lv1 = $11223344
        lv2 = $33883388

        txt.print_bool(lv1==0)
        txt.spc()
        txt.print_bool(lv1==1)
        txt.spc()
        txt.print_bool(lv1==$11000000)
        txt.spc()
        txt.print_bool(lv1==$11223344)
        txt.nl()
        txt.print_bool(lv1==lv2)
        txt.spc()
        lv2 = lv1
        txt.print_bool(lv1==lv2)
        txt.nl()


        lv2 = $33883388
        txt.print_bool(lv1!=0)
        txt.spc()
        txt.print_bool(lv1!=1)
        txt.spc()
        txt.print_bool(lv1!=$11000000)
        txt.spc()
        txt.print_bool(lv1!=$11223344)
        txt.nl()
        txt.print_bool(lv1!=lv2)
        txt.spc()
        lv2 = lv1
        txt.print_bool(lv1!=lv2)
        txt.nl()

        txt.print_ulhex(lv1, false)
        txt.nl()
        lv1 = ~lv1
        txt.print_ulhex(lv1, false)
        txt.nl()

        lv1 = 999999
        txt.print_l(lv1<<3)
        txt.nl()
        cx16.r4sL = -55
        lv1 = 999999
        txt.print_l(lv1+cx16.r4sL)
        txt.nl()
        cx16.r4s = -5555
        lv1 = 999999
        txt.print_l(lv1+cx16.r4s)
        txt.nl()

        lv2 <<= cx16.r0L
        lv2 >>= cx16.r0L

        lv1 <<= 3
        lv2 <<= cx16.r0L
        lv1 >>= 3
        lv2 >>= cx16.r0L

        lv1 += cx16.r0L
        lv1 += cx16.r0

        lv1 |= cx16.r0L
        lv1 |= cx16.r0
        lv1 |= lv2
        lv1 &= cx16.r0L
        lv1 &= cx16.r0
        lv1 &= lv2
        lv1 ^= cx16.r0L
        lv1 ^= cx16.r0
        lv1 ^= lv2

        b1 = lv1 == cx16.r0L
        b1 = lv2 == cx16.r0sL
        b2 = lv1 == cx16.r0
        b1 = lv2 == cx16.r0s
        b2 = lv1 == lv2

        b1 = lv1 != cx16.r0L
        b2 = lv2 != cx16.r0sL
        b1 = lv1 != cx16.r0
        b2 = lv2 != cx16.r0s
        b1 = lv1 != lv2

        txt.print_l(mklong2($a000,$bbbb))
        txt.spc()
        txt.print_ulhex(mklong2($a000,$bbbb), true)
        txt.spc()
        txt.print_l(mklong(9,8,7,6))
        txt.spc()
        txt.print_ulhex(mklong(9,8,7,6), true)
        txt.nl()
        cx16.r8 = $a000
        cx16.r9 = $bbbb
        cx16.r2L = 9
        cx16.r3L = 8
        cx16.r4L = 7
        cx16.r5L = 6
        txt.print_l(mklong2(cx16.r8, cx16.r9))
        txt.spc()
        txt.print_ulhex(mklong2(cx16.r8, cx16.r9), true)
        txt.spc()
        txt.print_l(mklong(cx16.r2L,cx16.r3L,cx16.r4L,cx16.r5L))
        txt.spc()
        txt.print_ulhex(mklong(cx16.r2L,cx16.r3L,cx16.r4L,cx16.r5L), true)
        txt.nl()

        long @shared lv = 111111111
        lv2 = 1000000
        word @shared ww = 1000
        byte @shared bb = 1
        long @shared result = lv + lv2
        txt.print_l(result)
        txt.spc()
        result = lv + ww
        txt.print_l(result)
        txt.spc()
        result = lv + bb
        txt.print_l(result)
        txt.nl()
        txt.print_l(lv)
        txt.spc()
        txt.print_l(lv + 1)
        txt.spc()
        txt.print_l(lv + 1000)
        txt.spc()
        txt.print_l(lv + 1000000)
        txt.nl()

        long[] array = [-1999888777, -999, 42, 0, 77, 123456, 999999999]
        long xx
        for xx in array {
            txt.print_uw(msw(xx))
            txt.spc()
            txt.print_uw(lsw(xx))
            txt.nl()
        }
        txt.nl()
        array[2] = 0
        array[3] = 222222222
        array[4] = lv1
        array[5]++
        array[6]--
    }
}
