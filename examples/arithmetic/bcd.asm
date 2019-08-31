;===========================================================================
;
; Based on: Bruce Clark: a program to verify decimal mode operation
;           www.6502.org/tutorials/decimal_mode.html
;
; NMOS 6502\6510: program for testing accumulator result and flag result
;                 of ADC and SBC in decimal mode,
;                 including correct incorrectness of NVZ flags.
;
;...........................................................................
;
; ttlworks 04/2016: modified the code for testing the 6510 in a C64.
;
;===========================================================================

ERROR   = $0400 ; 0='test passed', 1='test failed'
N1      = $0401 ; first  number to be added/subtrected
N2      = $0402 ; second number to be added/subtrected

N1L     = $00F7 ; Bit 3..0 of first number
N1H     = $00F8 ; Bit 7..4 of first number
N2L     = $00F9 ; Bit 3..0 of second number
N2H     = $00FA ; Bit 7..4 of second number
N2HH    = N2H+1 ; Bit 7..4 of second number OR $0F

AR      = $00FC ; predicted ACC  result in decimal mode
DA      = $00FD ; actual    ACC  result in decimal mode
HNVZC   = $00FE ; predicted flag result in decimal mode
DNVZC   = $00FF ; actual    flag result in decimal mode

;--------------------------------------------------------------------------

        .CPU     "6502"
        * =     $1000

TEST    LDY     #1      ; initialize Y (used to loop through carry flag values)
        STY     ERROR   ; store 1 in ERROR until the test passes

        LDA     #0      ; initialize N1 and N2
        STA     N1
        STA     N2

LOOP1   LDA     N2      ; N2L = N2 & #$0F
        TAX
        AND     #$0F
        STA     N2L

        TXA             ; N2H = N2 & #$F0
        AND     #$F0
        STA     N2H
        ORA     #$0F    ; N2HH = (N2 & #$F0) OR #$0F
        STA     N2HH

LOOP2   LDA     N1
        TAX
        AND     #$0F
        STA     N1L

        TXA
        AND     #$F0
        STA     N1H

        JSR     ADD
        BNE     DONE

        JSR     SUB
        BNE     DONE

        INC     N1
        BNE     LOOP2   ; loop through all 256 values of N1

        INC     N2
        BNE     LOOP1   ; loop through all 256 values of N2

        DEY
        BPL     LOOP1   ; loop through both values of the C_Flag

        LDA     #0      ; test passed, so store 0 in ERROR
        STA     ERROR

DONE    RTS

;--------------------------------------------------------------------------
;
; N1 + N2
;
;  calculate the actual decimal mode accumulator and flag results,
;  calculate the actual binary  mode accumulator and flag results
;
;  calculate the predicted decimal mode accumulator and flag results
;  by using binary arithmetic
;
ADD     ; actual decimal

        SED             ; decimal mode
        CPY     #1      ; set carry if Y=1, clear carry if Y=0
        LDA     N1
        ADC     N2
        STA     DA      ; actual accumulator result in decimal mode
        PHP
        PLA
        STA     DNVZC   ; actual flag result in decimal mode
        CLD             ; binary mode

        ; predicted decimal

        CPY     #1      ; set carry if Y=1, clear carry if Y=0
        LDA     N1L
        ADC     N2L
        CMP     #$0A
        AND     #$0F
        PHA
        LDX     #0
        STX     HNVZC
        BCC     A1

        INX
        ADC     #5      ; add 6 (carry is set)
        AND     #$0F
        SEC

A1      ORA     N1H
;
; if N1L + N2L <  $0A, then add  N2 AND $F0
; if N1L + N2L >= $0A, then add (N2 AND $F0) + $0F +1 (carry is set)
;
        ADC     N2H,X
        BCS     A2
        CMP     #$A0
        BCC     A3

A2      ADC     #$5F    ; add #$60 (carry is set)
        SEC

A3      STA     AR      ; predicted decimal mode accumulator result
        ROL     HNVZC   ; predicted decimal mode C_Flag

        CPX     #1
        PLA             ; evaluate NVZ
        ORA     N1H
        ADC     N2H,X

        JMP     EV1     ; evaluate/compare

;--------------------------------------------------------------------------
;
; N1 - N2
;
;  calculate the actual decimal mode accumulator and flag results,
;  calculate the actual binary  mode accumulator and flag results
;
;
;  calculate the predicted decimal mode accumulator and flag results
;  by using binary arithmetic

SUB     ; actual decimal

        SED             ; decimal mode
        CPY     #1      ; set carry if Y=1, clear carry if Y=0
        LDA     N1
        SBC     N2
        STA     DA      ; actual accumulator result in decimal mode
        PHP
        PLA
        STA     DNVZC   ; actual flag result in decimal mode
        CLD             ; binary mode

        ; predicted decimal

SUB1    CPY     #1      ; set carry if Y=1, clear carry if Y=0
        LDA     N1L
        SBC     N2L
        AND     #$0F
        LDX     #0
        STX     HNVZC
        PHA
        PHP
        BCS     S11

        INX
        SBC     #5      ; subtract 6 (carry is clear)
        AND     #$0F
        CLC

S11     ORA     N1H
;
; if N1L - N2L >= 0, then subtract  N2 AND $F0
; if N1L - N2L <  0, then subtract (N2 AND $F0) + $0F + 1 (carry is clear)
;
        SBC     N2H,X
        BCS     S12
        SBC     #$5F    ; subtract #$60 (carry is clear)
        CLC

S12     STA     AR
        ROL     HNVZC

        PLP             ; evaluate NVZ
        PLA
        ORA     N1H
        SBC     N2H,X

EV1     PHP
        PLA
        AND     #$C2
        ORA     HNVZC
        STA     HNVZC

;..........................................................................
;compare actual results to predicted results
;
; Return: Z_Flag = 1 (BEQ branch) if same
;         Z_Flag = 0 (BNE branch) if different

COMPARE

      ; LDA     HNVZC
        EOR     DNVZC
        AND     #$C3
        BNE     C1

        LDA     AR      ; accumulator
        CMP     DA

C1      RTS

;--------------------------------------------------------------------------

