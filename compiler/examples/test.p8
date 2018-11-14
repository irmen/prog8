
%import mathlib
%import c64lib
%import c64utils

~ main  {

sub start() {

    ubyte v1
    ubyte v2
    float f2
    uword address


    v1=foo()
    X, Y =c64.GETADR()
    v1, v2 =c64.GETADR()
    address  =c64.MEMBOT(1, 0.w)
    address  =c64.IOBASE()
    A = c64.CHRIN()
    X = c64.CHRIN()
    v1 = c64.CHRIN()

    return
}


sub foo() -> ubyte {
    return 1            ; @todo not ubyte but byte (if sub returns byte)
}



}
