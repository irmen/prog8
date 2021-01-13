; program to test the loading speed

.cpu "65c02"
*=$0801
        ; 10 sys 2061
        .byte $0b, $08, $0a, $00, $9e, $32, $30, $36
        .byte $31, $00, $00, $00

start

    phx

    ldx  #<_filename
    ldy  #>_filename
    lda  #10
    jsr  $FFBD      ; SETNAM
    jmp  _go
_filename
    .text  "romdis.asm"

_go
    lda  #0
    tax
    tay
    jsr  $FFDB      ; SETTIM  0,0,0

    lda  #1
    ldx  #8
    ldy  #0
    jsr  $FFBA       ; SETLFS   1,8,0
    jsr  $FFC0       ; OPEN
    ldx  #1
    jsr  $FFC6       ; CHKIN, use #1 as output channel
    ;lda  #'.'
    ;jsr  $FFD2       ; CHROUT

    ; load the file ....
_loop
    jsr  $ffb7       ;READST
    bne  _eof
    jsr  $FFCF       ;CHRIN
    sta  $02         ; store...
    jmp  _loop

_eof
    ; close stuff
    jsr  $FFCC       ;CLRCHN
    lda  #1
    jsr  $FFC3       ;CLOSE

    ; print the time taken
    jsr  $FFDE       ; RDTIM -> A,X,Y
    tay
    txa
    jsr  $fe03       ; GIVAYF
    jsr  $fe81       ; FOUT
    sta  2
    sty  3
    ldy  #0
_printlp
    lda  (2),y
    beq  _endstr
    jsr  $FFD2    ; CHROUT
    iny
    bne  _printlp
_endstr
    plx
    rts
