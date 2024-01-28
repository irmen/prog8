%import textio
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte @shared index = 1

;        @(2000) = 99
;        uword @shared ptr = 2000
;        txt.print_ub(@(2000))
;        txt.nl()
;        @(2000) ++
;        @(2000) ++
;        @(2000) --
;        txt.print_ub(@(2000))
;        txt.nl()


        uword[3] @split arr

        arr[1] = 9999
        txt.print_uw(arr[1])
        txt.nl()
        arr[1] = arr[1]*5
        cx16.r0=2222
        arr[1] *= cx16.r0
        arr[1] -=5
        arr[1] -=index
        txt.print_uw(arr[1])
        txt.nl()

;        arr[index] = 9999
;        txt.print_uw(arr[index])
;        txt.nl()
;        arr[index] += 5
;        arr[index] += 5
;        arr[index] -= 5
;        txt.print_uw(arr[index])
;        txt.nl()


;
;        ubyte @shared xx
;        uword[3] ubarr
;        bool[3] barr
;        float[3] flarr
;        bool @shared bb
;
;        ubarr[1] = ubarr[1] < 2
;        ubarr[1] = ubarr[1] <= 2
;        ubarr[1] = ubarr[1] > 3
;        ubarr[1] = ubarr[1] >= 3

;        barr[1] = barr[0] and barr[2]
;        barr[1] = barr[0] or barr[2]
;        barr[1] = barr[0] xor barr[2]
;        barr[1] = not barr[0]

;        ubarr[1] = 999
;        ubarr[1] = ubarr[1]==999
;        txt.print_uw(ubarr[1])

;        barr[1] = barr[1] and bb
;        barr[1] = barr[1] or bb
;        barr[1] = barr[1] xor bb

;        bb = bb and barr[1]
;        bb = bb or barr[1]
;        bb = bb xor barr[1]
;        bb = not bb
    }
}
