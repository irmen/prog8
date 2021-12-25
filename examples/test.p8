%import textio
%import floats

main {
    sub start() {

        test_val()

        repeat {
        }
    }

    sub test_val() {

        ; TODO c128 how do I set this in "bank 1" ?  VAL() needs that...

        str @shared value = "-1.23456"
        uword @shared result
        %asm {{
            stx  P8ZP_SCRATCH_B1
            lda  #<value
            sta  $24
            lda  #>value
            sta  $25
            lda  #8
            jsr  floats.VAL
            jsr  floats.FOUT
            sta  result
            sty  result+1
            ldx  P8ZP_SCRATCH_B1
        }}
        txt.print_uwhex(result, true)
        txt.nl()
        txt.print(result)
        txt.nl()
        txt.print($0100)
        txt.nl()
    }

    sub test_freadsa() {
        uword @shared result
        %asm {{
            stx  P8ZP_SCRATCH_B1
            ;lda  #-123
            ;jsr  floats.FREADSA
            lda  #<55444
            ldy  #>55444
            jsr  floats.GIVUAYFAY
            jsr  floats.FOUT
            sta  result
            sty  result+1
            ldx  P8ZP_SCRATCH_B1
        }}
        txt.print_uwhex(result, true)
        txt.nl()
        txt.print(result)
        txt.nl()
        txt.print($0100)
        txt.nl()
    }

    sub test_getadr() {
        uword @shared value
        %asm {{
            stx  P8ZP_SCRATCH_B1
            lda  #<23456
            ldy  #>23456
            jsr  floats.GIVAYFAY
            jsr  floats.GETADRAY
            sta  value
            sty  value+1
            ldx  P8ZP_SCRATCH_B1
        }}
        txt.print_uw(value)
        txt.nl()
    }

    sub test_ayint() {
        %asm {{
            stx  P8ZP_SCRATCH_B1
            lda  #<-23456
            ldy  #>-23456
            jsr  floats.GIVAYFAY
            jsr  floats.AYINT
            ldx  P8ZP_SCRATCH_B1
        }}
        word value = mkword(@($66), @($67)) as word
        txt.print_w(value)
        txt.nl()
    }

    sub test_printf() {
        floats.print_f(0)
        txt.nl()
        floats.print_f(1)
        txt.nl()
        floats.print_f(-1)
        txt.nl()
        floats.print_f(floats.PI)
        txt.nl()
        floats.print_f(floats.TWOPI)
        txt.nl()
    }
}
