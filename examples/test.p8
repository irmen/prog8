%import c64lib


~ main {

    sub start()  {

        memset($0400+40*3, 40*8, 81)
        memsetw($0400+40*12, 8*40/2, $80a0)
        memset($0400, 20, 33)
        memcopy($0400, $0400+121, 20)


        return

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


        ub1=ub2*ub1

    }


}

