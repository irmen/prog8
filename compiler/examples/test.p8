%option enable_floats
%import mathlib
%import c64lib
%import c64utils

~ main  {

sub start() {

    byte b1
    ubyte ub1
    memory byte mb1 = $c000
    memory ubyte mub1 = $c001
    ubyte[10] ubytearray
    byte[10] bytearray
    memory ubyte[10] memubytearray = $c100
    memory byte[10] membytearray=$c200
    word[10] wordarray

    c64.CHROUT(X)
    c64.CHROUT(b1)          ; @todo fix compiler crash   expression identifierref should be a vardef, not null
    c64.CHROUT(ub1)          ; @todo fix compiler crash   expression identifierref should be a vardef, not null
    c64.CHROUT(mb1)         ; @todo fix compiler crash   "
    c64.CHROUT(mub1)            ; @todo fix compiler crash   "
    c64.CHROUT(bytearray[1])       ; @todo fix compiler crash  null cannot be cast to non-null type prog8.ast.VarDecl
    c64.CHROUT(ubytearray[1])       ; @todo fix compiler crash  null cannot be cast to non-null type prog8.ast.VarDecl
    c64.CHROUT(membytearray[1])       ; @todo fix compiler crash  null cannot be cast to non-null type prog8.ast.VarDecl
    c64.CHROUT(memubytearray[1])       ; @todo fix compiler crash  null cannot be cast to non-null type prog8.ast.VarDecl
    c64.CHROUT(ubytearray[X])       ; @todo fix compiler crash    "
    c64.CHROUT(memubytearray[X])       ; @todo fix compiler crash    "
    c64.CHROUT(wordarray[1])        ; @todo fix compiler crash     "

    testsub(X)          ; @todo fix compiler crash
    testsub(b1)        ; @todo fix compiler crash
    testsub(ub1)     ; @todo fix compiler crash
    testsub(mb1)       ; @todo fix compiler crash
    testsub(mub1)      ; @todo fix compiler crash
    testsub(bytearray[1])      ; @todo fix compiler crash
    testsub(ubytearray[1])      ; @todo fix compiler crash
    testsub(membytearray[1])      ; @todo fix compiler crash
    testsub(memubytearray[1])      ; @todo fix compiler crash
    testsub(ubytearray[X])     ; @todo fix compiler crash
    testsub(memubytearray[X])     ; @todo fix compiler crash
    testsub(wordarray[1])      ; @todo fix compiler crash

    return
}


sub testsub(arg: ubyte) {
    return
}

}
