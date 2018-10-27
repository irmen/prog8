; %import c64utils
%option enable_floats
%output raw
%launcher none

~ main  {

sub start() {

    byte b = 99
    byte b2 = 100
    ubyte ub = 255
    ubyte ub2 = 0
    word w = 999
    word w2 = 3
    uword uw = 40000
    uword uw2 = 3434
    float fl1 = 1.1
    float fl2 = 2.2

    byte[5] barr1
    byte[5] barr2
    ubyte[5] ubarr1
    ubyte[5] ubarr2
    word[5] warr1
    word[5] warr2
    uword[5] uwarr1
    uword[5] uwarr2
    float[5] farr1
    float[5] farr2
    byte[2,3] bmatrix1
    byte[2,3] bmatrix2
    ubyte[2,3] ubmatrix1
    ubyte[2,3] ubmatrix2

    memory byte mbyte = $c000
    memory byte mbyte2 = $d000
    memory ubyte mubyte = $c001
    memory ubyte mubyte2 = $d001
    memory word mword = $c002
    memory word mword2 = $d002
    memory uword muword = $c004
    memory uword muword2 = $d004
    memory float mfloat = $c006
    memory float mfloat2 = $d006
    memory byte[3] mbarr1 = $e000
    memory ubyte[3] mubarr1 = $e100
    memory word[3] mwarr1 = $e100
    memory uword[3] muwarr1 = $e100


; all possible assignments to a BYTE VARIABLE
    A = 42
    A = Y
    A = ub
    A = mubyte
    A = ubarr1[2]
    A = ubmatrix1[1,2]



    return
}

}
