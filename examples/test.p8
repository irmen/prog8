%import c64utils
%import c64flt

~ main {

    sub start()  {

        ubyte i = 10
        ubyte ub2
        byte j = 5
        byte b2
        uword uw = 1000
        uword uw2
        word w = 1000
        word w2
        float f1 = 1.1
        float f2 = 2.2

        b2 = j + (-1)
        b2 = (-1) + j
        b2 = j - (-1)
        b2 = (-1) -j ; should not be reordered
        b2 = j+1
        b2 = 1+j
        b2 = 1-j   ; should not be reordered

        j = j + (-1)
        j = (-1) + j
        j = j - (-1)
        j = (-1) -j ; should not be reordered
        j = j+1
        j = 1+j
        j = 1-j   ; should not be reordered
    }
}
