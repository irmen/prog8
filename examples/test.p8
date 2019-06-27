%import c64utils
%zeropage basicsafe
%import c64flt


~ main {

    sub start() {
        ubyte u1 = 100
        ubyte u2 = 30
        byte bb = -30
        byte bb2 = -30
        float ff = -3.3
        word ww

        bb = (u2 as word) as byte
        bb = (((u2 as word) as byte) as word) as byte


    }

}
