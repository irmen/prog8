%import textio
%zeropage basicsafe

cbm2 {
    sub SETTIM(ubyte a, ubyte b, ubyte c) {
    }
    sub RDTIM16() -> uword {
        return 0
    }
}

main {
    sub start() {
        ubyte value
        byte svalue
        uword wvalue
        word swvalue

        txt.print("byte multiply..")
        cbm.SETTIM(0,0,0)
        repeat 200 {
            for value in 0 to 255 {
                cx16.r0L = value*99
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("byte multiply new..")
        cbm.SETTIM(0,0,0)
        repeat 200 {
            for value in 0 to 255 {
                cx16.r0L = multiply_b(value, 99)
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("byte multiply verify..")
        for value in 0 to 255 {
            if multiply_b(value,99) != value*99 {
                txt.print("different!")
                sys.exit(1)
            }
        }
        txt.nl()

        txt.print("sbyte multiply..")
        cbm.SETTIM(0,0,0)
        repeat 200 {
            for svalue in -128 to 127 {
                cx16.r0sL = svalue*99
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("sbyte multiply new..")
        cbm.SETTIM(0,0,0)
        repeat 200 {
            for svalue in -128 to 127 {
                cx16.r0L = multiply_sb(svalue, 99)
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("sbyte multiply verify..")
        for svalue in -128 to 127 {
            if multiply_sb(svalue,99) != svalue*99 {
                txt.print("different!")
                sys.exit(1)
            }
        }
        txt.nl()

        txt.print("word multiply..")
        cbm.SETTIM(0,0,0)
        repeat 200 {
            for wvalue in 200 to 400 {
                cx16.r0 = wvalue*987
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("word multiply new..")
        cbm.SETTIM(0,0,0)
        repeat 200 {
            for wvalue in 200 to 400 {
                cx16.r0 = multiply_w(wvalue, 987)
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("word multiply verify..")
        for wvalue in 200 to 400 {
            if multiply_w(value,987) != value*987 {
                txt.print("different!")
                sys.exit(1)
            }
        }
        txt.nl()

        txt.print("sword multiply..")
        cbm.SETTIM(0,0,0)
        repeat 100 {
            for swvalue in -400 to 400 {
                cx16.r0s = swvalue*987
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("sword multiply new..")
        cbm.SETTIM(0,0,0)
        repeat 100 {
            for swvalue in -400 to 400 {
                cx16.r0s = multiply_sw(swvalue, 987)
            }
        }
        txt.print_uw(cbm.RDTIM16())
        txt.nl()

        txt.print("sword multiply verify..")
        for swvalue in -400 to 400 {
            if multiply_sw(swvalue,987) != swvalue*987 {
                txt.print("different!")
                sys.exit(1)
            }
        }
        txt.nl()
    }

asmsub multiply_sb(byte value @A, byte multiplicant @Y) -> ubyte @A {
    %asm {{
        jmp  p8_multiply_b
    }}
}

asmsub multiply_sw(word value @AY, word multiplicant @R0) -> word @AY {
    %asm {{
        jmp  p8_multiply_w
    }}
}


    asmsub multiply_b(ubyte value @A, ubyte multiplicant @Y) -> ubyte @A {
        %asm {{

; ***************************************************************************************
; On Entry:
;   A:   multiplier
;   Y:   multiplicand
; On Exit:
;   A:     low byte of product
;   Y: (optional) high byte of product
_multiplicand    = P8ZP_SCRATCH_B1
_multiplier      = P8ZP_SCRATCH_REG

    sty  _multiplicand
    lsr  a
    sta  _multiplier
    lda  #0
    ldx  #2
-
    bcc  +
    clc
    adc  _multiplicand
+
    ror  a
    ror  _multiplier
    bcc  +
    clc
    adc  _multiplicand
+
    ror  a
    ror  _multiplier

    bcc  +
    clc
    adc  _multiplicand
+
    ror  a
    ror  _multiplier
    bcc  +
    clc
    adc  _multiplicand
+
    ror  a
    ror  _multiplier
    dex
    bne  -
    ; tay       ; if you want 16 bits result in AY, enable this again
    lda  _multiplier
    rts
        }}
    }

    asmsub multiply_w(uword value @AY, uword multiplicant @R0) -> uword @AY {
        %asm {{
            ; TODO
            lda  #99
            ldy  #1
            rts
        }}
    }
}
