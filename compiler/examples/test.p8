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
    memory ubyte mubyte = $c001
    memory word mword = $c002
    memory uword muword = $c004
    memory float mfloat = $c006


    uw2 = uw
    w2 = w
    b2 = b
    derp=pixely
    fl2 = fl1
    fl2++

    mbyte = 99
    mubyte = 99
    mword = 99      ; @todo fix ast error  literal value missing wordvalue
    muword = 99     ; @todo fix ast error  literal value missing wordvalue
    mword = 999.w
    muword = 999.w
    mfloat = 1.23456

    mbyte = b
    mubyte = derp
    mword = w
    muword = uw
    mfloat = fl2

    ; @todo fix deze assignments:
    b = mbyte
    derp = mubyte
    w = mword
    uw = muword
    fl2 = mfloat


    return
}

}
