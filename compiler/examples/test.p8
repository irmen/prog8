
%import mathlib
%import c64lib
%import c64utils

~ main  {

sub start() {

    ubyte v1
    ubyte v2
    float f2
    uword address
    memory uword memaddr = $c000
    uword[2] wordarray
    byte b1


    ;v1=foo()       ; @todo fix return type value error see sub

    A, Y =c64.GETADR()      ; ok!
    Y, A =c64.GETADR()      ; ok!
    address = c64flt.GETADRAY() ; ok!
    memaddr = c64flt.GETADRAY() ; ok!
    wordarray[1] = c64flt.GETADRAY() ; ok!
    v1, v2 =c64.GETADR()    ; ok!
    address  =c64.MEMBOT(1, 0.w)   ; ok !
    address  =c64.IOBASE() ; ok!
    A = c64.CHRIN()   ; ok !
    X = c64.CHRIN()  ; ok !
    v1 = c64.CHRIN()    ; ok !

    return
}


sub foo() -> ubyte {
    return 1            ; @todo not ubyte but byte (if sub returns byte)
}



}
