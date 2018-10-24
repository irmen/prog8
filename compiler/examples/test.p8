; %import c64utils
%option enable_floats
%output raw
%launcher none

~ main  {

sub start() {

    ubyte pixely = 255
    ubyte derp = 0
    byte b = 99
    byte b2 = 100
    word w = 999
    word w2 = 3
    uword uw = 40000
    uword uw2 = 3434
    float fl1 = 1.1
    float fl2 = 2.2

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

;    b = 1
;    derp = 1
;    w = 1
;    uw = 1
;    fl1 = 2.345
;
;    b = b2
;    derp = pixely
;    w = w2
;    uw = uw2
;    fl1 = fl2
;
;    b = mbyte
;    derp = mubyte
;    w = mword
;    uw = muword
;    fl1 = mfloat
;
;    mbyte = 1
;    mubyte = 1
;    mword = 1
;    muword = 1
;    mfloat = 3.456

    %breakpoint

    mbyte = b
    mubyte = derp
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
