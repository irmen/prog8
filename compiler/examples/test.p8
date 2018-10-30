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
    memory byte[mubyte2] mbarr1 = $e000
    memory ubyte[mubyte2] mubarr1 = $e100
    memory word[mubyte2] mwarr1 = $e100
    memory uword[mubyte2] muwarr1 = $e100

    str  string = "hello"
    str_p stringp = "hello"


; all possible assignments to a BYTE VARIABLE (not array)

byte_assignment_to_register:
    A = 42
    A = X
    A = ub2
    A = mubyte2
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
    ;A = ubmatrix1[X,2]  ;  todo via evaluation
    ;A = ubmatrix1[X,Y]  ;  todo via evaluation
    ;A = ubmatrix1[1,b2]  ;  todo via evaluation
    ;A = ubmatrix1[X,b2]  ;  todo via evaluation
    ;A = ubmatrix1[b2,2]  ;  todo via evaluation
    ;A = ubmatrix1[b2,X]  ;  todo via evaluation
    ;A = ubmatrix1[b,b2]  ;  todo via evaluation
    ;A = ubmatrix1[ub,ub2]  ;  todo via evaluation

ubyte_assignment_to_ubytevar:
    ub = 42
    ub = X
    ub = ub2
    ub = mubyte2
    ub = AY[4]
    ub = ubarr1[2]
    ub = string[4]
    ub = string[X]
    ub = string[b]
    ub = string[ub]
    ub = ubarr1[X]
    ub = ubarr1[b]
    ub = ubarr1[ub]
    ub = AY[Y]
    ub = AY[b]
    ub = AY[ub]
    ub = ubmatrix1[1,2]
    ;ub = ubmatrix1[1,Y]  ;  todo via evaluation
    ;ub = ubmatrix1[X,2]  ;  todo via evaluation
    ;ub = ubmatrix1[X,Y]  ;  todo via evaluation
    ;ub = ubmatrix1[1,b2]  ;  todo via evaluation
    ;ub = ubmatrix1[X,b2]  ;  todo via evaluation
    ;ub = ubmatrix1[b2,2]  ;  todo via evaluation
    ;ub = ubmatrix1[b2,X]  ;  todo via evaluation
    ;ub = ubmatrix1[b,b2]  ;  todo via evaluation
    ;ub = ubmatrix1[ub,ub2]  ;  todo via evaluation


ubyte_assignment_to_ubytemem:
    mubyte = 42
    mubyte = X
    mubyte = ub2
    mubyte = mubyte2
    mubyte = AY[4]
    mubyte = ubarr1[2]
    mubyte = string[4]
    mubyte = string[X]
    mubyte = string[b]
    mubyte = string[ub]
    mubyte = ubarr1[X]
    mubyte = ubarr1[b]
    mubyte = ubarr1[ub]
    mubyte = AY[Y]
    mubyte = AY[b]
    mubyte = AY[ub]
    mubyte = ubmatrix1[1,2]
    ;mubyte = ubmatrix1[1,Y]  ;  todo via evaluation
    ;mubyte = ubmatrix1[X,2]  ;  todo via evaluation
    ;mubyte = ubmatrix1[X,Y]  ;  todo via evaluation
    ;mubyte = ubmatrix1[1,b2]  ;  todo via evaluation
    ;mubyte = ubmatrix1[X,b2]  ;  todo via evaluation
    ;mubyte = ubmatrix1[b2,2]  ;  todo via evaluation
    ;mubyte = ubmatrix1[b2,X]  ;  todo via evaluation
    ;mubyte = ubmatrix1[b,b2]  ;  todo via evaluation
    ;mubyte = ubmatrix1[ub,ub2]  ;  todo via evaluation

