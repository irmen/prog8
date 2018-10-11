%option enable_floats


~ main {

    const uword width=320
    const uword height=200

sub start() {

    byte[3]  barr1 = [-2,-1,2]
    byte[3]  barr2 = [-22,-1, 3]
    ubyte[3]  barr3 = [-2,-1,2] ; @ todo error
    ubyte[3]  barr4 = [1,2,33]
    ubyte[3]  barr5 = [1,2,-33]     ; @todo error
    word[3]  warr1 = [-2,-1,2]
    ;word[3]  warr2 = [-2,-1,2, 3453]       ; @todo ok
    uword[3]  warr3 = [-2,-1,2]
    uword[3]  warr4 = [1,2,33.w]
    uword[3]  warr5 = [1,2,-33]     ; @todo error

    byte  b1 = 50 * 2
    byte  b2 = -50 * 2
    ubyte ub1 = 50 * 2
    word  w1 = 999 * 2
    word  w2 = -999 * 2
    uword uw1 = 999 * 2
    float f1 = 999*2
    float f2 = -999*2

    return

}

        sub toscreenx(x: float, z: float) -> word {
            return floor(x/(4.2+z) * flt(height)) + width // 2
        }

}
