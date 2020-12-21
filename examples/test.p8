%import textio
%import diskio
%import floats
%import graphics
%zeropage basicsafe
%import test_stack
%option no_sysinit

main {

    asmsub GRAPH_draw_line(uword x1 @R0, uword y1 @R1, uword x2 @R2, uword y2 @R3)  clobbers(A,X,Y)  {
        %asm {{
            lda  cx16.r0
            ldy  cx16.r0+1
            jsr  txt.print_uw
            lda  #13
            jsr  c64.CHROUT
            lda  cx16.r1
            ldy  cx16.r1+1
            jsr  txt.print_uw
            lda  #13
            jsr  c64.CHROUT
            lda  cx16.r2
            ldy  cx16.r2+1
            jsr  txt.print_uw
            lda  #13
            jsr  c64.CHROUT
            lda  cx16.r3
            ldy  cx16.r3+1
            jsr  txt.print_uw
            lda  #13
            jsr  c64.CHROUT
            rts
        }}
    }

    sub start () {
        GRAPH_draw_line(1111,2222,3333,4444)        ; TODO allocate R0-R15 for the C64 as well (at the bottom of the eval stack hi at $cf00)
    }
}
