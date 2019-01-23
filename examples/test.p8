%import c64lib
%option enable_floats

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
        str hoi = "hoi"
        str hoi2 = "hoi2"

    }


}

