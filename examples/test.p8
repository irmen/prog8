%import textio
%import floats
%import math
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ; ubyte[10] az
        ubyte @shared offset=4
        uword @shared az = $4000
        ubyte @shared value = 22

        cx16.r0H = value*4 + az[value]
        cx16.r0L = az[value] + value*4

;        @($4004) = 99
;        az[4]--
;        @(az + offset)--
;        txt.print_ub(@($4004))
;        txt.nl()
;        az[4]++
;        @(az+offset)++
;        txt.print_ub(@($4004))
;        txt.nl()
;        cx16.r0L = az[4] + value*5
;        cx16.r1L = value*5 + az[4]

;        ubyte @shared idx = 1
;        txt.print_w(array[idx])
;        txt.nl()
;        txt.print_w(-array[idx])
;        txt.nl()
;        array[idx] = -array[idx]
;        txt.print_w(array[idx])
;        txt.nl()
;

;        ubyte @shared xx
;        ubyte[3] ubarr
;        uword[3] @split uwarr
;        byte[3] sbarr
;        bool[3] barr
;        float[3] flarr
;        bool @shared bb
;        uword ptr = &ubarr
;
;        ptr[1]++
;        ptr[1]++
;        ptr[1]--
;        txt.print_ub(ubarr[1])
;        txt.nl()
;        ptr[1]+=4
;        ptr[1]-=3
;        txt.print_ub(ubarr[1])
;        txt.nl()

;        sbarr[1] = sbarr[1] == 0
;        sbarr[1] = sbarr[1] != 0
;        sbarr[1] = sbarr[1] < 0
;        sbarr[1] = sbarr[1] <= 0
;        sbarr[1] = sbarr[1] > 0
;        sbarr[1] = sbarr[1] >= 0
;
;        xx = 1
;
;        sbarr[xx] = sbarr[xx] == 0
;        sbarr[xx] = sbarr[xx] != 0
;        sbarr[xx] = sbarr[xx] < 0
;        sbarr[xx] = sbarr[xx] <= 0
;        sbarr[xx] = sbarr[xx] > 0
;        sbarr[xx] = sbarr[xx] >= 0

;        sbarr[1] = sbarr[1] == 2
;        sbarr[1] = sbarr[1] != 2
;        sbarr[1] = sbarr[1] < 2
;        sbarr[1] = sbarr[1] <= 2
;        sbarr[1] = sbarr[1] > 2
;        sbarr[1] = sbarr[1] >= 2
;        xx = 1
;        sbarr[xx] = sbarr[xx] == 2
;        sbarr[xx] = sbarr[xx] != 2
;        sbarr[xx] = sbarr[xx] < 2
;        sbarr[xx] = sbarr[xx] <= 2
;        sbarr[xx] = sbarr[xx] > 2
;        sbarr[xx] = sbarr[xx] >= 2

;        ubarr[1] = ubarr[1] == 2
;        ubarr[1] = ubarr[1] < 2
;        ubarr[1] = ubarr[1] <= 2
;        ubarr[1] = ubarr[1] > 3
;        ubarr[1] = ubarr[1] >= 3

;        barr[1] = barr[0] and barr[2]
;        barr[1] = barr[0] or barr[2]
;        barr[1] = barr[0] xor barr[2]
;        barr[1] = not barr[0]
;
;        ubarr[1] = 999
;        ubarr[1] = ubarr[1]==999
;        txt.print_uw(ubarr[1])
;
;        barr[1] = barr[1] and bb
;        barr[1] = barr[1] or bb
;        barr[1] = barr[1] xor bb
;
;        bb = bb and barr[1]
;        bb = bb or barr[1]
;        bb = bb xor barr[1]
;        bb = not bb
    }
}
