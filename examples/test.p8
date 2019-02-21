%import c64utils
%zeropage basicsafe

~ main {

    ; @todo test memset/memcopy  (there's a bug in memcopy?)


    ;ubyte x = rnd82() % 6   ; @todo fix compiler crash  + always 0???
    ;drawNext(rnd() & 7)    ; @todo missing asm pattern


    ;ubyte[7] blockColors = [3, 6, 8, 7, 5, 4, 2]
    ;drawNext(n % len(blockColors))          ; @todo why is len a word here?

    ; if(X and c64scr.getcc(1,1)!=32)  ...    @todo the result value of the asmsub getcc is not put on the eval stack when used in an expression

    ; (e1 and e2)   @todo should not simply bitwise and e1 and e2 (same for or, xor)
    ;            Y = (currentBlock[0] & sc)          ; @todo missing assembly pattern for this bitand_byte


    ; mul_word_3

    sub start() {

        Y=c64scr.getchr(30,20)
        c64scr.print_ub(Y)
        c64.CHROUT('\n')
        Y=c64scr.getclr(30,20)
        c64scr.print_ub(Y)
        c64.CHROUT('\n')

        c64scr.setcc(30,20,123,4)

        Y=c64scr.getchr(30,20)
        c64scr.print_ub(Y)
        c64.CHROUT('\n')
        Y=c64scr.getclr(30,20)
        c64scr.print_ub(Y)
        c64.CHROUT('\n')
    }

}
