%import c64utils
%import c64flt
%zeropage basicsafe
%option enable_floats

main {

    ubyte[10] barray
    uword[10] warray
    float[10] farray
    &ubyte memubarray = 1000
    &uword memuwarray = 1000
    &float memfltarray = 1000

    sub start() {
        ubyte i
        uword uw
        float fl = farray[2]

        barray[4] = 4
        barray[i] = 4
        barray[i+4] = 4
        memubarray[4] = 4
        memubarray[i] = 4
        memubarray[i+4] = 4


        warray[4] = 4
        warray[i] = 4
        warray[i+4] = 4
        memuwarray[4] = 4
        memuwarray[i] = 4
        memuwarray[i+4] = 4

        farray[4] = 4
        farray[i] = 4
        farray[i+4] = 4
        memfltarray[4] = 4
        memfltarray[i] = 4
        memfltarray[i+4] = 4
    }
}
