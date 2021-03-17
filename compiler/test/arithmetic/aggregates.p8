%import floats
%import textio
%import string
%zeropage basicsafe

main {

    sub start() {
        ubyte[]  ubarr = [100, 0, 99, 199, 22]
        byte[]  barr = [-100, 0, 99, -122, 22]
        uword[] uwarr = [1000, 0, 222, 4444, 999]
        word[]  warr = [-1000, 0, 999, -4444, 222]
        float[]  farr = [-1000.1, 0, 999.9, -4444.4, 222.2]
        str name = "irmen"
        ubyte ub
        byte bb
        word ww
        uword uw
        float ff

        ; LEN/STRLEN
        ubyte length = len(name)
        if length!=5 txt.print("error len1\n")
        length = len(uwarr)
        if length!=5 txt.print("error len2\n")
        length=string.length(name)
        if length!=5 txt.print("error strlen1\n")
        name[3] = 0
        length=string.length(name)
        if length!=3 txt.print("error strlen2\n")

        ; MAX
        ub = max(ubarr)
        if ub!=199 txt.print("error max1\n")
        bb = max(barr)
        if bb!=99 txt.print("error max2\n")
        uw = max(uwarr)
        if uw!=4444 txt.print("error max3\n")
        ww = max(warr)
        if ww!=999 txt.print("error max4\n")
        ff = max(farr)
        if ff!=999.9 txt.print("error max5\n")

        ; MIN
        ub = min(ubarr)
        if ub!=0 txt.print("error min1\n")
        bb = min(barr)
        if bb!=-122 txt.print("error min2\n")
        uw = min(uwarr)
        if uw!=0 txt.print("error min3\n")
        ww = min(warr)
        if ww!=-4444 txt.print("error min4\n")
        ff = min(farr)
        if ff!=-4444.4 txt.print("error min5\n")

        ; SUM
        uw = sum(ubarr)
        if uw!=420 txt.print("error sum1\n")
        ww = sum(barr)
        if ww!=-101 txt.print("error sum2\n")
        uw = sum(uwarr)
        if uw!=6665 txt.print("error sum3\n")
        ww = sum(warr)
        if ww!=-4223 txt.print("error sum4\n")
        ff = sum(farr)
        if ff!=-4222.4 txt.print("error sum5\n")

        ; ANY
        ub = any(ubarr)
        if ub==0 txt.print("error any1\n")
        ub = any(barr)
        if ub==0 txt.print("error any2\n")
        ub = any(uwarr)
        if ub==0 txt.print("error any3\n")
        ub = any(warr)
        if ub==0 txt.print("error any4\n")
        ub = any(farr)
        if ub==0 txt.print("error any5\n")

        ; ALL
        ub = all(ubarr)
        if ub==1 txt.print("error all1\n")
        ub = all(barr)
        if ub==1 txt.print("error all2\n")
        ub = all(uwarr)
        if ub==1 txt.print("error all3\n")
        ub = all(warr)
        if ub==1 txt.print("error all4\n")
        ub = all(farr)
        if ub==1 txt.print("error all5\n")
        ubarr[1]=$40
        barr[1]=$40
        uwarr[1]=$4000
        warr[1]=$4000
        farr[1]=1.1
        ub = all(ubarr)
        if ub==0 txt.print("error all6\n")
        ub = all(barr)
        if ub==0 txt.print("error all7\n")
        ub = all(uwarr)
        if ub==0 txt.print("error all8\n")
        ub = all(warr)
        if ub==0 txt.print("error all9\n")
        ub = all(farr)
        if ub==0 txt.print("error all10\n")

        txt.print("\nyou should see no errors printed above (only at first run).")
    }
}
