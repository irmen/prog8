%import c64lib


~ main {

    sub start()  {

        ; memset($0400, $0400+40, 81)

        A=99
        if(A<99) goto first else goto second

first:
        c64scr.print("a<99 !\n")
        goto next
second:
        c64scr.print("wrong: a>=99 ?!\n")

next:
        A=99
        if(A<99) goto first2 else {
            c64scr.print("wrong: a>=99 ?!\n")
        }
        return

first2:
        c64scr.print("a<99 !\n")

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

