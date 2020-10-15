%import textio
%import conv
%zeropage basicsafe

main {

    sub start() {

        str hex1 = "a4E"
        str hex2 = "$a4E"
        str hex3 = @"a4E"
        str hex4 = @"$a4E"
        str bin1 = "111111010010"
        str bin2 = "%111111010010"

        txt.print(hex1)
        txt.chrout('=')
        txt.print_uwhex(conv2.hex2uword(hex1), true)
        txt.chrout('\n')
        txt.print(hex2)
        txt.chrout('=')
        txt.print_uwhex(conv2.hex2uword(hex2), true)
        txt.chrout('\n')
        txt.print(hex3)
        txt.chrout('=')
        txt.print_uwhex(conv2.hex2uword(hex3), true)
        txt.chrout('\n')
        txt.print(hex4)
        txt.chrout('=')
        txt.print_uwhex(conv2.hex2uword(hex4), true)
        txt.chrout('\n')
        txt.print(bin1)
        txt.chrout('=')
        txt.print_uwbin(conv2.bin2uword(bin1), true)
        txt.chrout('\n')
        txt.print(bin2)
        txt.chrout('=')
        txt.print_uwbin(conv2.bin2uword(bin2), true)
        txt.chrout('\n')


        testX()
    }

    asmsub testX() {
        %asm {{
            stx  _saveX
            lda  #13
            jsr  txt.chrout
            lda  _saveX
            jsr  txt.print_ub
            lda  #13
            jsr  txt.chrout
            ldx  _saveX
            rts
_saveX   .byte 0
        }}
    }
}


conv2 {

    sub hex2uword(uword strptr) -> uword {
        uword result = 0
        while @(strptr) {
            if @(strptr)!='$' {
                ubyte add = 0
                if @(strptr) <= 6 or @(strptr) > '9'
                    add = 9
                result = (result << 4) | (add + @(strptr) & $0f)
            }
            strptr++
        }
        return result
    }

    asmsub bin2uword(uword strptr @AY) -> uword @AY {
        %asm {{
            sta  P8ZP_SCRATCH_W2
            sty  P8ZP_SCRATCH_W2+1
            ldy  #0
            sty  P8ZP_SCRATCH_W1
            sty  P8ZP_SCRATCH_W1+1
_loop       lda  (P8ZP_SCRATCH_W2),y
            beq  _stop
            cmp  #'%'
            beq  +
            asl  P8ZP_SCRATCH_W1
            rol  P8ZP_SCRATCH_W1+1
            and  #1
            ora  P8ZP_SCRATCH_W1
            sta  P8ZP_SCRATCH_W1
+           inc  P8ZP_SCRATCH_W2
            bne  _loop
            inc  P8ZP_SCRATCH_W2+1
            bne  _loop
_stop       lda  P8ZP_SCRATCH_W1
            ldy  P8ZP_SCRATCH_W1+1
            rts
        }}
    }
}
