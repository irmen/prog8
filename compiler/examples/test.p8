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


ubyte_assignment_to_register:
    A = 42
    A = X
    A = ub2
    A = mubyte2
    A = string[4]
    A = string[X]
    A = string[b]
    A = string[ub]
    A = string[mbyte2]
    A = string[mubyte2]
    A = ubarr1[2]
    A = ubarr1[X]
    A = ubarr1[b]
    A = ubarr1[ub]
    A = ubarr1[mbyte2]
    A = ubarr1[mubyte2]

ubyte_assignment_to_ubytevar:
    ub = 42
    ub = X
    ub = ub2
    ub = mubyte2
    ub = string[4]
    ub = string[X]
    ub = string[b]
    ub = string[ub]
    ub = string[mbyte2]
    ub = string[mubyte2]
    ub = ubarr1[2]
    ub = ubarr1[X]
    ub = ubarr1[b]
    ub = ubarr1[ub]
    ub = ubarr1[mbyte2]
    ub = ubarr1[mubyte2]


ubyte_assignment_to_ubytemem:
    mubyte = 42
    mubyte = X
    mubyte = ub2
    mubyte = mubyte2
    mubyte = string[4]
    mubyte = string[X]
    mubyte = string[b]
    mubyte = string[ub]
    mubyte = string[mbyte2]
    mubyte = string[mubyte2]
    mubyte = ubarr1[2]
    mubyte = ubarr1[X]
    mubyte = ubarr1[b]
    mubyte = ubarr1[ub]
    mubyte = ubarr1[mbyte2]
    mubyte = ubarr1[mubyte2]

byte_assignment_to_bytevar:
    b = -42
    b = b2
    b = mbyte2
    b = barr1[2]
    b = barr1[X]
    b = barr1[b]
    b = barr1[ub]
    b = barr1[mbyte2]
    b = barr1[mubyte2]


byte_assignment_to_bytemem:
    mbyte = -42
    mbyte = b2
    mbyte = mbyte2
    mbyte = barr1[2]
    mbyte = barr1[X]
    mbyte = barr1[b]
    mbyte = barr1[ub]
    mbyte = barr1[mbyte2]
    mbyte = barr1[mubyte2]


ubyte_assignment_to_ubytearray:
    ubarr2[3] = 42
    ubarr2[3] = X
    ubarr2[3] = ub2
    ubarr2[3] = mubyte2
    ubarr2[3] = string[4]
    ubarr2[3] = string[X]
    ubarr2[3] = string[b]
    ubarr2[3] = string[ub]
    ubarr2[3] = string[mbyte2]
    ubarr2[3] = string[mubyte2]
    ubarr2[3] = ubarr1[2]
    ubarr2[3] = ubarr1[X]
    ubarr2[3] = ubarr1[b]
    ubarr2[3] = ubarr1[ub]
    ubarr2[3] = ubarr1[mbyte2]
    ubarr2[3] = ubarr1[mubyte2]
    string[4] = 42
    string[4] = 'B'
    string[4] = X
    string[4] = ub2
    string[4] = mubyte2
    string[4] = ubarr1[2]
    string[4] = ubarr1[X]
    string[4] = ubarr1[ub]
    string[4] = ubarr1[mubyte2]
    string[4] = string[3]
    string[4] = string[Y]
    string[4] = string[ub2]
    string[4] = string[mbyte2]
    string[4] = string[mubyte2]


    ubarr2[Y] = 42
    ubarr2[Y] = X
    ubarr2[Y] = ub2
    ubarr2[Y] = mubyte2
    ubarr2[Y] = string[4]
    ubarr2[Y] = string[X]
    ubarr2[Y] = string[b]
    ubarr2[Y] = string[ub]
    ubarr2[Y] = string[mbyte2]
    ubarr2[Y] = string[mubyte2]
    ubarr2[Y] = ubarr1[2]
    ubarr2[Y] = ubarr1[X]
    ubarr2[Y] = ubarr1[b]
    ubarr2[Y] = ubarr1[ub]
    ubarr2[Y] = ubarr1[mbyte2]
    ubarr2[Y] = ubarr1[mubyte2]
    string[Y] = 42
    string[Y] = 'B'
    string[Y] = X
    string[Y] = ub2
    string[Y] = mubyte2
    string[Y] = ubarr1[2]
    string[Y] = ubarr1[Y]
    string[Y] = ubarr1[ub2]
    string[Y] = ubarr1[mubyte2]
    string[Y] = string[Y]
    string[Y] = string[ub2]
    string[Y] = string[mbyte2]
    string[Y] = string[mubyte2]


        ubarr2[ub2] = 42
        ubarr2[ub2] = X
        ubarr2[ub2] = ub2
        ubarr2[ub2] = mubyte2
        ubarr2[ub2] = string[4]
        ubarr2[ub2] = string[X]
        ubarr2[ub2] = string[b]
        ubarr2[ub2] = string[ub]
        ubarr2[ub2] = string[mbyte2]
        ubarr2[ub2] = string[mubyte2]
        ubarr2[ub2] = ubarr1[2]
        ubarr2[ub2] = ubarr1[X]
        ubarr2[ub2] = ubarr1[b]
        ubarr2[ub2] = ubarr1[ub]
        ubarr2[ub2] = ubarr1[mbyte2]
        ubarr2[ub2] = ubarr1[mubyte2]
        string[ub2] = 42
        string[ub2] = 'B'
        string[ub2] = X
        string[ub2] = ub2
        string[ub2] = mubyte2
        string[ub2] = ubarr1[2]
        string[ub2] = ubarr1[Y]
        string[ub2] = ubarr1[ub2]
        string[ub2] = ubarr1[mubyte2]
        string[ub2] = string[Y]
        string[ub2] = string[ub2]
        string[ub2] = string[mbyte2]
        string[ub2] = string[mubyte2]

    ubarr2[mubyte2] = 42
    ubarr2[mubyte2] = X
    ubarr2[mubyte2] = ub2
    ubarr2[mubyte2] = mubyte2
    ubarr2[mubyte2] = string[4]
    ubarr2[mubyte2] = ubarr1[2]
    string[mubyte2] = 42
    string[mubyte2] = 'B'
    string[mubyte2] = X
    string[mubyte2] = ub2
    string[mubyte2] = mubyte2
    string[mubyte2] = ubarr1[2]

