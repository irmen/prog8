%import textio
%import test_stack
%zeropage basicsafe
%option no_sysinit

main {
    uword[3] a
    uword[3] b
    ubyte @shared j
    uword @shared aa = 1

    sub start() {
        test_stack.test()
        j = 1
        a[j] = 1
        b[j] = 0
        b[j] += 5 * aa
        b[j] += 5 * aa
        b[j] += 5 * aa
        b[j] += 5 * aa
        txt.print_uw(b[j])      ; 20
        txt.nl()
        b[j] += 5 * a[1]
        b[j] += 5 * a[1]
        b[j] += 5 * a[1]
        b[j] += 5 * a[1]
        txt.print_uw(b[j])      ; 40
        txt.nl()
        b[j] += 5 * a[j]
        b[j] += 5 * a[j]
        b[j] += 5 * a[j]
        b[j] += 5 * a[j]
        txt.print_uw(b[j])      ; 60
        txt.nl()
        test_stack.test()
    }
}


;%import textio
;%import floats
;%zeropage basicsafe
;%option no_sysinit
;
;main {
;    sub start() {

;        uword  @shared addr = 2000
;        @(2000) = 199
;        txt.print_ub(@(2000))
;        txt.nl()
;        @(addr) = ~@(addr)
;        txt.print_ub(@(2000))
;        txt.nl()
;
;        word[3] @split @shared array = [1111,$10ff,3333]
;
;        txt.print_w(array[1])
;        txt.nl()
;        txt.print_w(-array[1])
;        txt.nl()
;        array[1] = -array[1]
;        txt.print_w(array[1])
;        txt.nl()
;        txt.nl()
;
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
;    }
;}
