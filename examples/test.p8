%import textio
%import math
%import floats
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {

        ubyte[3]    cycle_reverseflags
        bool[3]    cycle_reverseflags_b
        bool @shared bb
        str name="zzzz"
        ubyte @shared ubb
        float @shared fl1 = 2.2
        float @shared fl2 = 3.3

        if ubb==42
            goto $1000


;
;        ubyte @shared flags=2
;        bool @shared b1
;        bool @shared b2
;
;        cycle_reverseflags[1]= b1 and b2 ; flags & 2 != 0 as bool

    }
}



;%import textio
;%import string
;%zeropage basicsafe
;%option no_sysinit

;main {
;    sub start() {
;        ubyte[3]    cycle_reverseflags
;
;        ubyte @shared flags=2
;
;        cycle_reverseflags[1]= flags & 2 != 0
;    }
;}


;%import textio
;%zeropage basicsafe
;%option no_sysinit
;
;main {
;    sub start() {
;
;        ubyte @shared ub
;        byte @shared bb
;
;        if ub&1!=0
;            cx16.r0++
;        if (bb as ubyte)&1!=0
;            cx16.r0++
;
;;        bool[10] barray = true
;;        barray[9] = false
;;        cx16.r0L = 0
;;
;;        while barray[cx16.r0L] {
;;            cx16.r0L++
;;        }
;;        txt.print_ub(cx16.r0L)
;    }
;}
