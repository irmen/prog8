
%import mathlib
%import c64lib
%import c64utils

~ main  {

sub start() {

    ubyte v1
    ubyte v2


    v1=foo()
    v1 , v2 =c64.GETADR()

    return
}


sub foo() -> ubyte {
    return 1            ; @todo not ubyte but byte (if sub returns byte)
}



}
