%import textio
%zeropage basicsafe

main {

    sub xx(long lv) {
        lv++
    }

    sub start() {
        long @shared lv = 999999
        long @shared lv2 = -999999
        long @shared lv3 = 9999

        lv += -99
        txt.print_l(lv)
        txt.nl()

        lv -= -99
        txt.print_l(lv)
        txt.nl()

        lv += -4999
        txt.print_l(lv)
        txt.nl()

        lv -= -4999
        txt.print_l(lv)
        txt.nl()


;        lv = lv2 + cx16.r0L
;        lv2 = lv + cx16.r0
;        xx(lv+cx16.r0L)
;        xx(lv+cx16.r0)

;        cx16.r0bL = lv2==lv
;        cx16.r1bL = lv2==lv
;        cx16.r2bL = lv2==lv
;        cx16.r0bL = lv2!=lv
;        cx16.r1bL = lv2!=lv
;        cx16.r2bL = lv2!=lv

;        xx(lv2 + lv)
;        xx(lv2 + lv)
;        xx(lv2 + lv)
;        xx(lv2 - lv)
;        xx(lv2 - lv)
;        xx(lv2 - lv)
;        xx(lv2 & lv)
;        xx(lv2 & lv)
;        xx(lv2 & lv)
;        xx(lv2 | lv)
;        xx(lv2 | lv)
;        xx(lv2 | lv)
;        xx(lv2 ^ lv)
;        xx(lv2 ^ lv)
;        xx(lv2 ^ lv)

;        lv3 = lv2 << 3
;        lv3 = lv2 << 3
;        lv3 = lv2 << 3
;        lv3 = lv2 >> 3
;        lv3 = lv2 >> 3
;        lv3 = lv2 >> 3
;
;        xx(lv2 << 3)
;        xx(lv2 << 3)
;        xx(lv2 << 3)
;        xx(lv2 >> 3)
;        xx(lv2 >> 3)
;        xx(lv2 >> 3)
    }
}
