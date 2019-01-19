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

        ubyte[3] uba
        byte[3] ba
        uword[3] uwa
        word[3] wa

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

        swap(uba[0], uba[1])
        swap(ba[0], ba[1])
        swap(uwa[0], uwa[1])
        swap(wa[0], wa[1])

        ; this goes without xor trick:
        ubyte i1
        ubyte i2
        swap(uba[i1], uba[i2])
        swap(ba[i1], ba[i2])
        swap(uwa[i1], uwa[i2])
        swap(wa[i1], wa[i2])

        swap(uba[1], ub1)
        swap(uba[i1], ub1)
        swap(uwa[1], uw1)
        swap(uwa[i1], uw1)
    }


}

