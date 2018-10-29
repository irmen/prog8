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

    str  string = "hello"
    str_p stringp = "hello"


; all possible assignments to a BYTE VARIABLE (not array)

byte_assignment_to_register:
    A = 42
    A = X
    A = ub
    A = mubyte
    A = AY[4]
    A = ubarr1[2]
    A = string[4]
    A = string[X]
    A = string[b]
    A = string[ub]
    A = ubarr1[X]
    A = ubarr1[b]
    A = ubarr1[ub]
    A = AY[Y]
    A = AY[b]
    A = AY[ub]
    A = ubmatrix1[1,2]
    ;A = ubmatrix1[1,Y]  ;  todo via evaluation
    A = ubmatrix1[X,2]  ;  todo via evaluation     TODO fix error   constant y dimension of index should have been const-folded with x into one value
    ;A = ubmatrix1[X,Y]  ;  todo via evaluation
    ;A = ubmatrix1[1,b2]  ;  todo via evaluation
    ;A = ubmatrix1[X,b2]  ;  todo via evaluation
    A = ubmatrix1[b2,2]     ; todo FIX ERROR   constant y dimension of index should have been const-folded with x into one value
    ;A = ubmatrix1[b2,X]  ;  todo via evaluation
    ;A = ubmatrix1[b,b2]  ;  todo via evaluation
    ;A = ubmatrix1[ub,ub2]  ;  todo via evaluation

;byte_assignment_to_bytevar:
;    b = 42
;    b = b2
;    b = mbyte
;    b = barr1[2]
;    b = bmatrix1[1,2]
;
;    ub = 42
;    ub = X
;    ub = ub2
;    ub = mubyte
;    ub = ubarr1[2]
;    ub = ubmatrix1[1,2]
;    ub = string[4]
;    ub = AY[4]
;
;
;; all possible assignments to a WORD VARIABLE (not array)
;
;word_assignment_to_registerpair:
;    AY = 42
;    AY = 42.w
;    AY = 42555
;    AY = X
;    AY = XY
;    AY = ub
;    AY = mubyte
;    AY = ubarr1[2]
;    AY = ubmatrix1[1,2]
;    AY = string[4]
;    AY = uw
;    AY = muword
;    AY = uwarr1[2]
;    AY = string[4]
;    AY = XY[4]
;
;;word_assignment_to_wordvar:
;    w = -42
;    w = -42.w
;    w = -12345
;    w = X
;    w = b2
;    w = ub2
;    w = w2
;    w = mbyte
;    w = mubyte
;    w = mword
;    w = barr1[2]
;    w = ubarr1[2]
;    w = warr1[2]
;    w = bmatrix1[1,2]
;    w = ubmatrix1[1,2]
;    w = string[4]
;    w = AY[4]
;
;    uw = 42
;    uw = 42.w
;    uw = 42555
;    uw = X
;    uw = AY
;    uw = ub2
;    uw = uw2
;    uw = mubyte
;    uw = muword
;    uw = ubarr1[2]
;    uw = uwarr1[2]
;    uw = ubmatrix1[1,2]
;    uw = string[4]
;    uw = AY[4]
;
;
;; all possible assignments to a FLOAT VARIABLE
;float_assignment_to_floatvar:
;    fl1 = 34
;    fl1 = 34555.w
;    fl1 = 3.33e22
;    fl1 = X
;    fl1 = AY
;    fl1 = b2
;    fl1 = ub2
;    fl1 = w2
;    fl1 = uw2
;    fl1 = mbyte
;    fl1 = mubyte
;    fl1 = mword
;    fl1 = muword
;    fl1 = barr1[2]
;    fl1 = ubarr1[2]
;    fl1 = warr1[2]
;    fl1 = uwarr1[2]
;    fl1 = bmatrix1[1,2]
;    fl1 = ubmatrix1[1,2]
;    fl1 = string[4]

    return
}

}
