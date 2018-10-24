; %import c64utils
%option enable_floats
%output raw
%launcher none

~ main  {

sub start() {

    ubyte pixely = 255
    ubyte ub = 0
    byte b = 99
    byte b2 = 100
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


    barr1[2]=42
    ubarr1[2]=42
    warr1[2]=12555
    uwarr1[2]=42555
    farr1[2]=42.5678

    barr1[2] = b
    ubarr1[2] = ub
    warr1[2] = w
    uwarr1[2] = uw
    farr1[2] = fl1

    barr1[2] = mbyte
    ubarr1[2] = mubyte
    warr1[2] = mword
    uwarr1[2] = muword
    farr1[2] = mfloat

    b= barr1[2]
    ub = ubarr1[2]
    w = warr1[2]
    uw = uwarr1[2]
    fl1 = farr1[2]

    mbyte= barr1[2]
    mubyte = ubarr1[2]
    mword = warr1[2]
    muword = uwarr1[2]
    mfloat = farr1[2]

    barr1[2] = barr2[3]
    ubarr1[2] = ubarr2[3]
    warr1[2] = warr2[3]
    uwarr1[2] = uwarr2[3]
    farr1[2] = farr2[3]



;    b = 1
;    ub = 1
;    w = 1
;    uw = 1
;    fl1 = 2.345
;
;    b = b2
;    ub = pixely
;    w = w2
;    uw = uw2
;    fl1 = fl2
;
;    b = mbyte
;    ub = mubyte
;    w = mword
;    uw = muword
;    fl1 = mfloat
;
;    mbyte = 1
;    mubyte = 1
;    mword = 1
;    muword = 1
;    mfloat = 3.456

;    %breakpoint
;
;    mbyte = b
;    mubyte = ub
;    mword = w
;    muword = uw
;    mfloat = fl2
;
;    %breakpoint
;
;    mbyte = mbyte2
;    mubyte = mubyte2
;    mword = mword2
;    muword = muword2
;    mfloat = mfloat2
;

    return
}

}
