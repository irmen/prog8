%import textio
;%import floats
%zeropage basicsafe
%option no_sysinit

main {
    sub start() {

        bool @shared bb
        bool @shared bb2
;        ubyte[3]    cycle_reverseflags
;        bool[3]    cycle_reverseflags_b
;        str name="zzzz"
        ubyte @shared ubb=10
;        float @shared fl1 = 2.2
;        float @shared fl2 = 3.3

        bb=false
        bb2=true

        if bb and bb2
            txt.print("true")
        else
            txt.print("false")

        if not (bb and bb2)
            txt.print("true")
        else
            txt.print("false")

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
