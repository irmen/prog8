%import textio
%import floats
%import math
%zeropage basicsafe
%option no_sysinit

main {
    sub derp(str arg) {
        arg[4] = '?'
    }

    sub start() {
        str msg = "hello"
        derp(msg)
        txt.chrout(msg[4])              ; ?
        txt.nl()
        uword @shared az = $4000
        @($4004) = 0
        az[4] |= $40
        txt.print_ub(@($4004))          ; 64
        txt.nl()
        @(az+4) = 0
        @(az+4) |= $4f
        txt.print_ub(@($4004))          ; 79
        txt.nl()


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