byte_assignment_to_bytevar:
    b = -42
    b = b2
    b = mbyte2
    b = barr1[2]
    b = barr1[X]
    b = barr1[b]
    b = barr1[ub]
    b = bmatrix1[1,2]
    ;b = bmatrix1[1,Y]  ;  todo via evaluation
    ;b = bmatrix1[X,2]  ;  todo via evaluation
    ;b = bmatrix1[X,Y]  ;  todo via evaluation
    ;b = bmatrix1[1,b2]  ;  todo via evaluation
    ;b = bmatrix1[X,b2]  ;  todo via evaluation
    ;b = bmatrix1[b2,2]  ;  todo via evaluation
    ;b = bmatrix1[b2,X]  ;  todo via evaluation
    ;b = bmatrix1[b,b2]  ;  todo via evaluation
    ;b = bmatrix1[ub,ub2]  ;  todo via evaluation


byte_assignment_to_bytemem:
    mbyte = -42
    mbyte = b2
    mbyte = mbyte2
    mbyte = barr1[2]
    mbyte = barr1[X]
    mbyte = barr1[b]
    mbyte = barr1[ub]
    mbyte = bmatrix1[1,2]
    ;mbyte = bmatrix1[1,Y]  ;  todo via evaluation
    ;mbyte = bmatrix1[X,2]  ;  todo via evaluation
    ;mbyte = bmatrix1[X,Y]  ;  todo via evaluation
    ;mbyte = bmatrix1[1,b2]  ;  todo via evaluation
    ;mbyte = bmatrix1[X,b2]  ;  todo via evaluation
    ;mbyte = bmatrix1[b2,2]  ;  todo via evaluation
    ;mbyte = bmatrix1[b2,X]  ;  todo via evaluation
    ;mbyte = bmatrix1[b,b2]  ;  todo via evaluation
    ;mbyte = bmatrix1[ub,ub2]  ;  todo via evaluation


