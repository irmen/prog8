%import c64utils
%zeropage basicsafe
%option enable_floats

~ main {

    sub start() {

        word zc
        word qq = zc>>13
        ubyte[] colors = [1,2,3,4,5,6,7,8]

        uword bb = zc>>13
        c64.SPCOL[0] = colors[(zc>>13) as byte + 4]

    }

}
