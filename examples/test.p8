%import c64utils
%zeropage basicsafe
%import c64flt


~ main {

    sub start() {
        ubyte[100] arr1
        ubyte[100] arr2

    _lp:
        memcopy(arr1, arr2, len(arr2))
        c64scr.setcc(20,10,65,2)
        goto x
        c64scr.setcc(20,10,65,2)
    }

    sub x() {

derp:
        c64scr.print("ey\n")
        goto derp

    }

}
