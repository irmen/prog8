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
    memory byte[3] mbarr1 = $e000
    memory ubyte[3] mubarr1 = $e100
    memory word[3] mwarr1 = $e100
    memory uword[3] muwarr1 = $e100

label:

    Y=42
    AY=42
    AY=42555
    Y = ub
    AY= ub
    AY= uw

    Y = mubyte
    AY = mubyte
    AY = muword

    Y = ubarr1[2]
    AY = ubarr1[2]
    AY = uwarr1[2]
    Y = mubarr1[2]
    AY = mubarr1[2]
    AY = muwarr1[2]

    ub=Y
    uw=AY
    mubyte=Y
    muword=AY
    ubarr1[2]=Y
    uwarr1[2]=AY
    Y=A
    AY=Y


    barr1[2]=42
    ubarr1[2]=42
    warr1[2]=12555
    uwarr1[2]=42555
    farr1[2]=42.5678

    ubarr1[2]=X
    uwarr1[2]=XY

    ; farr1[2]=Y     ; @todo  via regular evaluation
    ; farr1[2]=XY     ; @todo   via regular evaluation
    ; farr1[X]=Y     ; @todo  via regular evaluation
    ; farr1[Y]=XY     ; @todo  via regular evaluation
    ; farr1[b]=XY     ; @todo   via regular evaluation
    ; farr1[ub]=XY     ; @todo   via regular evaluation
    ; farr1[mbyte]=XY     ; @todo   via regular evaluation
    ; farr1[mubyte]=XY     ; @todo  via regular evaluation

    farr1[w]=XY     ; @todo  error message about index 0..255
    farr1[uw]=XY     ; @todo  error message about index 0..255
    farr1[mword]=XY     ; @todo  error message about index 0..255
    farr1[muword]=XY     ; @todo  error message about index 0..255

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
;    fl1 = farr1[2]   ; @todo
;    fl1 = farr1[X]   ; @todo
;    fl1 = farr1[b]   ; @todo
;    fl1 = farr1[ub]   ; @todo
;    fl1 = farr1[mbyte]   ; @todo
;    fl1 = farr1[mubyte]   ; @todo
    fl1 = farr1[w]   ; @todo error message about index 0..255
    fl1 = farr1[uw]   ; @todo error message about index 0..255
    fl1 = farr1[mword]   ; @todo error message about index 0..255
    fl1 = farr1[muword]   ; @todo error message about index 0..255

    ;farr1[3] = farr2[4] ; @todo
    ;farr1[X] = farr2[Y] ; @todo
    farr1[XY] = farr2[AY] ; @todo  error message about index 0..255
    ;farr1[b] = farr2[b2] ; @todo
    ;farr1[mbyte] = farr2[mbyte2] ; @todo


    mbyte= barr1[2]
    mubyte = ubarr1[2]
    mword = warr1[2]
    muword = uwarr1[2]
    ; mfloat = farr1[2]   ; @todo

    barr1[2] = barr2[3]
    ubarr1[2] = ubarr2[3]
    warr1[2] = warr2[3]
    uwarr1[2] = uwarr2[3]
    ; farr1[2] = farr2[3]  ; @todo


    XY[2]=42
    XY[2] = ub
    XY[2] = mubyte
    ub = XY[2]
    uw = XY[2]
    ; fl1 = XY[2]    ; @todo    via regular evaluation
    mubyte = XY[2]
    muword = XY[2]
    ;mfloat = XY[2]    ; @todo     via regular evaluation

    XY[33] = ubarr2[2]
    XY[33] = mubarr1[2]
    ubarr2[2] =  XY[33]
    mubarr1[2] = XY[33]
    Y = AY[33]
    AY[33] = Y


    b = 1
    ub = 1
    w = 1
    uw = 1
    fl1 = 2.345

    b = b2
    ub = pixely
    w = b2
    w = w2
    w = ub
    uw = ub
    uw = uw2
    fl1 = ub
    fl1 = b2
    fl1 = uw2
    fl1 = w2
    fl1 = X
    fl1 = AY
    fl1 = fl2

    b = mbyte
    ub = mubyte
    w = mword
    w = mbyte
    w = mubyte
    uw = mubyte
    uw = muword
    fl1 = mfloat
    fl1 = mbyte
    fl1 = mword
    fl1 = mubyte
    fl1 = muword

    mbyte = 1
    mubyte = 1
    mword = 1
    muword = 1
    mfloat = 3.456

    %breakpoint

    mbyte = b
    mubyte = ub
    mword = w
    muword = uw
    mfloat = fl2

    %breakpoint

    mbyte = mbyte2
    mubyte = mubyte2
    mword = mword2
    muword = muword2
    mfloat = mfloat2


    return
}

}
