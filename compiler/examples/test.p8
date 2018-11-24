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
    memory byte mb1 = $c991


        str   stringvar   = "??????????\n\n\nnext line\r\r\rnext line after carriagereturn"
        ubyte  secretnumber = 0
        memory uword freadstr_arg = $22		; argument for FREADSTR
        uword testword
        ubyte char1 = "@"       ; @todo don't put this on the heap
        ubyte char2 = "\n"; @todo don't put this on the heap
        ubyte char3 = "\r"; @todo don't put this on the heap
        ubyte char1b = '@'
        ubyte char2b = '\n'
        ubyte char3b = '\r'

        testword = '@'
        testword = '\n'
        freadstr_arg = '@'
        freadstr_arg = '\n'
        secretnumber = '@'
        secretnumber = '\r'

        testword  = stringvar
        testword = wordarray
        freadstr_arg = stringvar
        freadstr_arg = wordarray
        wordarray[1] = stringvar
        wordarray[1] = wordarray
        wordarray[b1] = stringvar
        wordarray[b1] = wordarray
        wordarray[mb1] = stringvar
        wordarray[mb1] = wordarray
        testword = "stringstring"       ; @todo asmgen for this
        freadstr_arg = "stringstring"     ; @todo asmgen for this
        freadstr_arg = "stringstring2222"     ; @todo asmgen for this
        wordarray[1] = "stringstring"     ; @todo asmgen for this
        wordarray[b1] = "stringstring"     ; @todo asmgen for this
        wordarray[mb1] = "stringstring"     ; @todo asmgen for this



;    address  =c64.MEMBOT(1, 40000.w)   ; ok!
;    address  =c64.MEMBOT(1, address)   ; ok!
;    address  =c64.MEMBOT(1, memaddr)   ; ok!
;
;    A, Y =c64.GETADR()      ; ok!
;    Y, A =c64.GETADR()      ; ok!
;    address = c64flt.GETADRAY() ; ok!
;    memaddr = c64flt.GETADRAY() ; ok!
;    wordarray[1] = c64flt.GETADRAY() ; ok!
;    v1, v2 =c64.GETADR()    ; ok!
;    address  =c64.IOBASE() ; ok!
;    A = c64.CHRIN()   ; ok !
;    X = c64.CHRIN()  ; ok !
;    Y = c64.CHRIN()  ; ok!
;    v1 = c64.CHRIN()    ; ok !

    return
}


}
