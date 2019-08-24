%zeropage basicsafe

; TODO fix compiler errors:
; for bb in [1,2,3] -> byte loop variable can only loop over bytes
; for ww in [1111,3333,555,999] -> word loop variable can only loop over bytes or words
; for uw in 20 to 10 step -1   ->  'can't cast BYTE into UWORD'

main {

    sub start() {
        ubyte[] ubarr = [22,33,44,55,66]
        byte[] barr = [22,-33,-44,55,66]
        ubyte endub1
        byte endb1
        uword count
        ubyte aa
        ubyte ub
        byte bb
        word total


        for count in 10 to 20 step 1 {
        }
        for count in 20 to 10 step -1 {     ; @todo fix compiler error
        }

        ; ---------- BYTE var ---------

        ; @todo fix  byte loop in arrayliteral 'Error: byte loop variable can only loop over bytes'
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

        ; @todo fix compiler error
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
