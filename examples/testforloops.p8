%zeropage basicsafe

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

        c64scr.plot(0,24)

        ; ---------- REGISTER A ---------
        count = 0
        total = 0
        c64scr.print("a in string: ")
        for A in "hello" {
            aa=A
            count++
            total += aa
        }
        if count==5 and total==372
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("a in arrayliteral: ")
        for A in [1,3,5,99] {
            aa=A
            count++
            total += aa
        }
        if count==4 and total==108
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("a in arrayvar: ")
        for A in ubarr {
            aa=A
            count++
            total += aa
        }
        if count==5 and total==220
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("a in range step 1: ")
        for A in 10 to 20 {
            aa=A
            count++
            total += aa
        }
        if count==11 and total==165
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("a in range step -1: ")
        for A in 20 to 10 step -1 {
            aa=A
            count++
            total += aa
        }
        if count==11 and total==165
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("a in range step 3: ")
        for A in 10 to 21 step 3 {
            aa=A
            count++
            total += aa
        }
        if count==4 and total==58
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("a in rangeincl step 3: ")
        for A in 10 to 22 step 3 {
            aa=A
            count++
            total += aa
        }
        if count==5 and total==80
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("a in range step -3: ")
        for A in 24 to 10 step -3 {
            aa=A
            count++
            total += aa
        }
        if count==5 and total==90
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("a in rangeincl step -3: ")
        for A in 24 to 9 step -3 {
            aa=A
            count++
            total += aa
        }
        if count==6 and total==99
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endub1=101
        c64scr.print("a in ncrange step 1: ")
        for A in 95 to endub1 step 1 {
            aa=A
            count++
            total += aa
        }
        if count==7 and total==686
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endub1=101
        c64scr.print("a in ncrange step -1: ")
        for A in endub1 to 95 step -1 {
            aa=A
            count++
            total += aa
        }
        if count==7 and total==686
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endub1=105
        c64scr.print("a in ncrange step 3: ")
        for A in 95 to endub1 step 3 {
            aa=A
            count++
            total += aa
        }
        if count==4 and total==398
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endub1=105
        c64scr.print("a in ncrange step -3: ")
        for A in endub1 to 95 step -3 {
            aa=A
            count++
            total += aa
        }
        if count==4 and total==402
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endub1=107
        c64scr.print("a in ncrangeinc step 3: ")
        for A in 95 to endub1 step 3 {
            aa=A
            count++
            total += aa
        }
        if count==5 and total==505
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endub1=107
        c64scr.print("a in ncrangeinc step -3: ")
        for A in endub1 to 95 step -3 {
            aa=A
            count++
            total += aa
        }
        if count==5 and total==505
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        wait_input()

        ; ---------- UBYTE var ---------

        count = 0
        total = 0
        c64scr.print("ubyte var in string: ")
        for ub in "hello" {
            count++
            total += ub
        }
        if count==5 and total==372
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("ubyte var in arrayliteral: ")
        for ub in [1,3,5,99] {
            count++
            total += ub
        }
        if count==4 and total==108
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("ubyte var in arrayvar: ")
        for ub in ubarr {
            count++
            total += ub
        }
        if count==5 and total==220
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("ubyte var in range step 1: ")
        for ub in 10 to 20 {
            count++
            total += ub
        }
        if count==11 and total==165
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("ubyte var in range step -1: ")
        for ub in 20 to 10 step -1 {
            count++
            total += ub
        }
        if count==11 and total==165
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("ubyte var in range step 3: ")
        for ub in 10 to 21 step 3 {
            count++
            total += ub
        }
        if count==4 and total==58
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("ubyte var in rangeincl step 3: ")
        for ub in 10 to 22 step 3 {
            count++
            total += ub
        }
        if count==5 and total==80
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("ubyte var in range step -3: ")
        for ub in 24 to 10 step -3 {
            count++
            total += ub
        }
        if count==5 and total==90
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("ubyte var in rangeincl step -3: ")
        for ub in 24 to 9 step -3 {
            count++
            total += ub
        }
        if count==6 and total==99
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endub1=101
        c64scr.print("ubyte var in ncrange step 1: ")
        for ub in 95 to endub1 step 1 {
            count++
            total += ub
        }
        if count==7 and total==686
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endub1=101
        c64scr.print("ubyte var in ncrange step -1: ")
        for ub in endub1 to 95 step -1 {
            count++
            total += ub
        }
        if count==7 and total==686
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endub1=105
        c64scr.print("ubyte var in ncrange step 3: ")
        for ub in 95 to endub1 step 3 {
            count++
            total += ub
        }
        if count==4 and total==398
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endub1=105
        c64scr.print("ubyte var in ncrange step -3: ")
        for ub in endub1 to 95 step -3 {
            count++
            total += ub
        }
        if count==4 and total==402
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endub1=107
        c64scr.print("ubyte var in ncrangeinc step 3: ")
        for ub in 95 to endub1 step 3 {
            count++
            total += ub
        }
        if count==5 and total==505
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endub1=107
        c64scr.print("ubyte var in ncrangeinc step -3: ")
        for ub in endub1 to 95 step -3 {
            count++
            total += ub
        }
        if count==5 and total==505
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        wait_input()

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

        count = 0
        total = 0
        c64scr.print("byte var in arrayvar: ")
        for bb in barr {
            count++
            total += bb
        }
        if count==5 and total==66
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("byte var in range step 1: ")
        for bb in 10 to 20 {
            count++
            total += bb
        }
        if count==11 and total==165
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("byte var in range step -1: ")
        for bb in 20 to 10 step -1 {
            count++
            total += bb
        }
        if count==11 and total==165
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("byte var in range step 3: ")
        for bb in 10 to 21 step 3 {
            count++
            total += bb
        }
        if count==4 and total==58
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("byte var in rangeincl step 3: ")
        for bb in 10 to 22 step 3 {
            count++
            total += bb
        }
        if count==5 and total==80
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("byte var in range step -3: ")
        for bb in 24 to 10 step -3 {
            count++
            total += bb
        }
        if count==5 and total==90
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("byte var in rangeincl step -3: ")
        for bb in 24 to 9 step -3 {
            count++
            total += bb
        }
        if count==6 and total==99
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endb1=101
        c64scr.print("byte var in ncrange step 1: ")
        for bb in 95 to endb1 step 1 {
            count++
            total += bb
        }
        if count==7 and total==686
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endb1=101
        c64scr.print("byte var in ncrange step -1: ")
        for bb in endb1 to 95 step -1 {
            count++
            total += bb
        }
        if count==7 and total==686
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endb1=105
        c64scr.print("byte var in ncrange step 3: ")
        for bb in 95 to endb1 step 3 {
            count++
            total += bb
        }
        if count==4 and total==398
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endb1=105
        c64scr.print("byte var in ncrange step -3: ")
        for bb in endb1 to 95 step -3 {
            count++
            total += bb
        }
        if count==4 and total==402
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endb1=107
        c64scr.print("byte var in ncrangeinc step 3: ")
        for bb in 95 to endb1 step 3 {
            count++
            total += bb
        }
        if count==5 and total==505
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endb1=107
        c64scr.print("byte var in ncrangeinc step -3: ")
        for bb in endb1 to 95 step -3 {
            count++
            total += bb
        }
        if count==5 and total==505
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        wait_input()

        ; ---------- UWORD var ---------

        uword[] uwarr = [1111,2222,3330,4000]
        uword enduw1
        uword totaluw
        uword uw

        count = 0
        totaluw = 0
        c64scr.print("uword var in string: ")
        for uw in "hello" {
            count++
            totaluw += uw
        }
        if count==5 and totaluw==372
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        c64scr.print("uword var in arrayliteral: ")
        for uw in [1111,3333,555,999] {
            count++
            totaluw += uw
        }
        if count==4 and totaluw==5998
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        c64scr.print("uword var in arrayvar: ")
        for uw in uwarr {
            count++
            totaluw += uw
        }
        if count==4 and totaluw==10663
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        c64scr.print("uword var in range step 1: ")
        for uw in 1000 to 1100 {
            count++
            totaluw += uw
        }
        if count==101 and totaluw==40514
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        c64scr.print("uword var in range step -1: ")
        for uw in 2000 to 1500 step -1 {
            count++
            totaluw += uw
        }
        if count==501 and totaluw==24782
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        c64scr.print("uword var in range step 333: ")
        for uw in 1000 to 2200 step 333 {
            count++
            totaluw += uw
        }
        if count==4 and totaluw==5998
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        c64scr.print("uword var in rangeincl step 333: ")
        for uw in 1000 to 2332 step 333 {
            count++
            totaluw += uw
        }
        if count==5 and totaluw==8330
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        c64scr.print("uword var in range step -333: ")
        for uw in 17000 to 14500 step -333 {
            count++
            totaluw += uw
        }
        if count==8 and totaluw==61140
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        c64scr.print("uword var in rangeincl step -333: ")
        for uw in 17000 to 14336 step -333 {
            count++
            totaluw += uw
        }
        if count==9 and totaluw==9940
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        enduw1=17000
        c64scr.print("uword var in ncrange step 1: ")
        for uw in 16500 to enduw1 step 1 {
            count++
            totaluw += uw
        }
        if count==501 and totaluw==3142
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        c64scr.print("uword var in ncrange step -1: ")
        for uw in enduw1 to 16500 step -1 {
            count++
            totaluw += uw
        }
        if count==501 and totaluw==3142
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        enduw1=17000
        c64scr.print("uword var in ncrange step 333: ")
        for uw in 14500 to enduw1 step 333 {
            count++
            totaluw += uw
        }
        if count==8 and totaluw==59788
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        enduw1=17000
        c64scr.print("uword var in ncrange step -333: ")
        for uw in enduw1 to 14500 step -333 {
            count++
            totaluw += uw
        }
        if count==8 and totaluw==61140
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        enduw1=17164
        c64scr.print("uword var in ncrangeinc step 333: ")
        for uw in 14500 to enduw1 step 333 {
            count++
            totaluw += uw
        }
        if count==9 and totaluw==11416
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        totaluw = 0
        enduw1=17000
        c64scr.print("uword var in ncrangeinc step -333: ")
        for uw in enduw1 to 14336 step -333 {
            count++
            totaluw += uw
        }
        if count==9 and totaluw==9940
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        wait_input()

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

        count = 0
        total = 0
        c64scr.print("word var in arrayvar: ")
        for ww in warr {
            count++
            total += ww
        }
        if count==4 and total==222
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("word var in range step 1: ")
        for ww in -100 to 1000 {
            count++
            total += ww
        }
        if count==1101 and total==-28838
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("word var in range step -1: ")
        for ww in 1000 to -500 step -1 {
            count++
            total += ww
        }
        if count==1501 and total==-17966
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("word var in range step 333: ")
        for ww in -1000 to 2200 step 333 {
            count++
            total += ww
        }
        if count==10 and total==4985
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("word var in rangeincl step 333: ")
        for ww in -1000 to 2330 step 333 {
            count++
            total += ww
        }
        if count==11 and total==7315
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("word var in range step -333: ")
        for ww in 2000 to -2500 step -333 {
            count++
            total += ww
        }
        if count==14 and total==-2303
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("word var in rangeincl step -333: ")
        for ww in 2000 to -2662 step -333 {
            count++
            total += ww
        }
        if count==15 and total==-4965
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endw1=17000
        c64scr.print("word var in ncrange step 1: ")
        for ww in 16500 to endw1 step 1 {
            count++
            total += ww
        }
        if count==501 and total==3142
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        c64scr.print("word var in ncrange step -1: ")
        for ww in endw1 to 16500 step -1 {
            count++
            total += ww
        }
        if count==501 and total==3142
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endw1=17000
        c64scr.print("word var in ncrange step 333: ")
        for ww in 14500 to endw1 step 333 {
            count++
            total += ww
        }
        if count==8 and total==-5748
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endw1=17000
        c64scr.print("word var in ncrange step -333: ")
        for ww in endw1 to 14500 step -333 {
            count++
            total += ww
        }
        if count==8 and total==-4396
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endw1=17164
        c64scr.print("word var in ncrangeinc step 333: ")
        for ww in 14500 to endw1 step 333 {
            count++
            total += ww
        }
        if count==9 and total==11416
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        count = 0
        total = 0
        endw1=17000
        c64scr.print("word var in ncrangeinc step -333: ")
        for ww in endw1 to 14336 step -333 {
            count++
            total += ww
        }
        if count==9 and total==9940
            c64scr.print("ok\n")
        else
            c64scr.print("fail!!!\n")

        ubyte xx=X
        c64scr.print_uw(xx)
    }

    sub wait_input() {
        c64scr.print("enter to continue:")
        str input = "                                        "
        c64scr.input_chars(input)
        c64scr.print("\n\n")
    }
}
