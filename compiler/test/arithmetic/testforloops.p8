%import textio
%import test_stack
%zeropage basicsafe

; Note: this program is compatible with C64 and CX16.

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
        ubyte a

        txt.plot(0,24)

        ; ---------- REGISTER A ---------
        count = 0
        total = 0
        txt.print("a in string:    ")
        for a in "hello" {
            aa=a
            count++
            total += aa
        }
        if count==5 and total==372
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("a in arrayliteral:    ")
        for a in [1,3,5,99] {
            aa=a
            count++
            total += aa
        }
        if count==4 and total==108
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("a in arrayvar:    ")
        for a in ubarr {
            aa=a
            count++
            total += aa
        }
        if count==5 and total==220
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("a in range step 1:    ")
        for a in 10 to 20 {
            aa=a
            count++
            total += aa
        }
        if count==11 and total==165
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("a in range step -1:    ")
        for a in 20 downto 10 {
            aa=a
            count++
            total += aa
        }
        if count==11 and total==165
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("a in range step 3:    ")
        for a in 10 to 21 step 3 {
            aa=a
            count++
            total += aa
        }
        if count==4 and total==58
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("a in rangeincl step 3:    ")
        for a in 10 to 22 step 3 {
            aa=a
            count++
            total += aa
        }
        if count==5 and total==80
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("a in range step -3:    ")
        for a in 24 to 10 step -3 {
            aa=a
            count++
            total += aa
        }
        if count==5 and total==90
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("a in rangeincl step -3:    ")
        for a in 24 to 9 step -3 {
            aa=a
            count++
            total += aa
        }
        if count==6 and total==99
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endub1=101
        txt.print("a in ncrange step 1:    ")
        for a in 95 to endub1 step 1 {
            aa=a
            count++
            total += aa
        }
        if count==7 and total==686
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endub1=101
        txt.print("a in ncrange step -1:    ")
        for a in endub1 downto 95 {
            aa=a
            count++
            total += aa
        }
        if count==7 and total==686
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endub1=105
        txt.print("a in ncrange step 3:    ")
        for a in 95 to endub1 step 3 {
            aa=a
            count++
            total += aa
        }
        if count==4 and total==398
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endub1=105
        txt.print("a in ncrange step -3:    ")
        for a in endub1 to 95 step -3 {
            aa=a
            count++
            total += aa
        }
        if count==4 and total==402
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endub1=107
        txt.print("a in ncrangeinc step 3:    ")
        for a in 95 to endub1 step 3 {
            aa=a
            count++
            total += aa
        }
        if count==5 and total==505
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endub1=107
        txt.print("a in ncrangeinc step -3:    ")
        for a in endub1 to 95 step -3 {
            aa=a
            count++
            total += aa
        }
        if count==5 and total==505
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        wait_input()

        ; ---------- UBYTE var ---------

        count = 0
        total = 0
        txt.print("ubyte var in string:    ")
        for ub in "hello" {
            count++
            total += ub
        }
        if count==5 and total==372
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("ubyte var in arrayliteral:    ")
        for ub in [1,3,5,99] {
            count++
            total += ub
        }
        if count==4 and total==108
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("ubyte var in arrayvar:    ")
        for ub in ubarr {
            count++
            total += ub
        }
        if count==5 and total==220
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("ubyte var in range step 1:    ")
        for ub in 10 to 20 {
            count++
            total += ub
        }
        if count==11 and total==165
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("ubyte var in range step -1:    ")
        for ub in 20 downto 10 step -1 {
            count++
            total += ub
        }
        if count==11 and total==165
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("ubyte var in range step 3:    ")
        for ub in 10 to 21 step 3 {
            count++
            total += ub
        }
        if count==4 and total==58
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("ubyte var in rangeincl step 3:    ")
        for ub in 10 to 22 step 3 {
            count++
            total += ub
        }
        if count==5 and total==80
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("ubyte var in range step -3:    ")
        for ub in 24 to 10 step -3 {
            count++
            total += ub
        }
        if count==5 and total==90
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("ubyte var in rangeincl step -3:    ")
        for ub in 24 to 9 step -3 {
            count++
            total += ub
        }
        if count==6 and total==99
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endub1=101
        txt.print("ubyte var in ncrange step 1:    ")
        for ub in 95 to endub1 step 1 {
            count++
            total += ub
        }
        if count==7 and total==686
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endub1=101
        txt.print("ubyte var in ncrange step -1:    ")
        for ub in endub1 downto 95 {
            count++
            total += ub
        }
        if count==7 and total==686
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endub1=105
        txt.print("ubyte var in ncrange step 3:    ")
        for ub in 95 to endub1 step 3 {
            count++
            total += ub
        }
        if count==4 and total==398
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endub1=105
        txt.print("ubyte var in ncrange step -3:    ")
        for ub in endub1 to 95 step -3 {
            count++
            total += ub
        }
        if count==4 and total==402
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endub1=107
        txt.print("ubyte var in ncrangeinc step 3:    ")
        for ub in 95 to endub1 step 3 {
            count++
            total += ub
        }
        if count==5 and total==505
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endub1=107
        txt.print("ubyte var in ncrangeinc step -3:    ")
        for ub in endub1 to 95 step -3 {
            count++
            total += ub
        }
        if count==5 and total==505
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        wait_input()

        ; ---------- BYTE var ---------

        count = 0
        total = 0
        txt.print("byte var in arrayliteral:    ")
        for bb in [1,3,5,99] {
            count++
            total += bb
        }
        if count==4 and total==108
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("byte var in arrayvar:    ")
        for bb in barr {
            count++
            total += bb
        }
        if count==5 and total==66
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("byte var in range step 1:    ")
        for bb in 10 to 20 {
            count++
            total += bb
        }
        if count==11 and total==165
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("byte var in range step -1:    ")
        for bb in 20 downto 10 {
            count++
            total += bb
        }
        if count==11 and total==165
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("byte var in range step 3:    ")
        for bb in 10 to 21 step 3 {
            count++
            total += bb
        }
        if count==4 and total==58
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("byte var in rangeincl step 3:    ")
        for bb in 10 to 22 step 3 {
            count++
            total += bb
        }
        if count==5 and total==80
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("byte var in range step -3:    ")
        for bb in 24 to 10 step -3 {
            count++
            total += bb
        }
        if count==5 and total==90
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("byte var in rangeincl step -3:    ")
        for bb in 24 to 9 step -3 {
            count++
            total += bb
        }
        if count==6 and total==99
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endb1=101
        txt.print("byte var in ncrange step 1:    ")
        for bb in 95 to endb1 step 1 {
            count++
            total += bb
        }
        if count==7 and total==686
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endb1=101
        txt.print("byte var in ncrange step -1:    ")
        for bb in endb1 downto 95 {
            count++
            total += bb
        }
        if count==7 and total==686
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endb1=105
        txt.print("byte var in ncrange step 3:    ")
        for bb in 95 to endb1 step 3 {
            count++
            total += bb
        }
        if count==4 and total==398
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endb1=105
        txt.print("byte var in ncrange step -3:    ")
        for bb in endb1 to 95 step -3 {
            count++
            total += bb
        }
        if count==4 and total==402
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endb1=107
        txt.print("byte var in ncrangeinc step 3:    ")
        for bb in 95 to endb1 step 3 {
            count++
            total += bb
        }
        if count==5 and total==505
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endb1=107
        txt.print("byte var in ncrangeinc step -3:    ")
        for bb in endb1 to 95 step -3 {
            count++
            total += bb
        }
        if count==5 and total==505
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        wait_input()

        ; ---------- UWORD var ---------

        uword[] uwarr = [1111,2222,3330,4000]
        uword enduw1
        uword totaluw
        uword uw

        count = 0
        totaluw = 0
        txt.print("uword var in string:    ")
        for uw in "hello" {
            count++
            totaluw += uw
        }
        if count==5 and totaluw==372
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        txt.print("uword var in arrayliteral:    ")
        for uw in [1111,3333,555,999] {
            count++
            totaluw += uw
        }
        if count==4 and totaluw==5998
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        txt.print("uword var in arrayvar:    ")
        for uw in uwarr {
            count++
            totaluw += uw
        }
        if count==4 and totaluw==10663
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        txt.print("uword var in range step 1:    ")
        for uw in 1000 to 1100 {
            count++
            totaluw += uw
        }
        if count==101 and totaluw==40514
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        txt.print("uword var in range step -1:    ")
        for uw in 2000 downto 1500 {
            count++
            totaluw += uw
        }
        if count==501 and totaluw==24782
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        txt.print("uword var in range step 333:    ")
        for uw in 1000 to 2200 step 333 {
            count++
            totaluw += uw
        }
        if count==4 and totaluw==5998
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        txt.print("uword var in rangeincl step 333:    ")
        for uw in 1000 to 2332 step 333 {
            count++
            totaluw += uw
        }
        if count==5 and totaluw==8330
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        txt.print("uword var in range step -333:    ")
        for uw in 17000 to 14500 step -333 {
            count++
            totaluw += uw
        }
        if count==8 and totaluw==61140
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        txt.print("uword var in rangeincl step -333:    ")
        for uw in 17000 to 14336 step -333 {
            count++
            totaluw += uw
        }
        if count==9 and totaluw==9940
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        enduw1=17000
        txt.print("uword var in ncrange step 1:    ")
        for uw in 16500 to enduw1 step 1 {
            count++
            totaluw += uw
        }
        if count==501 and totaluw==3142
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        txt.print("uword var in ncrange step -1:    ")
        for uw in enduw1 downto 16500 {
            count++
            totaluw += uw
        }
        if count==501 and totaluw==3142
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        enduw1=17000
        txt.print("uword var in ncrange step 333:    ")
        for uw in 14500 to enduw1 step 333 {
            count++
            totaluw += uw
        }
        if count==8 and totaluw==59788
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        enduw1=17000
        txt.print("uword var in ncrange step -333:    ")
        for uw in enduw1 to 14500 step -333 {
            count++
            totaluw += uw
        }
        if count==8 and totaluw==61140
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        enduw1=17164
        txt.print("uword var in ncrangeinc step 333:    ")
        for uw in 14500 to enduw1 step 333 {
            count++
            totaluw += uw
        }
        if count==9 and totaluw==11416
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        totaluw = 0
        enduw1=17000
        txt.print("uword var in ncrangeinc step -333:    ")
        for uw in enduw1 to 14336 step -333 {
            count++
            totaluw += uw
        }
        if count==9 and totaluw==9940
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        wait_input()

        ; ---------- WORD var ---------

        word[] warr = [-111,222,-333,444]
        word endw1
        word ww

        count = 0
        total = 0
        txt.print("word var in arrayliteral:    ")
        for ww in [1111,3333,555,999] {
            count++
            total += ww
        }
        if count==4 and total==5998
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("word var in arrayvar:    ")
        for ww in warr {
            count++
            total += ww
        }
        if count==4 and total==222
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("word var in range step 1:    ")
        for ww in -100 to 1000 {
            count++
            total += ww
        }
        if count==1101 and total==-28838
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("word var in range step -1:    ")
        for ww in 1000 downto -500 {
            count++
            total += ww
        }
        if count==1501 and total==-17966
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("word var in range step 333:    ")
        for ww in -1000 to 2200 step 333 {
            count++
            total += ww
        }
        if count==10 and total==4985
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("word var in rangeincl step 333:    ")
        for ww in -1000 to 2330 step 333 {
            count++
            total += ww
        }
        if count==11 and total==7315
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("word var in range step -333:    ")
        for ww in 2000 to -2500 step -333 {
            count++
            total += ww
        }
        if count==14 and total==-2303
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("word var in rangeincl step -333:    ")
        for ww in 2000 to -2662 step -333 {
            count++
            total += ww
        }
        if count==15 and total==-4965
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endw1=17000
        txt.print("word var in ncrange step 1:    ")
        for ww in 16500 to endw1 step 1 {
            count++
            total += ww
        }
        if count==501 and total==3142
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        txt.print("word var in ncrange step -1:    ")
        for ww in endw1 downto 16500 {
            count++
            total += ww
        }
        if count==501 and total==3142
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endw1=17000
        txt.print("word var in ncrange step 333:    ")
        for ww in 14500 to endw1 step 333 {
            count++
            total += ww
        }
        if count==8 and total==-5748
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endw1=17000
        txt.print("word var in ncrange step -333:    ")
        for ww in endw1 to 14500 step -333 {
            count++
            total += ww
        }
        if count==8 and total==-4396
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endw1=17164
        txt.print("word var in ncrangeinc step 333:    ")
        for ww in 14500 to endw1 step 333 {
            count++
            total += ww
        }
        if count==9 and total==11416
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")

        count = 0
        total = 0
        endw1=17000
        txt.print("word var in ncrangeinc step -333:    ")
        for ww in endw1 to 14336 step -333 {
            count++
            total += ww
        }
        if count==9 and total==9940
            txt.print("ok\n")
        else
            txt.print("fail!!!\n")


        test_stack.test()
    }

    sub wait_input() {
        txt.print("enter to continue:")
        str input = "                                        "
        void txt.input_chars(input)
        txt.print("\n\n")
    }
}
