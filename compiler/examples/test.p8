%option enable_floats
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


        str   stringvar   = "??????????"
        ubyte  secretnumber = 0
        memory uword freadstr_arg = $22		; argument for FREADSTR
        uword testword
        ubyte char1 = "@"
        ubyte char2 = "\n"      ; @todo escapechar
        ubyte char3 = "\t"      ; @todo escapechar

        ;testword  = stringvar       ; @todo fix str address
        ;testword = "sadfsafsdf"     ; @todo fix str address
        testword = "@"     ; @todo fix argument conversion to UBYTE
        testword = "\n"     ; @todo fix argument conversion to UBYTE (escapechar)
        ;freadstr_arg = stringvar
        ;freadstr_arg = "asdfasdfasdfasdf"
        freadstr_arg = "@"     ; @todo fix argument conversion to UBYTE
        freadstr_arg = "\n"     ; @todo fix argument conversion to UBYTE (escapechar)
        secretnumber = "@"   ; @todo fix argument conversion to UBYTE
        secretnumber = "\n"   ; @todo fix argument conversion to UBYTE (escapechar)
        ;secretnumber = "asdfsdf"


    address  =c64.MEMBOT(1, 40000.w)   ; ok!
    address  =c64.MEMBOT(1, address)   ; ok!
    address  =c64.MEMBOT(1, memaddr)   ; ok!

    A, Y =c64.GETADR()      ; ok!
    Y, A =c64.GETADR()      ; ok!
    address = c64flt.GETADRAY() ; ok!
    memaddr = c64flt.GETADRAY() ; ok!
    wordarray[1] = c64flt.GETADRAY() ; ok!
    v1, v2 =c64.GETADR()    ; ok!
    address  =c64.IOBASE() ; ok!
    A = c64.CHRIN()   ; ok !
    X = c64.CHRIN()  ; ok !
    Y = c64.CHRIN()  ; ok!
    v1 = c64.CHRIN()    ; ok !

    return
}


}
