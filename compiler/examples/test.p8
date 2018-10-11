%option enable_floats


~ main {

    const uword width=320
    const uword height=200

sub start() {

    byte[4]  barr1 = -2
    byte[4]  barr2 = 33
    ubyte[4]  barr4 = 2
    ubyte[4]  barr5 = 44
    word[4]  warr1 = 4444
    word[4]  warr2 = -5555
    word[4]  warr2b = -5522
    float[4]  farr1
    float[4]  farr2 = 23
    float[4]  farr3 = 55.636346

;    byte  b1 = 50 * 2
;    byte  b2 = -50 * 2
;    ubyte ub1 = 50 * 2
;    word  w1 = 999 * 2
;    word  w2 = -999 * 2
;    uword uw1 = 999 * 2
;    float f1 = 999*2
;    float f2 = -999*2

    return

}

;        sub toscreenx(x: float, z: float) -> word {
;            return floor(x/(4.2+z) * flt(height)) + width // 2
;        }

}
