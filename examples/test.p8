%import textio
%import floats
%zeropage basicsafe

main {
    sub start() {
        %asm {{
            lda  #<float5_111
            ldy  #>float5_111
            jsr  floats.MOVFM
            lda  #<float5_122
            ldy  #>float5_122
            jsr  floats.FADD
            jsr  floats.FOUT
            sta  $7e
            sty  $7f
            ldy  #0
_loop
            lda  ($7e),y
            beq  _done
            jsr  c64.CHROUT
            iny
            bne  _loop
_done
            rts

float5_111	.byte  $81, $0e, $14, $7a, $e1  ; float 1.11
float5_122	.byte  $81, $1c, $28, $f5, $c2  ; float 1.22

        }}
    }

}
