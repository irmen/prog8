%import c64lib
%import c64utils
%import c64flt
%zeropage dontuse

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
        if length!=5 c64scr.print("error len1\n")
        length = len(uwarr)
        if length!=5 c64scr.print("error len2\n")
        length=strlen(name)
        if length!=5 c64scr.print("error strlen1\n")
        name[3] = 0
        length=strlen(name)
        if length!=3 c64scr.print("error strlen2\n")

        ; MAX
        ub = max(ubarr)
        if ub!=199 c64scr.print("error max1\n")
        bb = max(barr)
        if bb!=99 c64scr.print("error max2\n")
        uw = max(uwarr)
        if uw!=4444 c64scr.print("error max3\n")
        ww = max(warr)
        if ww!=999 c64scr.print("error max4\n")
        ff = max(farr)
        if ff!=999.9 c64scr.print("error max5\n")

        ; MIN
        ub = min(ubarr)
        if ub!=0 c64scr.print("error min1\n")
        bb = min(barr)
        if bb!=-122 c64scr.print("error min2\n")
        uw = min(uwarr)
        if uw!=0 c64scr.print("error min3\n")
        ww = min(warr)
        if ww!=-4444 c64scr.print("error min4\n")
        ff = min(farr)
        if ff!=-4444.4 c64scr.print("error min5\n")

        ; SUM
        uw = sum(ubarr)
        if uw!=420 c64scr.print("error sum1\n")
        ww = sum(barr)
        if ww!=-101 c64scr.print("error sum2\n")
        uw = sum(uwarr)
        if uw!=6665 c64scr.print("error sum3\n")
        ww = sum(warr)
        if ww!=-4223 c64scr.print("error sum4\n")
        ff = sum(farr)
        if ff!=-4222.4 c64scr.print("error sum5\n")

        ; ANY
        ub = any(ubarr)
        if ub==0 c64scr.print("error any1\n")
        ub = any(barr)
        if ub==0 c64scr.print("error any2\n")
        ub = any(uwarr)
        if ub==0 c64scr.print("error any3\n")
        ub = any(warr)
        if ub==0 c64scr.print("error any4\n")
        ub = any(farr)
        if ub==0 c64scr.print("error any5\n")

        ; ALL
        ub = all(ubarr)
        if ub==1 c64scr.print("error all1\n")
        ub = all(barr)
        if ub==1 c64scr.print("error all2\n")
        ub = all(uwarr)
        if ub==1 c64scr.print("error all3\n")
        ub = all(warr)
        if ub==1 c64scr.print("error all4\n")
        ub = all(farr)
        if ub==1 c64scr.print("error all5\n")
        ubarr[1]=$40
        barr[1]=$40
        uwarr[1]=$4000
        warr[1]=$4000
        farr[1]=1.1
        ub = all(ubarr)
        if ub==0 c64scr.print("error all6\n")
        ub = all(barr)
        if ub==0 c64scr.print("error all7\n")
        ub = all(uwarr)
        if ub==0 c64scr.print("error all8\n")
        ub = all(warr)
        if ub==0 c64scr.print("error all9\n")
        ub = all(farr)
        if ub==0 c64scr.print("error all10\n")

        check_eval_stack()

        c64scr.print("\nyou should see no errors above.")
    }

    sub check_eval_stack() {
        if X!=255 {
            c64scr.print("x=")
            c64scr.print_ub(X)
            c64scr.print(" error!\n")
        }
    }

}
