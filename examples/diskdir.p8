%target c64
%import textio
%import syslib
%zeropage dontuse

; This example shows the directory contents of disk drive 8.

main {
    sub start() {
        %asm {{
        lda  #$01
        ldx  #<dirname
        ldy  #>dirname
        jsr  c64.SETNAM
        lda  #1
        ldx  #8
        ldy  #0
        jsr  c64.SETLFS
        jsr  c64.OPEN       ; OPEN 1,8,0
        ldx  #1
        jsr  c64.CHKIN      ; define input channel
        ldy  #$04
labl1   jsr  c64.CHRIN      ; input byte on serial bus
        dey
        bne  labl1      ; get rid of Y bytes
        lda  $C6        ; key pressed?
        ora  $90        ; or EOF?
        bne  labl2      ; if yes exit
        jsr  c64.CHRIN      ; now get the size of the file
        pha
        jsr  c64.CHRIN
        tay
        pla
        jsr  txt.print_uw
        lda  #32
        jsr  c64.CHROUT
labl3   jsr  c64.CHRIN      ; now the filename
        jsr  c64.CHROUT    ; put a character to screen
        cmp  #0
        bne  labl3      ; while not 0 encountered
        lda  #13
        jsr  c64.CHROUT ; put a CR , end line
        ldy  #$02       ; set 2 bytes to skip
        bne  labl1      ; repeat
labl2   lda  #1
        jsr  c64.CLOSE  ; close serial bus device
        jsr  c64.CLRCHN ; restore I/O devices to default
        rts

dirname .byte "$"
        }}
    }
}
