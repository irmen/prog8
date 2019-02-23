%import c64utils
%zeropage basicsafe

~ main {

    ; @todo test memset/memcopy  (there's a bug in memcopy?)

    ; @todo see looplabelproblem.p8


    ;ubyte x = rnd82() % 6   ; @todo fix compiler crash  + always 0???
    ;drawNext(rnd() & 7)    ; @todo missing asm pattern


    ;ubyte[7] blockColors = [3, 6, 8, 7, 5, 4, 2]
    ;drawNext(n % len(blockColors))          ; @todo why is len a word here?

    ; if(X and c64scr.getcc(1,1)!=32)  ...    @todo the result value of the asmsub getcc is not put on the eval stack when used in an expression

    ;            Y = (currentBlock[0] & sc)          ; @todo missing assembly pattern for this bitand_byte


    ; mul_word_3

    sub start() {

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

;        ub3 = ub1 & ub2
;        b3 = b1 & b2
;        uw3 = uw1 & uw2
;        w3 = w1 & w2
;        c64scr.print("bitwise-and\n")
;        c64scr.print_ubbin(1, ub3)
;        c64.CHROUT('\n')
;        c64scr.print_b(b3)
;        c64.CHROUT('\n')
;        c64scr.print_uwbin(1, uw3)
;        c64.CHROUT('\n')
;        c64scr.print_w(w3)
;        c64.CHROUT('\n')
;
;        ub3 = ub1 | ub2
;        b3 = b1 | b2
;        uw3 = uw1 | uw2
;        w3 = w1 | w2
;        c64scr.print("bitwise-or\n")
;        c64scr.print_ubbin(1, ub3)
;        c64.CHROUT('\n')
;        c64scr.print_b(b3)
;        c64.CHROUT('\n')
;        c64scr.print_uwbin(1, uw3)
;        c64.CHROUT('\n')
;        c64scr.print_w(w3)
;        c64.CHROUT('\n')
;
;        ub3 = ub1 ^ ub2
;        b3 = b1 ^ b2
;        uw3 = uw1 ^ uw2
;        w3 = w1 ^ w2
;        c64scr.print("bitwise-xor\n")
;        c64scr.print_ubbin(1,ub3)
;        c64.CHROUT('\n')
;        c64scr.print_b(b3)
;        c64.CHROUT('\n')
;        c64scr.print_uwbin(1,uw3)
;        c64.CHROUT('\n')
;        c64scr.print_w(w3)
;        c64.CHROUT('\n')

        c64scr.print("logical-xor(w)\n")
        uw1 = %1001110000001100
        uw2 = %0110001100110011
        uw3 = uw1 xor uw2
        c64scr.print_uwbin(1,uw3)
        c64.CHROUT('\n')
        c64scr.print_uwbin(1,%1001110000001100 xor %0110001100110011)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        uw1 = %1111000011110000
        uw2 = %0011000000110000
        uw3 = uw1 xor uw2
        c64scr.print_uwbin(1,uw3)
        c64.CHROUT('\n')
        c64scr.print_uwbin(1,%1111000011110000 xor %0011000000110000)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        uw1 = %1001110011111111
        uw2 = 0
        uw3 = uw1 xor uw2
        c64scr.print_uwbin(1,uw3)
        c64.CHROUT('\n')
        c64scr.print_uwbin(1,%1001110011111111 xor 0)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        uw1 = 0
        uw2 = $2000
        uw3 = uw1 xor uw2
        c64scr.print_uwbin(1,uw3)
        c64.CHROUT('\n')
        c64scr.print_uwbin(1,0 xor $2000)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        uw1 = $0020
        uw2 = $2000
        uw3 = uw1 xor uw2
        c64scr.print_uwbin(1,uw3)
        c64.CHROUT('\n')
        c64scr.print_uwbin(1,$0020 xor $2000)
        c64.CHROUT('\n')
        c64.CHROUT('\n')
        uw1 = 0
        uw2 = 0
        uw3 = uw1 xor uw2
        c64scr.print_uwbin(1,uw3)
        c64.CHROUT('\n')
        c64scr.print_uwbin(1,0 xor 0)
        c64.CHROUT('\n')
        c64.CHROUT('\n')

;        uw3 = uw1 and uw2
;        c64scr.print_uwbin(1,uw3)
;        c64.CHROUT('\n')
;
;        ub3 = ub1 or ub2
;        uw3 = uw1 or uw2
;        c64scr.print("logical-or\n")
;        c64scr.print_ubbin(1,ub3)
;        c64.CHROUT('\n')
;        c64scr.print_uwbin(1,uw3)
;        c64.CHROUT('\n')
;
;        ub3 = ub1 xor ub2
;        uw3 = uw1 xor uw2
;        c64scr.print("logical-xor\n")
;        c64scr.print_ubbin(1,ub3)
;        c64.CHROUT('\n')
;        c64scr.print_uwbin(1,uw3)
;        c64.CHROUT('\n')
    }

}
