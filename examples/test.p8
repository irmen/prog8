%import c64utils
%zeropage basicsafe

~ main {

    ; @todo test memset/memcopy  (there's a bug in memcopy?)

    ; @todo see looplabelproblem.p8


    ;ubyte x = rnd82() % 6   ; @todo fix compiler crash  + always 0???

    ; if(X and c64scr.getcc(1,1)!=32)  ...    @todo the result value of the asmsub getcc is not put on the eval stack when used in an expression

    sub start() {


    }

    sub drawNext(ubyte x) {
        A=x
    }
    sub drawNextW(uword w) {
        w++
    }

}