;    ubarr2[mubyte2] = string[X]            ;;todo via evaluation
;    ubarr2[mubyte2] = string[b]             ;todo via evaluation
;    ubarr2[mubyte2] = string[ub]            ;todo via evaluation
;    ubarr2[mubyte2] = string[mbyte2]       ;todo via evaluation
;    ubarr2[mubyte2] = string[mubyte2]      ;todo via evaluation
;    ubarr2[mubyte2] = ubarr1[X]        ;todo via evaluation
;    ubarr2[mubyte2] = ubarr1[b]         ;todo via evaluation
;    ubarr2[mubyte2] = ubarr1[ub]        ;todo via evaluation
;    ubarr2[mubyte2] = ubarr1[mbyte2]    ;todo via evaluation
;    ubarr2[mubyte2] = ubarr1[mubyte2]   ;todo via evaluation
;    string[mubyte2] = ubarr1[Y]        ;todo via evaluation
;    string[mubyte2] = ubarr1[b]        ;todo via evaluation
;    string[mubyte2] = ubarr1[ub2]       ;todo via evaluation
;    string[mubyte2] = ubarr1[mbyte2]   ;todo via evaluation
;    string[mubyte2] = ubarr1[mubyte2]   ;todo via evaluation
;    string[mubyte2] = string[mubyte2]   ;todo via evaluation
;
;    ubarr1[ubarr2[X]] = ubarr2[ubarr1[Y]]   ; todo via evaluation-- check generated asm...



byte_assignment_to_bytearray:

    barr2[3] = 42
    barr2[3] = b2
    barr2[3] = mbyte2
    barr2[3] = barr1[2]
    barr2[3] = barr1[X]
    barr2[3] = barr1[b]
    barr2[3] = barr1[ub]
    barr2[3] = barr1[mbyte2]
    barr2[3] = barr1[mubyte2]

    barr2[Y] = 42
    barr2[Y] = b2
    barr2[Y] = mbyte2
    barr2[Y] = barr1[2]
    barr2[Y] = barr1[X]
    barr2[Y] = barr1[b]
    barr2[Y] = barr1[ub]
    barr2[Y] = barr1[mbyte2]
    barr2[Y] = barr1[mubyte2]

        barr2[ub2] = 42
        barr2[ub2] = b2
        barr2[ub2] = mbyte2
        barr2[ub2] = barr1[2]
        barr2[ub2] = barr1[X]
        barr2[ub2] = barr1[b]
        barr2[ub2] = barr1[ub]
        barr2[ub2] = barr1[mbyte2]
        barr2[ub2] = barr1[mubyte2]

    barr2[mubyte2] = 42
    barr2[mubyte2] = b2
    barr2[mubyte2] = mbyte2
    barr2[mubyte2] = barr1[2]

;    barr2[mubyte2] = string[X]            ;;todo via evaluation
;    barr2[mubyte2] = string[b]             ;todo via evaluation
;    barr2[mubyte2] = string[ub]            ;todo via evaluation
;    barr2[mubyte2] = string[mbyte2]       ;todo via evaluation
;    barr2[mubyte2] = string[mubyte2]      ;todo via evaluation
;    barr2[mubyte2] = ubarr1[X]        ;todo via evaluation
;    barr2[mubyte2] = ubarr1[b]         ;todo via evaluation
;    barr2[mubyte2] = ubarr1[ub]        ;todo via evaluation
;    barr2[mubyte2] = ubarr1[mbyte2]    ;todo via evaluation
;    barr2[mubyte2] = ubarr1[mubyte2]   ;todo via evaluation
;
;    barr1[ubarr2[X]] = barr2[ubarr1[Y]]   ; todo via evaluation-- check generated asm...





; all possible assignments to a UWORD VARIABLE (not array)

