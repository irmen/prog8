%import c64lib
%import c64utils
%import c64flt
%zeropage dontuse

main {

    sub start() {
        byte[]  barr = [-100, 0, 99, -122, 22]
        ubyte[]  ubarr = [100, 0, 99, 199, 22]
        word[]  warr = [-1000, 0, 999, -4444, 222]
        uword[] uwarr = [1000, 0, 222, 4444, 999]
        float[]  farr = [-1000.1, 0, 999.9, -4444.4, 222.2]
        str name = "irmen"
        ubyte ub
        byte bb
        word ww
        uword uw
        float ff

        ; LEN/STRLEN
        ubyte length = len(name)
        if(length!=5) c64scr.print("error len1\n")
        length = len(uwarr)
        if(length!=5) c64scr.print("error len2\n")
        length=strlen(name)
        if(length!=5) c64scr.print("error strlen1\n")
        name[3] = 0
        length=strlen(name)
        if(length!=3) c64scr.print("error strlen2\n")

        ; MAX
;        ub = max(ubarr)
;        bb = max(barr)
;        ww = max(warr)
;        uw = max(uwarr)
;        ff = max(farr)

;        word ww = sum(barr)
;        uword uw = sum(ubarr)
;        ww = sum(warr)
;        uw = sum(uwarr)
;        float ff = sum(farr)

        c64scr.print("\nyou should see no errors above.")
    }
}
