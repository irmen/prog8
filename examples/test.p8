%import c64utils

~ main {

    sub start()  {

        ubyte ub=20
        ubyte ub2
        byte b =-10
        byte b2
        uword uw = 2000
        uword uw2
        word w = -222
        word w2

        A>>=1
        A>>=3
        A<<=1
        A<<=3
        lsr(A)
        lsl(A)

        ub2 = ub>>1
        ub2 = ub>>2
        ub2 = ub<<1
        ub2 = ub<<2
        b2 = b>>1
        b2 = b>>2
        b2 = b<<1
        b2 = b<<2
        uw2 = uw>>1
        uw2 = uw>>2
        uw2 = uw<<1
        uw2 = uw<<2
        w2 = w>>1
        w2 = w>>2
        w2 = w<<1
        w2 = w<<2

        lsr(ub)
        lsr(b)
        lsr(uw)
        lsr(w)
        lsl(ub)
        lsl(b)
        lsl(uw)
        lsl(w)
        rol(ub)
        rol(uw)
        rol2(ub)
        rol2(uw)
        ror(ub)
        ror(uw)
        ror2(ub)
        ror2(uw)

        ;c64scr.print_ub(X)
        ;c64.CHROUT('\n')
    }
}