uword_assignment_to_registerpair:
    AY = 42
    AY = 42.w
    AY = 42555
    AY = X
    AY = XY
    AY = ub2
    AY = mubyte2
    AY = string[4]
    AY = ubarr1[2]
    ;AY = uwarr1[X]                        ; todo via evaluation
    ;AY = string[X]                  ; todo via evaluation
    ;AY = string[b]              ; todo via evaluation
    ;AY = string[ub]              ; todo via evaluation
    ;AY = string[mbyte2]              ; todo via evaluation
    ;AY = string[mubyte2]              ; todo via evaluation
    ;AY = ubarr1[X]                        ; todo via evaluation
    ;AY = ubarr1[b]                   ; todo via evaluation
    ;AY = ubarr1[ub]                  ; todo via evaluation
    ;AY = ubarr1[mbyte2]                  ; todo via evaluation
    ;AY = ubarr1[mubyte2]                 ; todo via evaluation
    ;AY = uwarr1[X]                        ; todo via evaluation
    ;AY = uwarr1[b]              ; todo via evaluation
    ;AY = uwarr1[ub]              ; todo via evaluation
    ;AY = uwarr1[mbyte2]              ; todo via evaluation
    ;AY = uwarr1[mubyte2]              ; todo via evaluation


uword_assignment_to_uwordvar:
    uw = 42
    uw = 42.w
    uw = 42555
    uw = X
    uw = XY
    uw = ub2
    uw = uw2
    uw = mubyte2
    uw = muword2
    uw = string[4]
    uw = ubarr1[2]
    uw = uwarr1[2]
    ;uw = string[X]          ; todo via evaluation
    ;uw = string[b]         ; todo via evaluation
    ;uw = string[ub]            ; todo via evaluation
    ;uw = string[mbyte2]    ; todo via evaluation
    ;uw = string[mubyte2]   ; todo via evaluation
;    uw = ubarr1[X]          ;; todo via evaluation
;    uw = ubarr1[b]          ;; todo via evaluation
;    uw = ubarr1[ub]         ; todo via evaluation
;    uw = ubarr1[mbyte2]     ; todo via evaluation
;    uw = ubarr1[mubyte2]    ; todo via evaluation
;    uw = uwarr1[X]      ; todo via evaluation
;    uw = uwarr1[b]      ; todo via evaluation
;    uw = uwarr1[ub]     ; todo via evaluation
;    uw = uwarr1[mbyte2]     ; todo via evaluation
;    uw = uwarr1[mubyte2]        ; todo via evaluation


uword_assignment_to_uwordmem:
    muword = 42
    muword = 42.w
    muword = 42555
    muword = X
    muword = XY
    muword = ub2
    muword = uw2
    muword = mubyte2
    muword = muword2
    muword = string[4]
    muword = ubarr1[2]
    muword = uwarr1[2]
;    muword = string[X]      ; todo via evaluation
;    muword = string[b]     ; todo via evaluation
;    muword = string[ub]     ; todo via evaluation
;    muword = string[mbyte2]     ; todo via evaluation
;    muword = string[mubyte2]     ; todo via evaluation
;    muword = ubarr1[X]     ; todo via evaluation
;    muword = ubarr1[b]     ; todo via evaluation
;    muword = ubarr1[ub]     ; todo via evaluation
;    muword = ubarr1[mbyte2]     ; todo via evaluation
;    muword = ubarr1[mubyte2]     ; todo via evaluation
;    muword = uwarr1[X]     ; todo via evaluation
;    muword = uwarr1[b]     ; todo via evaluation
;    muword = uwarr1[ub]     ; todo via evaluation
;    muword = uwarr1[mbyte2]     ; todo via evaluation
;    muword = uwarr1[mubyte2]     ; todo via evaluation



word_assignment_to_wordvar:
    w = -42
    w = -42.w
    w = 12555
    w = X
    w = ub2
    w = b2
    w = w2
    w = mubyte2
    w = mbyte2
    w = mword2
    w = string[4]
    w = ubarr1[2]
    w = barr1[2]
    w = warr1[2]
    ;uw = string[X]          ; todo via evaluation
    ;uw = string[b]         ; todo via evaluation
    ;uw = string[ub]            ; todo via evaluation
    ;uw = string[mbyte2]    ; todo via evaluation
    ;uw = string[mubyte2]   ; todo via evaluation
;    uw = ubarr1[X]          ;; todo via evaluation
;    uw = ubarr1[b]          ;; todo via evaluation
;    uw = ubarr1[ub]         ; todo via evaluation
;    uw = ubarr1[mbyte2]     ; todo via evaluation
;    uw = ubarr1[mubyte2]    ; todo via evaluation
;    uw = uwarr1[X]      ; todo via evaluation
;    uw = uwarr1[b]      ; todo via evaluation
;    uw = uwarr1[ub]     ; todo via evaluation
;    uw = uwarr1[mbyte2]     ; todo via evaluation
;    uw = uwarr1[mubyte2]        ; todo via evaluation





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
;    fl1 = string[4]

    return
}

}
