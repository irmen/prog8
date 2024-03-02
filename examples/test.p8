%import string
%import textio
%option no_sysinit
%zeropage basicsafe


main {
    sub start() {
        bool @shared ba1, ba2, ba3, ba4, bb1, bb2, bb3, bb4
        ba1 = cx16.r0L >= 128
        ba2 = cx16.r0L >= cx16.r1L
        ba3 = cx16.r0L >= @($2000)
        ba4 = cx16.r0L >= @(cx16.r1)
        bb1 = cx16.r0L < 128
        bb2 = cx16.r0L < cx16.r1L
        bb3 = cx16.r0L < @($2000)
        bb4 = cx16.r0L < @(cx16.r1)


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

