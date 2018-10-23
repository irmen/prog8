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

    uw2 = uw
    w2 = w
    b2 = b
    derp=pixely
    fl2 = fl1
    fl2++
    


    return
}

}
