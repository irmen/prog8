%import c64utils
%zeropage basicsafe

~ main {

    ; @todo test memset/memcopy  (there's a bug in memcopy?)

    ; @todo see looplabelproblem.p8


    ;ubyte x = rnd82() % 6   ; @todo fix compiler crash  + always 0???

    ;ubyte[7] blockColors = [3, 6, 8, 7, 5, 4, 2]
    ;drawNext(n % len(blockColors))          ; @todo why is len a word here?

    ; if(X and c64scr.getcc(1,1)!=32)  ...    @todo the result value of the asmsub getcc is not put on the eval stack when used in an expression


    sub start() {

        ubyte sc = 3
        ubyte[4] currentBlock
        Y=currentBlock[0] & sc

        ubyte ub1 = %1001
        ubyte ub2 = %0111
        ubyte ub3
        byte b1 = %1001
        byte b2 = %0111
        byte b3
        uword uw1 = %0000100100000000
        uword uw2 = %0000001111000000
        uword uw3
        word w1 = %0000100100000000
        word w2 = %0000001111000000
        word w3

    }

    sub drawNext(ubyte x) {
        A=x
    }
    sub drawNextW(uword w) {
        w++
    }

}
