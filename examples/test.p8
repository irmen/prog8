%import c64utils

~ main {

    sub start()  {

        ubyte ub1
        ubyte ub2
        byte b1
        byte b2
        uword uw1
        uword uw2
        word w1
        word w2

        memory ubyte mub1 = $c000
        memory ubyte mub2 = $c001
        memory uword muw1 = $c100
        memory uword muw2 = $c102

        ubyte[3] uba1
        byte[3] ba1
        uword[3] uwa1
        word[3] wa1
        ubyte[3] uba2
        byte[3] ba2
        uword[3] uwa2
        word[3] wa2

;        ub1 = ub2 & 44
;        b1 = b2 & 44
;        uw1 = uw2 & 4444
;        w1 = w2 & 4444
;        ub1 = ub2 | 44
;        b1 = b2 | 44
;        uw1 = uw2 | 4444
;        w1 = w2 | 4444
;        ub1 = ub2 ^ 44
;        b1 = b2 ^ 44
;        uw1 = uw2 ^ 4444
;        w1 = w2 ^ 4444
;
;        ub1 = ub2 & ub1
;        b1 = b2 & b1
;        uw1 = uw2 & uw1
;        w1 = w2 & w1
;        ub1 = ub2 | ub1
;        b1 = b2 | b1
;        uw1 = uw2 | uw1
;        w1 = w2 | w1
;        ub1 = ub2 ^ ub1
;        b1 = b2 ^ b1
;        uw1 = uw2 ^ uw1
;        w1 = w2 ^ w1

        swap(ub1, ub2)
        swap(b1, b2)
        swap(uw1, uw2)
        swap(w1, w2)
        swap(@($d020), @($d021))
        swap(mub1, mub2)
        swap(muw1, muw2)
        swap(mub1, ub2)
        swap(muw1, uw2)

        swap(uba1[1], uba2[2])
        swap(ba1[1], ba2[2])
        swap(uwa1[1], uwa2[2])
        swap(wa1[1], wa2[2])

        ubyte i1
        ubyte i2
        swap(uba1[i1], uba2[i2])
        swap(ba1[i1], ba2[i2])
        swap(uwa1[i1], uwa2[i2])
        swap(wa1[i1], wa2[i2])

        swap(uba1[1], ub1)
        swap(uba1[i1], ub1)
        swap(uwa1[1], uw1)
        swap(uwa1[i1], uw1)
    }


}

