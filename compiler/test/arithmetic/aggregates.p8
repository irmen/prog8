%import floats
%import textio
%import strings
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
        length=strings.length(name)
        if length!=5 txt.print("error strlen1\n")
        name[3] = 0
        length=strings.length(name)
        if length!=3 txt.print("error strlen2\n")

        txt.print("\nyou should see no errors printed above (first run only).")
    }
}
