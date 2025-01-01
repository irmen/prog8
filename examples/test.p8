%import textio
%option no_sysinit
%zeropage basicsafe

main {
    sub start() {
        ubyte bb, index
        uword ww
        index=2

        ww = 1234
        cx16.r0=10000

        cx16.r0 -= ww
        txt.print_uw(cx16.r0)            ; 7532
        txt.nl()
        cx16.r0 += ww
        txt.print_uw(cx16.r0)            ; 10000
        txt.nl()
        cx16.r0 &= ww
        txt.print_uw(cx16.r0)            ; 10000
        txt.nl()

;        bb += tabb[index]
;        bb -= index
;        bb -= tabb[index]
;        bb &= tabb[index]
;        bb |= tabb[index]
;        bb ^= tabb[index]
;        bb *= tabb[index]
;
;        cx16.r0L = bb + tabb[index]
;        cx16.r1L = bb - index
;        cx16.r2L = bb - tabb[index]
;        cx16.r3L = bb & tabb[index]
;        cx16.r4L = bb | tabb[index]
;        cx16.r5L = bb ^ tabb[index]
;        cx16.r6L = bb * tabb[index]

;        ww += tabw[index]
;        ww -= tabw[index]
;        ww &= tabw[index]
;        ww |= tabw[index]
;        ww ^= tabw[index]
;        ww *= tabw[index]
;
;        cx16.r0 = ww + tabw[index]
;        cx16.r1 = ww - cx16.r0
;        cx16.r2 = ww - tabw[index]
;        cx16.r3 = ww & tabw[index]
;        cx16.r4 = ww | tabw[index]
;        cx16.r5 = ww ^ tabw[index]
;        cx16.r6 = ww * tabw[index]

        ubyte[] tabb = [11,22,33,44,55,66]
        uword[] tabw = [111,222,333,444,555,666]
    }
}
