%import string
%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {

        uword[] flakes = [1,2,3,4]

        if cx16.r0==5555
            cx16.r0L++
        if cx16.r0!=5555
            cx16.r0L++

        if max(cx16.r0, cx16.r1)==9999
            cx16.r0L++

        if max(cx16.r0, cx16.r1)!=9999
            cx16.r0L++

        if derp()==1000
            cx16.r0L++

        if derp()!=1000
            cx16.r0L++

        cx16.r0=2
        if flakes[cx16.r0L]==239
            cx16.r0L++

        if flakes[cx16.r0L]!=239
            cx16.r0L++

        sub derp() -> uword {
            cx16.r0++
            return cx16.r0
        }

;
;        ubyte[] barray = [11,22,33]
;        uword[] warray = [1111,2222,3333]
;
;        if any(barray)
;            cx16.r0++
;
;
;        if barray[2] == 33
;            cx16.r0++
;        else
;            cx16.r1++
;
;        if warray[2] == 3333
;            cx16.r0++
;        else
;            cx16.r1++
;
;        if barray[cx16.r0L] == 33
;            cx16.r0++
;        else
;            cx16.r1++
;
;        if warray[cx16.r0L] == 3333
;            cx16.r0++
;        else
;            cx16.r1++
    }
}

