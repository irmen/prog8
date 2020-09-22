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
        lda  #$08
        sta  $BA        ; device #8
        lda  #$60
        sta  $B9        ; secondary chn
        jsr  $F3D5      ; open for serial bus devices       ; SETLFS + OPEN?
        jsr  $F219      ; set input device
        ldy  #$04
labl1   jsr  c64.ACPTR      ; input byte on serial bus
        dey
        bne  labl1      ; get rid of Y bytes
        lda  $C6        ; key pressed?
        ora  $90        ; or EOF?
        bne  labl2      ; if yes exit
        jsr  c64.ACPTR      ; now get the size of the file
        pha
        jsr  c64.ACPTR
        tay
        pla
        jsr  txt.print_uw
        lda  #32
        jsr  c64.CHROUT
labl3   jsr  c64.ACPTR      ; now the filename
        jsr  c64.CHROUT    ; put a character to screen
        cmp  #0
        bne  labl3      ; while not 0 encountered
        lda  #13
        jsr  c64.CHROUT ; put a CR , end line
        ldy  #$02       ; set 2 bytes to skip
        bne  labl1      ; repeat
labl2   jsr  $F642      ; close serial bus device
        jsr  c64.CLRCHN ; restore I/O devices to default
        rts

dirname .byte "$"
        }}
    }
}
