
%import mathlib
%import c64lib
%import c64utils

~ main  {

sub start() {

    ubyte derp


    derp=foo()
    derp=c64.GETADR()
    return
}


sub foo() -> ubyte {
    return 1            ; @todo not ubyte but byte (if sub returns byte)
}



}
