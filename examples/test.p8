%import c64utils
%zeropage basicsafe

~ main {

    ; @todo test memset/memcopy  (there's a bug in memcopy?)


    ubyte x = rnd82() % 6   ; @todo fix compiler crash  + always 0???
    drawNext(rnd() & 7)    ; @todo missing asm pattern


    ubyte[7] blockColors = [3, 6, 8, 7, 5, 4, 2]
    drawNext(n % len(blockColors))          ; @todo why is len a word here?


    ; mul_word_3

    sub start() {

        byte b1
        byte b2 = -3

        ubyte ub1
        ubyte ub2 = 4

        word w1
        word w2 = -499

        uword uw1
        uword uw2 = 1199

        b1 = b2*40
        ub1 = ub2*40
        w1 = w2*40
        uw1 = uw2*40

        c64scr.print_b(b1)
        c64.CHROUT('\n')
        c64scr.print_ub(ub1)
        c64.CHROUT('\n')
        c64scr.print_w(w1)
        c64.CHROUT('\n')
        c64scr.print_uw(uw1)
        c64.CHROUT('\n')
        c64.CHROUT('\n')


        c64scr.print_ub(X)
        c64.CHROUT('\n')
    }

}
