%import textio
%import string
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {
        ubyte[3]    cycle_reverseflags

        ubyte @shared flags=2

        cycle_reverseflags[1]= flags & 2 != 0
    }
}


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
