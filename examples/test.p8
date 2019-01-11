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

        i %= 1
        i %= 2
        ub2 = i % 1
        ub2 = i % 2

        uw %= 1
        uw %= 2
        uw2 = uw % 1
        uw2 = uw % 2
    }
}
