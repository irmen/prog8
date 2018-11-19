
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


    ;v1=foo()

    address  =c64.MEMBOT(1, 40000.w)   ; ok!
    address  =c64.MEMBOT(1, address)   ; ok!
    address  =c64.MEMBOT(1, memaddr)   ; ok!i
    ;address  =c64.MEMBOT(1, wordarray[1])   ; @todo nice error about loading X register from stack

    A, Y =c64.GETADR()      ; ok!
    Y, A =c64.GETADR()      ; ok!
    address = c64flt.GETADRAY() ; ok!
    memaddr = c64flt.GETADRAY() ; ok!
    wordarray[1] = c64flt.GETADRAY() ; ok!
    v1, v2 =c64.GETADR()    ; ok!
    address  =c64.IOBASE() ; ok!
    A = c64.CHRIN()   ; ok !
    X = c64.CHRIN()  ; ok !
    v1 = c64.CHRIN()    ; ok !

    return
}


sub foo() -> byte {
    return 1            ; @todo fix error: '1' as byte literal (not ubyte)
}



}
