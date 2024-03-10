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

        ; ANY
        ub = any(ubarr) as ubyte
        if ub==0 txt.print("error any1\n")
        ub = any(barr) as ubyte
        if ub==0 txt.print("error any2\n")
        ub = any(uwarr) as ubyte
        if ub==0 txt.print("error any3\n")
        ub = any(warr) as ubyte
        if ub==0 txt.print("error any4\n")
        ub = any(farr) as ubyte
        if ub==0 txt.print("error any5\n")

        ; ALL
        ub = all(ubarr) as ubyte
        if ub==1 txt.print("error all1\n")
        ub = all(barr) as ubyte
        if ub==1 txt.print("error all2\n")
        ub = all(uwarr) as ubyte
        if ub==1 txt.print("error all3\n")
        ub = all(warr) as ubyte
        if ub==1 txt.print("error all4\n")
        ub = all(farr) as ubyte
        if ub==1 txt.print("error all5\n")
        ubarr[1]=$40
        barr[1]=$40
        uwarr[1]=$4000
        warr[1]=$4000
        farr[1]=1.1
        ub = all(ubarr) as ubyte
        if ub==0 txt.print("error all6\n")
        ub = all(barr) as ubyte
        if ub==0 txt.print("error all7\n")
        ub = all(uwarr) as ubyte
        if ub==0 txt.print("error all8\n")
        ub = all(warr) as ubyte
        if ub==0 txt.print("error all9\n")
        ub = all(farr) as ubyte
        if ub==0 txt.print("error all10\n")

        txt.print("\nyou should see no errors printed above (only at first run).")
    }
}
