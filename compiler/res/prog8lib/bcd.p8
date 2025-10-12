bcd {
    ; Decimal addition an subtraction routines.
    ; For CPUs that support BCD mode (binary coded decimal) (not all 6502 variants support this mode...)
    ; This is useful for example for counting decimal score in a game, to avoid costly conversion to a decimal display string. Just print the hex representation.

    sub addb(byte a, byte b) -> byte {
        setbcd()
        a += b
        clearbcd()
        return a
    }

    sub addub(ubyte a, ubyte b) -> ubyte {
        setbcd()
        a += b
        clearbcd()
        return a
    }

    sub addw(word a, word b) -> word {
        setbcd()
        a += b
        clearbcd()
        return a
    }

    sub adduw(uword a, uword b) -> uword {
        setbcd()
        a += b
        clearbcd()
        return a
    }

    sub addl(long a, long b) -> long {
        setbcd()
        a += b
        clearbcd()
        return a
    }

    sub addtol(^^long a, long b) {
        ; -- inplace long BCD addition (avoids copying long values by value)
        setbcd()
        ;; NOT YET IMPLEMENTED IN PROG8: a^^ += b, so inline asm
        %asm {{
            lda  p8v_a
            ldy  p8v_a+1
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
            clc
            lda  (P8ZP_SCRATCH_W1),y
            adc  p8v_b
            sta  (P8ZP_SCRATCH_W1),y
            iny
            lda  (P8ZP_SCRATCH_W1),y
            adc  p8v_b+1
            sta  (P8ZP_SCRATCH_W1),y
            iny
            lda  (P8ZP_SCRATCH_W1),y
            adc  p8v_b+2
            sta  (P8ZP_SCRATCH_W1),y
            iny
            lda  (P8ZP_SCRATCH_W1),y
            adc  p8v_b+3
            sta  (P8ZP_SCRATCH_W1),y
        }}
        clearbcd()
    }

    sub subb(byte a, byte b) -> byte {
        setbcd()
        a -= b
        clearbcd()
        return a
    }

    sub subub(ubyte a, ubyte b) -> ubyte {
        setbcd()
        a -= b
        clearbcd()
        return a
    }

    sub subuw(uword a, uword b) -> uword {
        setbcd()
        a -= b
        clearbcd()
        return a
    }

    sub subl(long a, long b) -> long {
        setbcd()
        a -= b
        clearbcd()
        return a
    }

    sub subfroml(^^long a, long b) {
        ; -- inplace long BCD subtraction (avoids copying long values by value)
        setbcd()
        ;; NOT YET IMPLEMENTED IN PROG8: a^^ -= b, so inline asm
        %asm {{
            lda  p8v_a
            ldy  p8v_a+1
            sta  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
            ldy  #0
            sec
            lda  (P8ZP_SCRATCH_W1),y
            sbc  p8v_b
            sta  (P8ZP_SCRATCH_W1),y
            iny
            lda  (P8ZP_SCRATCH_W1),y
            sbc  p8v_b+1
            sta  (P8ZP_SCRATCH_W1),y
            iny
            lda  (P8ZP_SCRATCH_W1),y
            sbc  p8v_b+2
            sta  (P8ZP_SCRATCH_W1),y
            iny
            lda  (P8ZP_SCRATCH_W1),y
            sbc  p8v_b+3
            sta  (P8ZP_SCRATCH_W1),y
        }}
        clearbcd()
    }


    inline asmsub setbcd() {
        ; be safe and 6502 compatible and prohibit interrupts during BCD mode
        %asm {{
            php
            sei
            sed
        }}
    }

    inline asmsub clearbcd() {
        %asm {{
            plp
        }}
    }
}
