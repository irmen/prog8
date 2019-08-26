%zeropage basicsafe

main {

    sub start() {
        ubyte[] ubarr = [22,33,44,55,66]
        byte[] barr = [22,-33,-44,55,66]
        ubyte endub1
        byte endb1
        ubyte aa
        ubyte ub
        byte bb
        uword uw
        word total
        uword count


        ; ---------- BYTE var ---------

        count = 0
        total = 0
        c64scr.print("byte var in arrayliteral: ")
        for bb in [1,3,5,99] {
            count++
            total += bb
        }
        if count==4 and total==108
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")


        ; ---------- WORD var ---------

        word[] warr = [-111,222,-333,444]
        word endw1
        word ww

        count = 0
        total = 0
        c64scr.print("word var in arrayliteral: ")
        for ww in [1111,3333,555,999] {
            count++
            total += ww
        }
        if count==4 and total==5998
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

    }
}
