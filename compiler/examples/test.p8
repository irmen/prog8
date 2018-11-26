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
    c64.CHROUT(ub1)
    c64.CHROUT(mub1)
    c64.CHROUT(ubytearray[1])
    c64.CHROUT(memubytearray[1])
    c64.CHROUT(ubytearray[X])
    c64.CHROUT(memubytearray[X])

    testsub(X)
    testsub(ub1)
    testsub(mub1)
    testsub(ubytearray[1])
    testsub(memubytearray[1])
    testsub(ubytearray[X])
    testsub(memubytearray[X])

    return
}


sub testsub(arg: ubyte) {
    return
}

}