ubyte_assignment_to_ubytearray:
    ubarr2[3] = 42
    ubarr2[3] = X
    ubarr2[3] = ub2
    ubarr2[3] = mubyte2
    ubarr2[3] = AY[4]
    ubarr2[3] = ubarr1[2]
    ubarr2[3] = string[4]
    ubarr2[3] = string[X]
    ubarr2[3] = string[b]
    ubarr2[3] = string[ub]
    ubarr2[3] = ubarr1[X]
    ubarr2[3] = ubarr1[b]
    ubarr2[3] = ubarr1[ub]
    ubarr2[3] = AY[Y]
    ubarr2[3] = AY[b]
    ubarr2[3] = AY[ub]
    AY[3] = 42
    AY[3] = X
    AY[3] = ub2
    AY[3] = mubyte2
    AY[3] = ubarr1[2]
    AY[3] = string[4]
    AY[3] = string[X]
    AY[3] = string[b]
    AY[3] = string[ub]
    AY[3] = ubarr1[X]
    AY[3] = ubarr1[b]
    AY[3] = ubarr1[ub]
    AY[3] = ubmatrix1[1,2]
    string[4] = 42
    string[4] = 'B'
    string[4] = X
    string[4] = ub2
    string[4] = mubyte2
    string[4] = AY[4]
    string[4] = ubarr1[2]
    string[4] = string[3]
    ubarr2[3] = ubmatrix1[1,2]
    ;ubarr2[3] = ubmatrix1[1,Y]  ;  todo via evaluation
    ;ubarr2[3] = ubmatrix1[X,2]  ;  todo via evaluation
    ;ubarr2[3] = ubmatrix1[X,Y]  ;  todo via evaluation
    ;ubarr2[3] = ubmatrix1[1,b2]  ;  todo via evaluation
    ;ubarr2[3] = ubmatrix1[X,b2]  ;  todo via evaluation
    ;ubarr2[3] = ubmatrix1[b2,2]  ;  todo via evaluation
    ;ubarr2[3] = ubmatrix1[b2,X]  ;  todo via evaluation
    ;ubarr2[3] = ubmatrix1[b,b2]  ;  todo via evaluation
    ;ubarr2[3] = ubmatrix1[ub,ub2]  ;  todo via evaluation


    ubarr2[Y] = 42
    ubarr2[Y] = X
    ubarr2[Y] = ub2
    ubarr2[Y] = mubyte2
    ubarr2[Y] = AY[4]
    ubarr2[Y] = ubarr1[2]
    ubarr2[Y] = string[4]
    ubarr2[Y] = string[X]
    ubarr2[Y] = string[b]
    ubarr2[Y] = string[ub]
    ubarr2[Y] = ubarr1[X]
    ubarr2[Y] = ubarr1[b]
    ubarr2[Y] = ubarr1[ub]
    ubarr2[Y] = AY[Y]
    ubarr2[Y] = AY[b]
    ubarr2[Y] = AY[ub]
    AY[Y] = 42
    AY[Y] = X
    AY[Y] = ub2
    AY[Y] = mubyte2
    AY[Y] = ubarr1[2]
    AY[Y] = string[4]
    AY[Y] = string[X]
    AY[Y] = string[b]
    AY[Y] = string[ub]
    AY[Y] = ubarr1[X]
    AY[Y] = ubarr1[b]
    AY[Y] = ubarr1[ub]
    AY[Y] = ubmatrix1[1,2]
    string[Y] = 42
    string[Y] = 'B'
    string[Y] = X
    string[Y] = ub2
    string[Y] = mubyte2
    string[Y] = AY[4]
    string[Y] = ubarr1[2]
    string[Y] = string[Y]
    ubarr2[Y] = ubmatrix1[1,2]
    ;ubarr2[Y] = ubmatrix1[1,Y]  ;  todo via evaluation
    ;ubarr2[Y] = ubmatrix1[X,2]  ;  todo via evaluation
    ;ubarr2[Y] = ubmatrix1[X,Y]  ;  todo via evaluation
    ;ubarr2[Y] = ubmatrix1[1,b2]  ;  todo via evaluation
    ;ubarr2[Y] = ubmatrix1[X,b2]  ;  todo via evaluation
    ;ubarr2[Y] = ubmatrix1[b2,2]  ;  todo via evaluation
    ;ubarr2[Y] = ubmatrix1[b2,X]  ;  todo via evaluation
    ;ubarr2[Y] = ubmatrix1[b,b2]  ;  todo via evaluation
    ;ubarr2[Y] = ubmatrix1[ub,ub2]  ;  todo via evaluation
    
    
        ubarr2[ub2] = 42
        ubarr2[ub2] = X
        ubarr2[ub2] = ub2
        ubarr2[ub2] = mubyte2
        ubarr2[ub2] = AY[4]
        ubarr2[ub2] = ubarr1[2]
        ubarr2[ub2] = string[4]
        ubarr2[ub2] = string[X]
        ubarr2[ub2] = string[b]
        ubarr2[ub2] = string[ub]
        ubarr2[ub2] = ubarr1[X]
        ubarr2[ub2] = ubarr1[b]
        ubarr2[ub2] = ubarr1[ub]
        ubarr2[ub2] = AY[Y]
        ubarr2[ub2] = AY[b]
        ubarr2[ub2] = AY[ub]
        AY[ub2] = 42
        AY[ub2] = X
        AY[ub2] = ub2
        AY[ub2] = mubyte2
        AY[ub2] = ubarr1[2]
        AY[ub2] = string[4]
        AY[ub2] = string[X]
        AY[ub2] = string[b]
        AY[ub2] = string[ub]
        AY[ub2] = ubarr1[X]
        AY[ub2] = ubarr1[b]
        AY[ub2] = ubarr1[ub]
        AY[ub2] = ubmatrix1[1,2]
        string[ub2] = 42
        string[ub2] = 'B'
        string[ub2] = X
        string[ub2] = ub2
        string[ub2] = mubyte2
        string[ub2] = AY[4]
        string[ub2] = ubarr1[2]
        string[ub2] = string[ub2]
        ubarr2[ub2] = ubmatrix1[1,2]
        ;ubarr2[ub2] = ubmatrix1[1,Y]  ;  todo via evaluation
        ;ubarr2[ub2] = ubmatrix1[X,2]  ;  todo via evaluation
        ;ubarr2[ub2] = ubmatrix1[X,Y]  ;  todo via evaluation
        ;ubarr2[ub2] = ubmatrix1[1,b2]  ;  todo via evaluation
        ;ubarr2[ub2] = ubmatrix1[X,b2]  ;  todo via evaluation
        ;ubarr2[ub2] = ubmatrix1[b2,2]  ;  todo via evaluation
        ;ubarr2[ub2] = ubmatrix1[b2,X]  ;  todo via evaluation
        ;ubarr2[ub2] = ubmatrix1[b,b2]  ;  todo via evaluation
        ;ubarr2[ub2] = ubmatrix1[ub,ub2]  ;  todo via evaluation

    ubarr2[mubyte2] = 42
    ubarr2[mubyte2] = X
    ubarr2[mubyte2] = ub2
    ubarr2[mubyte2] = mubyte2
    ubarr2[mubyte2] = AY[4]
    ubarr2[mubyte2] = ubarr1[2]
    ubarr2[mubyte2] = string[4]
    ubarr2[mubyte2] = string[X]
    ubarr2[mubyte2] = string[b]
    ubarr2[mubyte2] = string[ub]
    ubarr2[mubyte2] = ubarr1[X]
    ubarr2[mubyte2] = ubarr1[b]
    ubarr2[mubyte2] = ubarr1[ub]
    ubarr2[mubyte2] = AY[Y]
    ubarr2[mubyte2] = AY[b]
    ubarr2[mubyte2] = AY[ub]
    AY[mubyte2] = 42
    AY[mubyte2] = X
    AY[mubyte2] = ub2
    AY[mubyte2] = mubyte2
    AY[mubyte2] = ubarr1[2]
    AY[mubyte2] = string[4]
    AY[mubyte2] = string[X]
    AY[mubyte2] = string[b]
    AY[mubyte2] = string[ub]
    AY[mubyte2] = ubarr1[X]
    AY[mubyte2] = ubarr1[b]
    AY[mubyte2] = ubarr1[ub]
    AY[mubyte2] = ubmatrix1[1,2]
    string[mubyte2] = 42
    string[mubyte2] = 'B'
    string[mubyte2] = X
    string[mubyte2] = ub2
    string[mubyte2] = mubyte2
    string[mubyte2] = AY[4]
    string[mubyte2] = ubarr1[2]
    string[mubyte2] = string[mubyte2]
    ubarr2[mubyte2] = ubmatrix1[1,2]
    ;ubarr2[mubyte2] = ubmatrix1[1,Y]  ;  todo via evaluation
    ;ubarr2[mubyte2] = ubmatrix1[X,2]  ;  todo via evaluation
    ;ubarr2[mubyte2] = ubmatrix1[X,Y]  ;  todo via evaluation
    ;ubarr2[mubyte2] = ubmatrix1[1,b2]  ;  todo via evaluation
    ;ubarr2[mubyte2] = ubmatrix1[X,b2]  ;  todo via evaluation
    ;ubarr2[mubyte2] = ubmatrix1[b2,2]  ;  todo via evaluation
    ;ubarr2[mubyte2] = ubmatrix1[b2,X]  ;  todo via evaluation
    ;ubarr2[mubyte2] = ubmatrix1[b,b2]  ;  todo via evaluation
    ;ubarr2[mubyte2] = ubmatrix1[ub,ub2]  ;  todo via evaluation

    ubarr1[ubarr2[X]] = ubarr2[ubarr1[Y]]   ; todo via evaluation


        
byte_assignment_to_bytearray:
; @todo




; all possible assignments to a WORD VARIABLE (not array)
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
