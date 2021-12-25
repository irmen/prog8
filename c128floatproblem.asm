
; this program adds 2 floats 1.2345 + 4.4444 and prints the result 5.67879
; it works fine on C64 and CX16 but fails to add the right number on C128
; I think it has something to do with the banking on the C128
; because several rom fp routines mention "value in bank 1"
; but I don't know what this means and why the program as given doesn't work

.cpu  '6502'

* = $1300       ; sys 4864

P8ZP_SCRATCH_W1 = 251    ; free zp word
CHROUT = $ffd2

; c128:
MOVFM = $af63
FADD = $af18
FOUT = $af06

;cx16:
;MOVFM = $fe42
;FADD = $fe12
;FOUT = $fe7b

;c64:
;MOVFM = $bba2
;FADD = $b867
;FOUT = $bddd

            lda  #<f1
            ldy  #>f1
            jsr  MOVFM       ; load FAC1
            lda  #<f2
            ldy  #>f2
            jsr  FADD        ; add mem float to FAC1     DOESN'T WORK ON C128!?
            jsr  FOUT        ; convert to string
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
_printloop  lda  (P8ZP_SCRATCH_W1),y
            beq  _done
            jsr  CHROUT
            iny
            bne  _printloop
_done       jmp  _done

f1	.byte  $81, $1e, $04, $18, $93  ; float 1.2345
f2	.byte  $83, $0e, $38, $86, $59  ; float 4.4444
