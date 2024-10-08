%import textio
%zeropage basicsafe

main {
    sub start() {
        routine(11,22,33)
        txt.nl()
        cx16.r0 = callfar2(0, &routine, 11, 22, 33, true)
        txt.nl()
        txt.print_uwhex(cx16.r0, true)
        txt.nl()
        cx16.r0 = callfar(0, &routine, 11*256 + 22)
        txt.nl()
        txt.print_uwhex(cx16.r0, true)
        txt.nl()
    }

    asmsub routine(ubyte v1 @A, ubyte v2 @X, ubyte v3 @Y) -> uword @AY {
        %asm {{
            sta  cx16.r8L
            stx  cx16.r9L
            sty  cx16.r10L
            lda  #0
            rol  a
            sta  cx16.r11L

            lda  cx16.r8L
            jsr  txt.print_ub
            lda  #' '
            jsr  txt.chrout
            lda  cx16.r9L
            jsr  txt.print_ub
            lda  #' '
            jsr  txt.chrout
            lda  cx16.r10L
            jsr  txt.print_ub
            lda  #' '
            jsr  txt.chrout
            lda  cx16.r11L
            jsr  txt.print_ub
            lda  #$31
            ldy  #$ea
            rts
        }}
    }
}
