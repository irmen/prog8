%import c64lib


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


        A=$34
        Y=$34
        ub1=$33
        ub1=$34
        ub2=1
        ub2=2
        ub2=3
        ub2=4
        ub2=$34
        uw1=0
        uw1=1
        uw1=$0034
        w1=1
        w1=2
        w1=3
        w1=$0034


        if A>5 {
        A=$34
        Y=$34
        ub1=$33
        ub1=$34
        ub2=1
        ub2=2
        ub2=3
        ub2=4
        ub2=$34
        uw1=0
        uw1=1
        uw1=$0034
        w1=1
        w1=2
        w1=3
        w1=$0034

        } else {
        A=$34
        Y=$34
        ub1=$33
        ub1=$34
        ub2=1
        ub2=2
        ub2=3
        ub2=4
        ub2=$34
        uw1=0
        uw1=1
        uw1=$0034
        w1=1
        w1=2
        w1=3
        w1=$0034

        }

        while(true) {
        A=$34
        Y=$34
        ub1=$33
        ub1=$34
        ub2=1
        ub2=2
        ub2=3
        ub2=4
        ub2=$34
        uw1=0
        uw1=1
        uw1=$0034
        w1=1
        w1=2
        w1=3
        w1=$0034

        }

        repeat {
        A=$34
        Y=$34
        ub1=$33
        ub1=$34
        ub2=1
        ub2=2
        ub2=3
        ub2=4
        ub2=$34
        uw1=0
        uw1=1
        uw1=$0034
        w1=1
        w1=2
        w1=3
        w1=$0034

        } until(true)

        A=$34
        Y=$34
        ub1=$33
        ub1=$34
        ub2=1
        ub2=2
        ub2=3
        ub2=4
        ub2=$34
        uw1=0
        uw1=1
        uw1=$0034
        w1=1
        w1=2
        w1=3
        w1=$0034

    }


}

